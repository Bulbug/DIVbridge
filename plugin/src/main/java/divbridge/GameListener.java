package divbridge;

import io.papermc.paper.event.player.AsyncChatEvent;
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

    private final DIVbridge plugin;

    public GameListener(DIVbridge plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    private int online(int delta) {
        return Math.max(0, Bukkit.getOnlinePlayers().size() + delta);
    }

    private String fill(String template, Player player, int onlineDelta) {
        return template
                .replace("%player%", player.getName())
                .replace("%online%", String.valueOf(online(onlineDelta)));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!cfg().getBoolean("events.join", true)) return;
        Player p = event.getPlayer();

        String firstTemplate = cfg().getString("events.first-join-message", "");
        boolean first = !p.hasPlayedBefore() && firstTemplate != null && !firstTemplate.isEmpty();
        String template = first ? firstTemplate
                : cfg().getString("events.join-message", "%player% joined the server");

        plugin.sendEvent(first ? "first-join" : "join", fill(template, p, 0), p.getUniqueId(), online(0));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!cfg().getBoolean("events.quit", true)) return;
        Player p = event.getPlayer();
        // The leaving player is still counted while this event fires.
        plugin.sendEvent("quit",
                fill(cfg().getString("events.quit-message", "%player% left the server"), p, -1),
                p.getUniqueId(), online(-1));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!cfg().getBoolean("events.death", true)) return;
        Component msg = event.deathMessage();
        if (msg == null) return;
        Player p = event.getEntity();
        String text = PlainTextComponentSerializer.plainText().serialize(msg);
        plugin.sendEvent("death",
                cfg().getString("events.death-message", "%message%").replace("%message%", text),
                p.getUniqueId(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!cfg().getBoolean("chat.minecraft-to-discord", true)) return;
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        plugin.sendChat(event.getPlayer().getName(), event.getPlayer().getUniqueId(), message);
    }
}
