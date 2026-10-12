package io.github.kaz.discordbridge;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Discord -> Minecraft (chat messages and the /cmd slash command). */
public class DiscordListener extends ListenerAdapter {

    private final DiscordBridge plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public DiscordListener(DiscordBridge plugin) {
        this.plugin = plugin;
    }

    // ---------------- chat: Discord -> Minecraft ----------------

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot() || event.isWebhookMessage()) return;
        if (!plugin.getConfig().getBoolean("chat.discord-to-minecraft", true)) return;

        String channelId = plugin.getConfig().getString("channel-id", "").trim();
        if (!event.getChannel().getId().equals(channelId)) return;

        String content = event.getMessage().getContentDisplay();
        if (!event.getMessage().getAttachments().isEmpty()) {
            content = (content + " [attachment]").trim();
        }
        if (content.isBlank()) return;

        Member member = event.getMember();
        String name = member != null ? member.getEffectiveName() : event.getAuthor().getName();

        Component line = mm.deserialize(
                plugin.getConfig().getString("chat.discord-format",
                        "<dark_aqua>[Discord] <white><user><gray>: <white><message>"),
                Placeholder.unparsed("user", name),       // unparsed = no MiniMessage injection
                Placeholder.unparsed("message", content));

        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(line));
    }

    // ---------------- /cmd ----------------

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("cmd")) return;

        if (!plugin.getConfig().getBoolean("commands.enabled", true) || !isAllowed(event.getMember())) {
            event.reply("You're not allowed to use this command.").setEphemeral(true).queue();
            return;
        }

        String command = event.getOption("command").getAsString().trim();
        if (command.startsWith("/")) command = command.substring(1).trim();
        if (command.isEmpty()) {
            event.reply("Give me a command to run.").setEphemeral(true).queue();
            return;
        }

        // Block by first word, ignoring case and any "minecraft:" style prefix.
        String first = command.split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (first.contains(":")) first = first.substring(first.indexOf(':') + 1);
        List<String> blocked = plugin.getConfig().getStringList("commands.blocked-commands");
        for (String b : blocked) {
            if (b.equalsIgnoreCase(first)) {
                event.reply("`" + first + "` is blocked from Discord.").setEphemeral(true).queue();
                return;
            }
        }

        final String finalCommand = command;
        final String who = event.getUser().getName() + " (" + event.getUser().getId() + ")";
        event.deferReply().queue(hook -> run(hook, finalCommand, who));
    }

    private boolean isAllowed(Member member) {
        if (member == null) return false;
        if (plugin.getConfig().getStringList("commands.allowed-user-ids").contains(member.getId())) {
            return true;
        }
        List<String> roleIds = plugin.getConfig().getStringList("commands.allowed-role-ids");
        for (Role r : member.getRoles()) {
            if (roleIds.contains(r.getId())) return true;
        }
        return false;
    }

    private void run(InteractionHook hook, String command, String who) {
        List<String> output = Collections.synchronizedList(new ArrayList<>());
        // A sender that collects whatever the command prints back.
        CommandSender sender = Bukkit.createCommandSender(
                c -> output.add(PlainTextComponentSerializer.plainText().serialize(c)));

        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getLogger().info("Discord user " + who + " ran: /" + command);
            boolean ok;
            try {
                ok = Bukkit.dispatchCommand(sender, command);
            } catch (Exception e) {
                hook.editOriginal("The command threw an error: " + e.getMessage()).queue();
                return;
            }
            final boolean success = ok;
            // Give the command half a second to print its output, then reply.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                StringBuilder sb = new StringBuilder();
                sb.append(success ? "✅ Ran `/" : "⚠️ Unknown or failed command `/")
                  .append(command.replace("`", "'")).append("`");
                String text = String.join("\n", output).replace("```", "'''");
                if (!text.isBlank()) {
                    if (text.length() > 1700) text = text.substring(0, 1700) + "\n…";
                    sb.append("\n```\n").append(text).append("\n```");
                }
                hook.editOriginal(sb.toString()).queue();
            }, 10L);
        });
    }
}
