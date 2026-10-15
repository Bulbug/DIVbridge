package divbridge;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Links a Discord account to a Minecraft account (works for Java and Bedrock/Floodgate players). */
public class LinkManager {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I
    private static final int CODE_LENGTH = 6;
    private static final long CODE_LIFETIME_MS = 5 * 60_000L;
    private static final long LOCKOUT_MS = 10 * 60_000L;
    private static final int MAX_FAILURES = 5;

    private record Pending(UUID uuid, long expires) { }

    private final DIVbridge plugin;
    private final File file;
    private final SecureRandom random = new SecureRandom();

    private final Map<Long, UUID> byDiscord = new ConcurrentHashMap<>();
    private final Map<UUID, Long> byUuid = new ConcurrentHashMap<>();
    private final Map<String, Pending> codes = new ConcurrentHashMap<>();
    private final Map<Long, long[]> failures = new ConcurrentHashMap<>(); // {count, windowStart}

    public LinkManager(DIVbridge plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "links.yml");
    }

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = data.getConfigurationSection("links");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                long discordId = Long.parseLong(key);
                UUID uuid = UUID.fromString(sec.getString(key, ""));
                byDiscord.put(discordId, uuid);
                byUuid.put(uuid, discordId);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipping bad entry in links.yml: " + key);
            }
        }
    }

    private synchronized void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<Long, UUID> e : byDiscord.entrySet()) {
            data.set("links." + e.getKey(), e.getValue().toString());
        }
        try {
            plugin.getDataFolder().mkdirs();
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save links.yml: " + e.getMessage());
        }
    }

    // ---------------- lookups ----------------

    public UUID getUuid(long discordId) {
        return byDiscord.get(discordId);
    }

    public Long getDiscordId(UUID uuid) {
        return byUuid.get(uuid);
    }

    // ---------------- link codes ----------------

    /** Makes a fresh code for this player (replacing any older one). */
    public String createCode(UUID uuid) {
        long now = System.currentTimeMillis();
        codes.entrySet().removeIf(e -> e.getValue().expires() < now || e.getValue().uuid().equals(uuid));
        String code;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < CODE_LENGTH; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            code = sb.toString();
        } while (codes.containsKey(code));
        codes.put(code, new Pending(uuid, now + CODE_LIFETIME_MS));
        return code;
    }

    /** @return the player the code belongs to, or null if it's wrong or expired. A used code is consumed. */
    public UUID redeem(String code) {
        Pending p = codes.remove(code);
        if (p == null || p.expires() < System.currentTimeMillis()) return null;
        return p.uuid();
    }

    public void link(long discordId, UUID uuid) {
        unlinkByDiscord(discordId, false);
        unlinkByUuid(uuid, false);
        byDiscord.put(discordId, uuid);
        byUuid.put(uuid, discordId);
        failures.remove(discordId);
        save();
    }

    public boolean unlinkByDiscord(long discordId) {
        return unlinkByDiscord(discordId, true);
    }

    private boolean unlinkByDiscord(long discordId, boolean persist) {
        UUID uuid = byDiscord.remove(discordId);
        if (uuid == null) return false;
        byUuid.remove(uuid);
        if (persist) save();
        return true;
    }

    public boolean unlinkByUuid(UUID uuid) {
        return unlinkByUuid(uuid, true);
    }

    private boolean unlinkByUuid(UUID uuid, boolean persist) {
        Long discordId = byUuid.remove(uuid);
        if (discordId == null) return false;
        byDiscord.remove(discordId);
        if (persist) save();
        return true;
    }

    // ---------------- guess protection ----------------

    public boolean isLockedOut(long discordId) {
        long[] f = failures.get(discordId);
        if (f == null) return false;
        if (System.currentTimeMillis() - f[1] > LOCKOUT_MS) {
            failures.remove(discordId);
            return false;
        }
        return f[0] >= MAX_FAILURES;
    }

    public void recordFailure(long discordId) {
        failures.compute(discordId, (k, f) -> {
            long now = System.currentTimeMillis();
            if (f == null || now - f[1] > LOCKOUT_MS) return new long[]{1, now};
            f[0]++;
            return f;
        });
    }
}
