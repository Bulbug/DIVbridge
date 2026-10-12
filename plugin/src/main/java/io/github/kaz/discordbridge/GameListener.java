package io.github.kaz.discordbridge;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Minecraft -> Discord. */
public class GameListener implements Listener {

    private final DiscordBridge plugin;

    public GameListener(DiscordBridge plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    private String fill(String template, Player player, int onlineDelta) {
        int online = Math.max(0, Bukkit.getOnlinePlayers().size() + onlineDelta);
        return template
                .replace("%player%", MarkdownSanitizer.escape(player.getName()))
                .replace("%online%", String.valueOf(online));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!cfg().getBoolean("events.join", true)) return;
        Player p = event.getPlayer();
        String template = cfg().getString("events.join-message", "");
        String first = cfg().getString("events.first-join-message", "");
        if (!p.hasPlayedBefore() && first != null && !first.isEmpty()) template = first;
        plugin.sendToDiscord(fill(template, p, 0));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!cfg().getBoolean("events.quit", true)) return;
        // The leaving player is still counted while this event fires.
        plugin.sendToDiscord(fill(cfg().getString("events.quit-message", ""), event.getPlayer(), -1));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!cfg().getBoolean("events.death", true)) return;
        Component msg = event.deathMessage();
        if (msg == null) return;
        String text = PlainTextComponentSerializer.plainText().serialize(msg);
        plugin.sendToDiscord(cfg().getString("events.death-message", "%message%")
                .replace("%message%", MarkdownSanitizer.escape(text)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!cfg().getBoolean("chat.minecraft-to-discord", true)) return;
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        String out = cfg().getString("chat.minecraft-format", "**%player%**: %message%")
                .replace("%player%", MarkdownSanitizer.escape(event.getPlayer().getName()))
                .replace("%message%", message);
        plugin.sendToDiscord(out);
    }
}
