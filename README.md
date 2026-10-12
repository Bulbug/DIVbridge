# DiscordBridge

Connect your [PaperMC](https://papermc.io) Minecraft server to Discord.

## What is this?

DiscordBridge links a Discord channel to your Minecraft server, so your community can stay
connected even when they aren't in the game.

**What it does**

| Feature | What you get |
|---|---|
| **Two-way chat** | Messages typed in the Discord channel show up in game, and in-game chat shows up in Discord |
| **Activity feed** | Join, leave, death, first-join, and server start/stop messages posted to Discord |
| **`/cmd` in Discord** | Run server commands (like `whitelist add Steve` or `say hello`) from Discord, with the output posted back |
| **`/start` in Discord** | Boot the server from Discord when it's turned off |

**What it looks like**

In Discord:
```
🟢 Steve joined the server (3 online)
Steve: anyone want to trade?
💀 Steve fell from a high place
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

## Setting it up on your server

### Step 1: Create the Discord bot

1. Go to the [Discord Developer Portal](https://discord.com/developers/applications) and click **New Application**. Name it something like "MC Bridge".
2. Open the **Bot** tab:
   - Click **Reset Token** and copy the token somewhere safe. It's like a password, so never share it.
   - Scroll down and turn on **Message Content Intent**. Without this, the bot can't read chat from Discord.
3. Open **OAuth2 > URL Generator**:
   - Under Scopes, tick `bot` and `applications.commands`.
   - Under Bot Permissions, tick **View Channels**, **Send Messages** and **Read Message History**.
4. Copy the generated URL, open it in your browser, and add the bot to your Discord server.

### Step 2: Copy your Discord IDs

1. In Discord, go to **User Settings > Advanced** and turn on **Developer Mode**.
2. Right-click the pieces below and choose **Copy ID** for each:
   - Your **server** (name at the top-left): this is the `guild-id`
   - The **channel** you want to use for chat and notifications: this is the `channel-id`
   - Your **own user**, or a **staff role**: these decide who may use `/cmd`

### Step 3: Install the plugin

1. Download `DiscordBridge-x.y.z.jar` from the [Releases](../../releases) page. (Or [build it yourself](#building-from-source).)
2. Put the jar in your server's `plugins/` folder.
3. Start the server once, then stop it. This creates `plugins/DiscordBridge/config.yml`.

### Step 4: Edit the config

Open `plugins/DiscordBridge/config.yml` and fill in the top section. Keep the quotes around the IDs.

```yaml
bot-token: "your-bot-token"
guild-id: "your server ID"
channel-id: "your channel ID"

commands:
  enabled: true
  allowed-user-ids: ["your user ID"]
  allowed-role-ids: []        # or ["a staff role ID"]
```

> **Important:** if both `allowed-user-ids` and `allowed-role-ids` are empty, nobody can use `/cmd`.

### Step 5: Start the server and check the console

Start the server again. You should see:
```
[DiscordBridge] Connected to Discord as <your bot name>
```
Your bot will also appear online in Discord, and a "server is online" message should be posted in your channel.

### Step 6: Test it

- Type something in the Discord channel. It should appear in game as `[Discord] YourName: ...`
- Say something in game. It should appear in the Discord channel.
- Join the server. A join message should appear in the channel.
- In Discord, type `/cmd` and pick the `command` option, for example `list`. The bot replies with the result.

### Step 7 (optional): Set up `/start`

This lets staff turn the server on from Discord. It's a separate bot that runs 24/7 on the same
machine as your Minecraft server.

1. Create a **second** application in the Developer Portal (for example "MC Launcher"), copy its token,
   and invite it with the `bot` and `applications.commands` scopes. It needs no extra permissions.
   You do **not** need Message Content Intent for this one.
2. Set it up:
   ```
   cd launcher
   pip install -r requirements.txt
   cp config.example.json config.json
   ```
3. Edit `launcher/config.json`:

   | Setting | Meaning |
   |---|---|
   | `bot_token` | Token of the **launcher** bot |
   | `guild_id` | Your Discord server ID |
   | `server_dir` | Folder containing your `paper.jar` |
   | `start_command` | How you normally start the server, as a list |
   | `server_port` | Your server port (default 25565) |
   | `allowed_role_ids` / `allowed_user_ids` | Who may use `/start` (empty = nobody) |

4. Run `python launcher_bot.py` and leave it running. Set it to start on boot so it survives restarts.

Now `/start` boots the server and tells you when it's ready, and `/status` says whether it's online.
To stop the server from Discord, use `/cmd command:stop`.

---

## Configuration

Everything is in [`plugin/src/main/resources/config.yml`](plugin/src/main/resources/config.yml) (the copy at
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
| `/cmd` doesn't show in Discord | Wait a minute, then reload Discord (Ctrl+R). Make sure the bot was invited with the `applications.commands` scope. |
| "You're not allowed to use this command" | Add your user ID or role ID under `commands` in the config, then run `/discordbridge reload`. |
| `/start` says the server exited right away | Check `start_command` and `server_dir`, then look at the server's `logs/latest.log`. |
| `/start` says it's already online but you can't join | Check `server_port` matches your real port. |

## Building from source

```
cd plugin
gradle build
```
The jar is written to `plugin/build/libs/`. (If you create the wrapper with `gradle wrapper`, use `./gradlew build`.)

To publish a release, push a tag such as `v1.0.0`. GitHub Actions builds the jar and attaches it to a release.

## Security

- **Never commit your bot tokens.** `launcher/config.json` is git-ignored; keep your real plugin
  `config.yml` out of the repo too. If a token ever leaks, reset it in the Developer Portal right away.
- Only give `/cmd` and `/start` access to people you trust.

## License

[MIT](LICENSE). Bundles [JDA](https://github.com/discord-jda/JDA) (Apache-2.0) in the plugin jar;
the launcher uses [discord.py](https://github.com/Rapptz/discord.py) (MIT).
