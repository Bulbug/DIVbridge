# DiscordBridge

Connect your [PaperMC](https://papermc.io) Minecraft server to Discord.

## What is this?

DiscordBridge links a Discord channel to your Minecraft server, so your community can stay
connected even when they aren't in the game.

**What it does**

| Feature | What you get |
|---|---|
| **Two-way chat** | Discord messages show up in game (with a hover tag and clickable attachments), and in-game chat shows up in Discord, optionally as each player (their name and head) |
| **Activity cards** | Clean embed cards with the player's head for join, leave, death, first-join, and server start/stop |
| **`/list` in Discord** | A live card showing who's online, the player count and server TPS |
| **Proximity voice** (optional) | Players link their account and join a Discord voice channel. The bot moves people near each other in the game into the same voice room. Works for Bedrock and Java players |
| **`/cmd` in Discord** | Run server commands (like `whitelist add Steve` or `say hello`) from Discord, with the output posted back |
| **`/start` in Discord** | Boot the server from Discord when it's turned off |

**What it looks like**

In Discord (these are embed cards and webhook chat, shown here as text):
```
[head] Steve joined the server          <- green card, footer "3 online"
[head] Steve: anyone want to trade?     <- chat, sent as Steve
[head] Steve fell from a high place     <- grey card
```
In game:
```
[Discord] Alex: I'll trade you iron for diamonds
```

**Who is it for?** Server owners who want a Discord community that can see what's going on, chat with
players, and let trusted staff manage the server without logging in.

**Good to know**
- It uses a real Discord *bot* (not a webhook), which is what makes two-way chat and slash commands possible.
- Messages can't ping `@everyone`, `@here` or roles, so players can't use the chat bridge to spam pings.
- `/cmd` and `/start` only work for the roles/users you choose.

### How it's put together

There are two parts:

| Part | Folder | What it does | Where it runs |
|---|---|---|---|
| **Plugin** | [`plugin/`](plugin) | Chat bridge, activity feed, `/cmd` | Inside your Paper server |
| **Launcher** (optional) | [`launcher/`](launcher) | `/start` and `/status` | Always on, on the same machine as the server |

A plugin only exists while the server is running, so it can't switch a server on. That's why `/start` comes
from the small launcher program. If you don't need `/start`, skip the launcher entirely.

**Requirements:** Paper 1.21.x, Java 21. The launcher also needs Python 3.10+.

---

## Setting it up

**The complete, step-by-step guide is [docs/SETUP.md](docs/SETUP.md).** It has the exact buttons to click,
exact text to type, example config files and a full troubleshooting table.

The short version:

1. Create a Discord bot in the [Developer Portal](https://discord.com/developers/applications), turn on
   **Message Content Intent**, and copy its token.
2. Invite it to your Discord server (the guide has a ready-made invite link with the right permissions).
3. Copy your server ID and channel ID (Discord Developer Mode, then right-click, then Copy ID).
4. Put `DiscordBridge-x.y.z.jar` in your Paper server's `plugins/` folder, start the server once, stop it.
5. Fill in `bot-token`, `guild-id` and `channel-id` in `plugins/DiscordBridge/config.yml`, then start the server.
6. Look for `Connected to Discord as ...` in the console, then test chat, `/list` and `/cmd`.

Optional extras, each with its own section in the guide:
[webhook chat](docs/SETUP.md#part-6a-optional-nicer-in-game-chat-with-a-webhook),
[`/cmd`](docs/SETUP.md#part-6b-optional-run-server-commands-from-discord-cmd),
[Discord proximity voice rooms](docs/SETUP.md#part-6c-optional-discord-proximity-voice-rooms) and
[the `/start` launcher](docs/SETUP.md#part-7-optional-start-the-server-from-discord-the-launcher).

For **real in-game voice for Bedrock players**, see
[docs/BEDROCK-VOICE-CHAT-SETUP.md](docs/BEDROCK-VOICE-CHAT-SETUP.md).

---

## Proximity voice (talk to the players near you)

> **Want real in-game voice for Bedrock players** (hear nearby players, fading with distance, no Discord needed)?
> That's a separate project, Bedrock Voice Chat. See [docs/BEDROCK-VOICE-CHAT-SETUP.md](docs/BEDROCK-VOICE-CHAT-SETUP.md).
> The Discord rooms below are the simpler option that needs no extra server.

Bedrock players can't install Java voice-chat mods, so this uses **Discord itself** for voice. Players stay in a
Discord voice channel (on their phone, PC or console) while they play. The bot watches where they are in the game
and automatically moves people who are close together into the same temporary voice room.

- Walk up to someone and you land in the same room, so you can talk.
- Walk away and you're moved to a different room, or back to the lobby if nobody is near you.
- Works for **Bedrock and Java** players on the same server.
- **The plugin never listens to or records any audio.** It only moves people between voice channels.

### Set it up

The full walkthrough, with every click, is in [docs/SETUP.md](docs/SETUP.md#part-6c-optional-discord-proximity-voice-rooms). The short version:

1. In Discord, create a **voice channel** named something like "Lobby", and a **category** (for example "Voice Rooms").
   The bot makes its temporary rooms inside that category.
2. Give the bot these permissions in the lobby and the category: **View Channel**, **Connect**, **Manage Channels**
   and **Move Members**. Make sure your players have **Connect** and **Speak** in the category too.
3. Copy the IDs of the lobby channel and the category (Developer Mode, then right-click, then Copy ID).
4. In `config.yml`, set:
   ```yaml
   voice:
     enabled: true
     lobby-channel-id: "your lobby voice channel ID"
     category-id: "your category ID"
     radius: 32
   ```
5. Restart the server. You should see `Proximity voice is on` in the console.

### How players use it

1. In Minecraft, type `/link`. You get a 6-character code (it works on Bedrock too).
2. In Discord, type `/link` and enter the code.
3. Join the **Lobby** voice channel in Discord and keep Discord open while you play.
4. Walk near other players to talk. Use `/unlink` (in game or in Discord) to remove the link.

Links are stored in `plugins/DiscordBridge/links.yml`.

### Good to know

- Everyone in a room hears each other at the same volume. There's no fading with distance.
- The bot can only move people who are **already in a voice channel**. It can't pull anyone in, so players must join the lobby first.
- The bot only touches people in the lobby or its own rooms. If someone joins a different voice channel, it leaves them alone.
- Moves are checked every few seconds (`update-seconds`), and Discord limits how fast people can be moved, so very busy servers may see small delays.
- Players in different worlds or dimensions are never put in the same room.

## Configuration

Everything, including the colors and look of the cards (`style:`), is in [`plugin/src/main/resources/config.yml`](plugin/src/main/resources/config.yml) (the copy at
`plugins/DiscordBridge/config.yml` on your server). Each message can be changed or turned off.

Placeholders: `%player%`, `%online%`, `%message%`. Messages shown in game use
[MiniMessage](https://docs.advntr.dev/minimessage/format.html) formatting.

`/discordbridge reload` (in game or console, needs `discordbridge.admin`) reloads messages and settings.
Changing the token or IDs needs a server restart.

### About `/cmd`

`/cmd` runs a command as the server console, which is **full control of your server**. So:
- Nobody can use it until you list their role or user ID.
- `op` and `deop` are blocked by default (`blocked-commands`).
- Every use is logged in the server console, with the Discord user who ran it.

## Troubleshooting

| Problem | Likely fix |
|---|---|
| Console says no token / "Could not connect to Discord" | Check `bot-token` is correct and has no extra spaces. Reset the token if unsure. |
| "Could not find the channel" | Wrong `channel-id`, or the bot can't see that channel. Check the channel's permissions. |
| "Could not find the Discord server" | Wrong `guild-id`, or the bot hasn't been invited to that server. |
| Discord messages don't appear in game | Turn on **Message Content Intent** in the Developer Portal, then restart. Make sure you're typing in the configured channel. |
| Cards show as nothing / bot errors in the console when posting | The bot needs the **Embed Links** permission in the channel. Or set `style.use-embeds: false`. |
| In-game chat still shows as the bot, not the player | `chat.webhook-url` is empty or invalid. It must start with `https://discord.com/api/webhooks/`. |
| A player's chat shows up as plain bot text | Discord rejects some webhook names (for example ones containing "discord"). The plugin falls back to plain text for those. |
| Proximity voice does nothing / console says the bot is missing a permission | Give the bot **Manage Channels**, **Move Members** and **Connect** on the lobby and the category. |
| Console says `voice.lobby-channel-id` isn't a voice channel | Copy the ID of the voice channel itself (not a text channel) and restart. |
| A player isn't being moved | They must be linked (`/link`), online in game, and already in the lobby or a bot room in Discord. After a bot restart, players already in voice may need to leave and rejoin. |
| `/link` says the code is invalid | Codes last 5 minutes and work once. Type `/link` in Minecraft for a new one. |
| `/cmd` doesn't show in Discord | Wait a minute, then reload Discord (Ctrl+R). Make sure the bot was invited with the `applications.commands` scope. |
| "You're not allowed to use this command" | Add your user ID or role ID under `commands` in the config, then run `/discordbridge reload`. |
| `/start` says the server exited right away | Check `start_command` and `server_dir`, then look at the server's `logs/latest.log`. |
| `/start` says it's already online but you can't join | Check `server_port` matches your real port. |

## Building from source

```
cd plugin
gradle build
```
The jar is written to `plugin/build/libs/DiscordBridge-1.2.0.jar`. (Details: [docs/SETUP.md](docs/SETUP.md#part-4-get-the-plugin-jar).) (If you create the wrapper with `gradle wrapper`, use `./gradlew build`.)

To publish a release, push a tag such as `v1.0.0`. GitHub Actions builds the jar and attaches it to a release.

## Security

- **Never commit your bot tokens.** `launcher/config.json` is git-ignored; keep your real plugin
  `config.yml` out of the repo too. If a token ever leaks, reset it in the Developer Portal right away.
- Only give `/cmd` and `/start` access to people you trust.

## License

[MIT](LICENSE). Bundles [JDA](https://github.com/discord-jda/JDA) (Apache-2.0) in the plugin jar;
the launcher uses [discord.py](https://github.com/Rapptz/discord.py) (MIT).
