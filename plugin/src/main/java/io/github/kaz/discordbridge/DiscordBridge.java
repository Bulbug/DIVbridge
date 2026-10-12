package io.github.kaz.discordbridge;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class DiscordBridge extends JavaPlugin {

    private volatile JDA jda;
    private volatile TextChannel channel;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String token = getConfig().getString("bot-token", "").trim();
        if (token.isEmpty() || token.startsWith("PASTE_")) {
            getLogger().warning("No bot token set in plugins/DiscordBridge/config.yml. "
                    + "Fill in bot-token, guild-id and channel-id, then restart the server.");
            return;
        }

        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        // Connecting can take a few seconds, so do it off the main thread.
        getServer().getScheduler().runTaskAsynchronously(this, () -> connect(token));
    }

    private void connect(String token) {
        try {
            JDA built = JDABuilder.createLight(token,
                            GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(new DiscordListener(this))
                    .build()
                    .awaitReady();

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
            } else if (getConfig().getBoolean("commands.enabled", true)) {
                guild.updateCommands().addCommands(
                        Commands.slash("cmd", "Run a command on the Minecraft server")
                                .addOption(OptionType.STRING, "command",
                                        "The command, without the leading slash", true)
                ).queue(null, e -> getLogger().warning("Could not register /cmd: " + e.getMessage()));
            }

            getLogger().info("Connected to Discord as " + built.getSelfUser().getName());
            if (getConfig().getBoolean("events.server-start", true)) {
                sendToDiscord(getConfig().getString("events.server-start-message", ""));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            getLogger().severe("Could not connect to Discord: " + e.getMessage()
                    + " (is bot-token correct?)");
        }
    }

    @Override
    public void onDisable() {
        JDA j = jda;
        if (j == null) return;
        try {
            TextChannel ch = channel;
            if (ch != null && getConfig().getBoolean("events.server-stop", true)) {
                String msg = getConfig().getString("events.server-stop-message", "");
                if (!msg.isBlank()) {
                    ch.sendMessage(msg).setAllowedMentions(Collections.emptyList()).complete();
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

    /** Posts a message to the configured channel. Safe to call from any thread. */
    public void sendToDiscord(String text) {
        TextChannel ch = channel;
        if (ch == null || text == null || text.isBlank()) return;
        if (text.length() > 1900) text = text.substring(0, 1900) + "…";
        ch.sendMessage(text)
                .setAllowedMentions(Collections.emptyList()) // no @everyone / @role / @user pings
                .queue(null, err -> getLogger().warning("Could not send to Discord: " + err.getMessage()));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage("DiscordBridge config reloaded. (Token/IDs need a restart.)");
        } else {
            sender.sendMessage("Usage: /discordbridge reload");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String alias, @NotNull String[] args) {
        return args.length == 1 ? List.of("reload") : List.of();
    }
}
