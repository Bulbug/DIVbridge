package divbridge;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Discord -> Minecraft (chat messages and slash commands). */
public class DiscordListener extends ListenerAdapter {

    private final DIVbridge plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public DiscordListener(DIVbridge plugin) {
        this.plugin = plugin;
    }

    // ---------------- chat: Discord -> Minecraft ----------------

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignoring bots and webhooks also stops our own webhook chat from echoing back into the game.
        if (!event.isFromGuild() || event.getAuthor().isBot() || event.isWebhookMessage()) return;
        if (!plugin.getConfig().getBoolean("chat.discord-to-minecraft", true)) return;

        String channelId = plugin.getConfig().getString("channel-id", "").trim();
        if (!event.getChannel().getId().equals(channelId)) return;

        String content = event.getMessage().getContentDisplay();
        List<Message.Attachment> attachments = event.getMessage().getAttachments();
        if (content.isBlank() && attachments.isEmpty()) return;

        Member member = event.getMember();
        String name = member != null ? member.getEffectiveName() : event.getAuthor().getName();

        Component line = mm.deserialize(
                plugin.getConfig().getString("chat.discord-format",
                        "<blue>[Discord]</blue> <white><user> <dark_gray>»</dark_gray> <gray><message>"),
                Placeholder.unparsed("user", name),       // unparsed = no MiniMessage injection
                Placeholder.unparsed("message", content));

        // Attachments become clickable links: hover to see the file name, click to open.
        for (Message.Attachment a : attachments) {
            line = line.append(Component.text(a.isImage() ? " [image]" : " [file]", NamedTextColor.AQUA)
                    .hoverEvent(HoverEvent.showText(Component.text(a.getFileName())))
                    .clickEvent(ClickEvent.openUrl(a.getUrl())));
        }

        final Component out = line;
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(out));
    }

    // ---------------- slash commands ----------------

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "list" -> handleList(event);
            case "cmd" -> handleCmd(event);
            case "link" -> handleLink(event);
            case "unlink" -> handleUnlink(event);
            default -> { }
        }
    }

    private void handleList(SlashCommandInteractionEvent event) {
        if (!plugin.getConfig().getBoolean("list.enabled", true)) {
            event.reply("This command is turned off.").setEphemeral(true).queue();
            return;
        }
        // Read server data on the main thread, then reply with the card.
        event.deferReply().queue(hook ->
                Bukkit.getScheduler().runTask(plugin, () ->
                        hook.editOriginalEmbeds(plugin.buildListEmbed()).queue()));
    }

    // ---------------- account linking (for proximity voice) ----------------

    private void handleLink(SlashCommandInteractionEvent event) {
        LinkManager links = plugin.links();
        if (links == null || !plugin.getConfig().getBoolean("voice.enabled", false)) {
            event.reply("Voice linking isn't turned on.").setEphemeral(true).queue();
            return;
        }
        long discordId = event.getUser().getIdLong();
        if (links.isLockedOut(discordId)) {
            event.reply("Too many wrong codes. Please wait a few minutes and try again.").setEphemeral(true).queue();
            return;
        }

        String code = event.getOption("code").getAsString().trim().toUpperCase(Locale.ROOT);
        UUID uuid = links.redeem(code);
        if (uuid == null) {
            links.recordFailure(discordId);
            event.reply("That code isn't valid or it has expired. Type `/link` in Minecraft to get a new one.")
                    .setEphemeral(true).queue();
            return;
        }

        links.link(discordId, uuid);
        Player online = Bukkit.getPlayer(uuid);
        String name = online != null ? online.getName() : "your Minecraft account";
        event.reply("✅ Linked to **" + name.replace("*", "").replace("_", "\\_") + "**. "
                        + "Join the lobby voice channel and walk up to other players to talk!")
                .setEphemeral(true).queue();

        if (online != null) {
            Bukkit.getScheduler().runTask(plugin, () -> online.sendMessage(Component.text(
                    "Linked to Discord account " + event.getUser().getName()
                            + ". Join the lobby voice channel in Discord to use proximity voice.",
                    NamedTextColor.GREEN)));
        }
    }

    private void handleUnlink(SlashCommandInteractionEvent event) {
        LinkManager links = plugin.links();
        if (links != null && links.unlinkByDiscord(event.getUser().getIdLong())) {
            event.reply("Your Minecraft account has been unlinked.").setEphemeral(true).queue();
        } else {
            event.reply("You don't have a linked account.").setEphemeral(true).queue();
        }
    }

    // ---------------- /cmd ----------------

    private void handleCmd(SlashCommandInteractionEvent event) {
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
