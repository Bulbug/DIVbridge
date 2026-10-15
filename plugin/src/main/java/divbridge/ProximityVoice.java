package divbridge;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Proximity voice using Discord voice channels.
 *
 * Every few seconds we look at where linked players are in the game, group the ones who are close together,
 * and move their Discord accounts into a shared temporary voice room. Players with nobody nearby wait in the lobby.
 * No audio is ever touched. We only move people between channels.
 */
public class ProximityVoice {

    private static final long NEW_ROOM_GRACE_MS = 15_000;
    private static final long MOVE_COOLDOWN_MS = 10_000;

    private record MoveRecord(long target, long time) { }

    private record Participant(Member member, UUID world, double x, double y, double z, long channelId) { }

    private final DIVbridge plugin;
    private final Guild guild;
    private final long lobbyId;
    private final Category category; // may be null

    private final Map<Long, Long> rooms = new ConcurrentHashMap<>();      // room channel id -> created at (ms)
    private final Map<Long, MoveRecord> lastMoves = new ConcurrentHashMap<>(); // member id -> last move
    private BukkitTask task;
    private boolean warnedPermissions;

    public ProximityVoice(DIVbridge plugin, Guild guild, VoiceChannel lobby, Category category) {
        this.plugin = plugin;
        this.guild = guild;
        this.lobbyId = lobby.getIdLong();
        this.category = category;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    /** Call on the main thread. */
    public void start() {
        adoptOrCleanLeftovers();
        long ticks = Math.max(2, cfg().getLong("voice.update-seconds", 5)) * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, ticks);
        plugin.getLogger().info("Proximity voice is on (radius " + cfg().getDouble("voice.radius", 32) + " blocks).");
    }

    // ---------------------------------------------------------------
    // The main loop (runs on the server thread)
    // ---------------------------------------------------------------

    private void tick() {
        VoiceChannel lobby = guild.getVoiceChannelById(lobbyId);
        if (lobby == null) return;

        long now = System.currentTimeMillis();
        double radius = Math.max(1, cfg().getDouble("voice.radius", 32));
        double multiplier = Math.max(1, cfg().getDouble("voice.leave-radius-multiplier", 1.3));
        LinkManager links = plugin.links();

        // 1. Anyone sitting in a room whose Minecraft player has left the game goes back to the lobby.
        for (long roomId : rooms.keySet()) {
            VoiceChannel room = guild.getVoiceChannelById(roomId);
            if (room == null) continue;
            for (Member m : room.getMembers()) {
                UUID uuid = links.getUuid(m.getIdLong());
                if (uuid != null && Bukkit.getPlayer(uuid) == null) move(m, lobby, now);
            }
        }

        // 2. Linked players who are online in game AND in the lobby or one of our rooms.
        List<Participant> people = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Long discordId = links.getDiscordId(p.getUniqueId());
            if (discordId == null) continue;
            Member member = guild.getMemberById(discordId);
            if (member == null) continue;
            GuildVoiceState vs = member.getVoiceState();
            if (vs == null || !vs.inAudioChannel() || vs.getChannel() == null) continue;
            long channelId = vs.getChannel().getIdLong();
            if (channelId != lobbyId && !rooms.containsKey(channelId)) continue; // in some other channel: hands off
            var loc = p.getLocation();
            people.add(new Participant(member, loc.getWorld().getUID(), loc.getX(), loc.getY(), loc.getZ(), channelId));
        }

        // 3. Group people who are close to each other.
        int n = people.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Participant a = people.get(i), b = people.get(j);
                if (!a.world().equals(b.world())) continue;
                double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
                double d2 = dx * dx + dy * dy + dz * dz;
                boolean alreadyTogether = a.channelId() == b.channelId() && a.channelId() != lobbyId;
                double limit = alreadyTogether ? radius * multiplier : radius;
                if (d2 <= limit * limit) union(parent, i, j);
            }
        }
        Map<Integer, List<Participant>> byRoot = new HashMap<>();
        for (int i = 0; i < n; i++) byRoot.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(people.get(i));
        List<List<Participant>> groups = new ArrayList<>(byRoot.values());
        groups.sort((a, b) -> b.size() - a.size()); // biggest groups choose rooms first

        // 4. Give each group a room.
        Set<Long> claimed = new HashSet<>();
        Set<String> usedNames = new HashSet<>();
        for (long id : rooms.keySet()) {
            VoiceChannel ch = guild.getVoiceChannelById(id);
            if (ch != null) usedNames.add(ch.getName());
        }

        for (List<Participant> group : groups) {
            if (group.size() == 1) { // alone: wait in the lobby
                Participant p = group.get(0);
                if (p.channelId() != lobbyId) move(p.member(), lobby, now);
                continue;
            }

            // Reuse the existing room most of this group is already in (so people don't get shuffled needlessly).
            Map<Long, Integer> counts = new HashMap<>();
            for (Participant p : group) {
                if (rooms.containsKey(p.channelId())) counts.merge(p.channelId(), 1, Integer::sum);
            }
            Long best = null;
            int bestCount = 0;
            for (Map.Entry<Long, Integer> e : counts.entrySet()) {
                if (!claimed.contains(e.getKey()) && e.getValue() > bestCount) {
                    best = e.getKey();
                    bestCount = e.getValue();
                }
            }
            VoiceChannel target = best == null ? null : guild.getVoiceChannelById(best);
            if (target != null) {
                claimed.add(best);
                for (Participant p : group) {
                    if (p.channelId() != best) move(p.member(), target, now);
                }
            } else {
                createRoomFor(group, usedNames);
            }
        }

        // 5. Delete rooms that are empty.
        Iterator<Map.Entry<Long, Long>> it = rooms.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Long> e = it.next();
            VoiceChannel ch = guild.getVoiceChannelById(e.getKey());
            if (ch == null) { it.remove(); continue; }
            if (claimed.contains(ch.getIdLong())) continue;
            if (ch.getMembers().isEmpty() && now - e.getValue() > NEW_ROOM_GRACE_MS) {
                it.remove();
                try {
                    ch.delete().queue(null, err -> plugin.getLogger().fine("Could not delete room: " + err.getMessage()));
                } catch (Exception ex) {
                    warnPermissions(ex);
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Rooms and moving people
    // ---------------------------------------------------------------

    private void createRoomFor(List<Participant> group, Set<String> usedNames) {
        String name = nextRoomName(usedNames);
        usedNames.add(name);
        try {
            var action = category != null ? guild.createVoiceChannel(name, category) : guild.createVoiceChannel(name);
            action.queue(ch -> {
                long created = System.currentTimeMillis();
                rooms.put(ch.getIdLong(), created);
                for (Participant p : group) move(p.member(), ch, created);
            }, err -> plugin.getLogger().warning("Could not create a voice room: " + err.getMessage()));
        } catch (Exception ex) {
            warnPermissions(ex);
        }
    }

    private String roomTemplate() {
        return cfg().getString("voice.room-name", "Nearby %n%");
    }

    private String nextRoomName(Set<String> used) {
        String template = roomTemplate();
        for (int i = 1; i < 1000; i++) {
            String name = template.replace("%n%", String.valueOf(i));
            if (!used.contains(name)) return name;
        }
        return template.replace("%n%", String.valueOf(System.currentTimeMillis() % 100000));
    }

    private void move(Member member, VoiceChannel target, long now) {
        MoveRecord last = lastMoves.get(member.getIdLong());
        if (last != null && last.target() == target.getIdLong() && now - last.time() < MOVE_COOLDOWN_MS) return;
        lastMoves.put(member.getIdLong(), new MoveRecord(target.getIdLong(), now));
        try {
            guild.moveVoiceMember(member, target)
                    .queue(null, err -> plugin.getLogger().fine("Could not move a member: " + err.getMessage()));
        } catch (IllegalStateException ignored) {
            // They left voice just now.
        } catch (Exception ex) {
            warnPermissions(ex);
        }
    }

    private void warnPermissions(Exception ex) {
        if (ex instanceof InsufficientPermissionException) {
            if (!warnedPermissions) {
                warnedPermissions = true;
                plugin.getLogger().warning("Proximity voice: the bot is missing a permission (" + ex.getMessage()
                        + "). It needs Manage Channels, Move Members and Connect on the lobby and the category.");
            }
        } else {
            plugin.getLogger().fine("Voice error: " + ex.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Startup / shutdown
    // ---------------------------------------------------------------

    /** After a restart, adopt old rooms that still have people in them and delete the empty ones. */
    private void adoptOrCleanLeftovers() {
        if (category == null) return; // only touch channels inside our own category
        String template = roomTemplate();
        int idx = template.indexOf("%n%");
        String prefix = idx >= 0 ? template.substring(0, idx) : template;
        if (prefix.isBlank()) return;

        for (VoiceChannel ch : category.getVoiceChannels()) {
            if (ch.getIdLong() == lobbyId || !ch.getName().startsWith(prefix)) continue;
            if (ch.getMembers().isEmpty()) {
                try {
                    ch.delete().queue(null, err -> plugin.getLogger().fine("Could not delete old room: " + err.getMessage()));
                } catch (Exception ex) {
                    warnPermissions(ex);
                }
            } else {
                rooms.put(ch.getIdLong(), System.currentTimeMillis());
            }
        }
    }

    /** Sends everyone back to the lobby and deletes our rooms. Called when the plugin turns off. */
    public void shutdown() {
        if (task != null) task.cancel();
        VoiceChannel lobby = guild.getVoiceChannelById(lobbyId);
        for (long id : rooms.keySet()) {
            VoiceChannel room = guild.getVoiceChannelById(id);
            if (room == null) continue;
            try {
                if (lobby != null) {
                    for (Member m : room.getMembers()) {
                        try {
                            guild.moveVoiceMember(m, lobby).submit().get(3, TimeUnit.SECONDS);
                        } catch (Exception ignored) {
                            // Best effort.
                        }
                    }
                }
                room.delete().submit().get(3, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // Best effort. Leftover empty rooms are cleaned up on the next start.
            }
        }
        rooms.clear();
    }

    // ---------------------------------------------------------------
    // Union-find helpers
    // ---------------------------------------------------------------

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a), rb = find(parent, b);
        if (ra != rb) parent[ra] = rb;
    }
}
