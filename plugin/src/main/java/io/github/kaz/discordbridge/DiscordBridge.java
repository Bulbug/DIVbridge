package io.github.kaz.discordbridge;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class DiscordBridge extends JavaPlugin {

    private volatile JDA jda;
    private volatile TextChannel channel;
    private WebhookSender webhook;
    private LinkManager links;
    private volatile ProximityVoice voice;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String token = getConfig().getString("bot-token", "").trim();
        if (token.isEmpty() || token.startsWith("PASTE_")) {
            getLogger().warning("No bot token set in plugins/DiscordBridge/config.yml. "
                    + "Fill in bot-token, guild-id and channel-id, then restart the server.");
            return;
        }

        webhook = new WebhookSender(this);
        links = new LinkManager(this);
        links.load();
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        // Connecting can take a few seconds, so do it off the main thread.
        getServer().getScheduler().runTaskAsynchronously(this, () -> connect(token));
    }

    private void connect(String token) {
        try {
            boolean voiceOn = getConfig().getBoolean("voice.enabled", false);
            JDABuilder builder = JDABuilder.createLight(token,
                            GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(new DiscordListener(this));
            if (voiceOn) {
                // Lets us see who is in which voice channel (not a privileged intent).
                builder.enableIntents(GatewayIntent.GUILD_VOICE_STATES)
                        .enableCache(CacheFlag.VOICE_STATE)
                        .setMemberCachePolicy(MemberCachePolicy.VOICE);
            }
            JDA built = builder.build().awaitReady();

            if (!isEnabled()) { // server was stopped while we were connecting
                built.shutdownNow();
                return;
            }
            this.jda = built;

            String channelId = getConfig().getString("channel-id", "").trim();
            String guildId = getConfig().getString("guild-id", "").trim();

            if (!channelId.isEmpty() && !channelId.startsWith("PASTE_")) {
                channel = built.getTextChannelById(channelId);
            }
            if (channel == null) {
                getLogger().warning("Could not find the channel. Check channel-id and that the bot can see it.");
            }

            Guild guild = guildId.isEmpty() || guildId.startsWith("PASTE_") ? null : built.getGuildById(guildId);
            if (guild == null) {
                getLogger().warning("Could not find the Discord server. Check guild-id and that the bot was invited.");
            } else {
                List<SlashCommandData> cmds = new ArrayList<>();
                if (getConfig().getBoolean("commands.enabled", true)) {
                    cmds.add(Commands.slash("cmd", "Run a command on the Minecraft server")
                            .addOption(OptionType.STRING, "command",
                                    "The command, without the leading slash", true));
                }
                if (getConfig().getBoolean("list.enabled", true)) {
                    cmds.add(Commands.slash("list", "See who is online right now"));
                }
                if (voiceOn) {
                    cmds.add(Commands.slash("link", "Link your Minecraft account (type /link in Minecraft first)")
                            .addOption(OptionType.STRING, "code", "The code shown in Minecraft", true));
                    cmds.add(Commands.slash("unlink", "Unlink your Minecraft account"));
                }
                guild.updateCommands().addCommands(cmds)
                        .queue(null, e -> getLogger().warning("Could not register slash commands: " + e.getMessage()));
            }

            if (voiceOn && guild != null) {
                setUpVoice(built, guild);
            }

            getLogger().info("Connected to Discord as " + built.getSelfUser().getName());
            if (getConfig().getBoolean("events.server-start", true)) {
                sendEvent("start", getConfig().getString("events.server-start-message", ""), null, null);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            getLogger().severe("Could not connect to Discord: " + e.getMessage()
                    + " (is bot-token correct?)");
        }
    }

    private void setUpVoice(JDA built, Guild guild) {
        String lobbyId = getConfig().getString("voice.lobby-channel-id", "").trim();
        VoiceChannel lobby = lobbyId.isEmpty() ? null : built.getVoiceChannelById(lobbyId);
        if (lobby == null) {
            getLogger().warning("voice.enabled is true but voice.lobby-channel-id is not a voice channel the bot can see. "
                    + "Proximity voice is off.");
            return;
        }
        String categoryId = getConfig().getString("voice.category-id", "").trim();
        Category category = categoryId.isEmpty() ? null : built.getCategoryById(categoryId);
        if (!categoryId.isEmpty() && category == null) {
            getLogger().warning("voice.category-id was not found. Rooms will be created at the top of the channel list.");
        }
        ProximityVoice pv = new ProximityVoice(this, guild, lobby, category);
        this.voice = pv;
        getServer().getScheduler().runTask(this, pv::start);
    }

    public LinkManager links() {
        return links;
    }

    @Override
    public void onDisable() {
        JDA j = jda;
        if (j == null) return;
        ProximityVoice pv = voice;
        if (pv != null) pv.shutdown();
        try {
            TextChannel ch = channel;
            String msg = getConfig().getString("events.server-stop-message", "");
            if (ch != null && !msg.isBlank() && getConfig().getBoolean("events.server-stop", true)) {
                if (useEmbeds()) {
                    ch.sendMessageEmbeds(eventEmbed("stop", msg, null, null))
                            .setAllowedMentions(Collections.emptyList()).complete();
                } else {
                    ch.sendMessage(plainEvent("stop", msg))
                            .setAllowedMentions(Collections.emptyList()).complete();
                }
            }
        } catch (Exception ignored) {
            // Shutting down anyway.
        }
        j.shutdown();
        try {
            if (!j.awaitShutdown(Duration.ofSeconds(5))) j.shutdownNow();
        } catch (InterruptedException e) {
            j.shutdownNow();
        }
    }

    // ---------------------------------------------------------------
    // Sending to Discord (safe to call from any thread)
    // ---------------------------------------------------------------

    private boolean useEmbeds() {
        return getConfig().getBoolean("style.use-embeds", true);
    }

    /** Plain text message. */
    public void sendToDiscord(String text) {
        TextChannel ch = channel;
        if (ch == null || text == null || text.isBlank()) return;
        if (text.length() > 1900) text = text.substring(0, 1900) + "…";
        ch.sendMessage(text)
                .setAllowedMentions(Collections.emptyList()) // no @everyone / @role / @user pings
                .queue(null, err -> getLogger().warning("Could not send to Discord: " + err.getMessage()));
    }

    /**
     * An activity card (join, quit, death, start, stop...).
     *
     * @param kind     join, first-join, quit, death, start or stop (also picks the color)
     * @param playerId whose head to show, or null
     * @param online   player count for the footer, or null
     */
    public void sendEvent(String kind, String text, UUID playerId, Integer online) {
        TextChannel ch = channel;
        if (ch == null || text == null || text.isBlank()) return;
        if (!useEmbeds()) {
            sendToDiscord(plainEvent(kind, text));
            return;
        }
        ch.sendMessageEmbeds(eventEmbed(kind, text, playerId, online))
                .setAllowedMentions(Collections.emptyList())
                .queue(null, err -> getLogger().warning("Could not send to Discord: " + err.getMessage()));
    }

    private MessageEmbed eventEmbed(String kind, String text, UUID playerId, Integer online) {
        if (text.length() > 250) text = text.substring(0, 250) + "…";
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(color(kind))
                .setTimestamp(Instant.now());
        String avatar = playerId == null ? null : avatar(playerId);
        if (avatar != null) eb.setAuthor(text, null, avatar);
        else eb.setAuthor(text);
        if (online != null) eb.setFooter(online + " online");
        return eb.build();
    }

    private String plainEvent(String kind, String text) {
        String emoji = switch (kind) {
            case "join" -> "🟢";
            case "first-join" -> "🎉";
            case "quit" -> "🔴";
            case "death" -> "💀";
            case "start" -> "✅";
            case "stop" -> "⛔";
            default -> "•";
        };
        return emoji + " " + MarkdownSanitizer.escape(text);
    }

    /** In-game chat -> Discord. Uses the webhook (player name + head) if one is set. */
    public void sendChat(String name, UUID id, String message) {
        if (message == null || message.isBlank()) return;
        if (webhook != null && webhook.isConfigured()) {
            webhook.send(name, avatar(id), message, () -> sendToDiscord(plainChat(name, message)));
        } else {
            sendToDiscord(plainChat(name, message));
        }
    }

    private String plainChat(String name, String message) {
        return getConfig().getString("chat.minecraft-format", "**%player%**: %message%")
                .replace("%player%", MarkdownSanitizer.escape(name))
                .replace("%message%", message);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private Color color(String kind) {
        String hex = getConfig().getString("style.colors." + kind, "5865F2");
        try {
            return new Color(Integer.parseInt(hex.replace("#", "").trim(), 16));
        } catch (NumberFormatException e) {
            return new Color(0x5865F2);
        }
    }

    private String avatar(UUID id) {
        String template = getConfig().getString("style.avatar-url", "");
        if (template == null || template.isBlank()) return null;
        return template.replace("%uuid%", id.toString());
    }

    /** The /list card. Call this on the main server thread. */
    public MessageEmbed buildListEmbed() {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(MarkdownSanitizer.escape(p.getName()));
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);

        int max = Bukkit.getMaxPlayers();
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(color("list"))
                .setTitle("🟢 " + getConfig().getString("style.server-name", "Minecraft Server"))
                .setFooter("Minecraft " + Bukkit.getMinecraftVersion())
                .setTimestamp(Instant.now());

        if (names.isEmpty()) {
            eb.setDescription("Nobody is online right now.");
        } else {
            eb.setDescription("**" + names.size() + " / " + max + "** players online");
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (String n : names) {
                String line = "• " + n + "\n";
                if (sb.length() + line.length() > 900) break;
                sb.append(line);
                shown++;
            }
            if (shown < names.size()) sb.append("…and ").append(names.size() - shown).append(" more");
            eb.addField("Players", sb.toString().trim(), false);
        }

        if (getConfig().getBoolean("list.show-tps", true)) {
            try {
                double tps = Math.min(20.0, Bukkit.getTPS()[0]);
                eb.addField("TPS", String.format(Locale.ROOT, "%.1f", tps), true);
            } catch (Throwable ignored) {
                // TPS not available on this server build
            }
        }
        return eb.build();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "link" -> handleLink(sender);
            case "unlink" -> handleUnlink(sender);
            default -> {
                if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                    reloadConfig();
                    sender.sendMessage("DiscordBridge config reloaded. (Token/IDs and voice settings need a restart.)");
                } else {
                    sender.sendMessage("Usage: /discordbridge reload");
                }
            }
        }
        return true;
    }

    private void handleLink(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can link an account.");
            return;
        }
        if (links == null || !getConfig().getBoolean("voice.enabled", false)) {
            player.sendMessage(Component.text("Voice linking isn't turned on on this server.", NamedTextColor.RED));
            return;
        }
        String code = links.createCode(player.getUniqueId());
        player.sendMessage(Component.text("Your link code is ", NamedTextColor.GRAY)
                .append(Component.text(code, NamedTextColor.GREEN, TextDecoration.BOLD)));
        player.sendMessage(Component.text("In Discord, type /link and enter that code. It expires in 5 minutes.",
                NamedTextColor.GRAY));
    }

    private void handleUnlink(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can unlink an account.");
            return;
        }
        if (links != null && links.unlinkByUuid(player.getUniqueId())) {
            player.sendMessage(Component.text("Your Discord account has been unlinked.", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("Your account isn't linked.", NamedTextColor.GRAY));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String alias, @NotNull String[] args) {
        return args.length == 1 ? List.of("reload") : List.of();
    }
}
