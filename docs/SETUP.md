# DiscordBridge: complete setup guide

This guide walks through everything, one small step at a time, with exact buttons to click and exact text to
type. Do the parts in order. Parts 1-5 give you the basics (chat, join messages, `/list`). Part 6 and
Part 7 are optional extras.

**What you'll need before you start**

| Item | How to check |
|---|---|
| A Paper server, version 1.21.x | Run `/version` in the server console. |
| Java 21 on the machine that runs the server | Run `java -version`. It should say 21 or higher. |
| A Discord server where you can manage settings | You need the **Manage Server** permission (owners have it). |
| Admin access to the server files | You'll copy a file into the `plugins` folder and edit a text file. |

**Time needed:** about 30 minutes for the basics.

## Contents

1. [Create the Discord bot](#part-1-create-the-discord-bot)
2. [Invite the bot to your Discord server](#part-2-invite-the-bot-to-your-discord-server)
3. [Copy your Discord IDs](#part-3-copy-your-discord-ids)
4. [Get the plugin jar](#part-4-get-the-plugin-jar)
5. [Install, configure and test](#part-5-install-configure-and-test)
6. [Optional: nicer chat with a webhook](#part-6a-optional-nicer-in-game-chat-with-a-webhook)
7. [Optional: `/cmd` from Discord](#part-6b-optional-run-server-commands-from-discord-cmd)
8. [Optional: Discord proximity voice rooms](#part-6c-optional-discord-proximity-voice-rooms)
9. [Optional: `/start` (the launcher bot)](#part-7-optional-start-the-server-from-discord-the-launcher)
10. [Troubleshooting](#troubleshooting)
11. [Updating and backups](#updating-and-backups)

---

## Part 1: Create the Discord bot

1. Go to https://discord.com/developers/applications and log in.
2. Click **New Application** (top right).
3. Type a name, for example `MC Bridge`. Tick the terms checkbox and click **Create**.
4. On the left menu, click **General Information**. Find **Application ID** and click **Copy**. Paste it
   into a notepad file and label it `APPLICATION ID`. You need it in Part 2.
5. On the left menu, click **Bot**.
6. Click **Reset Token**, confirm, and then click **Copy**. Paste it into your notepad and label it
   `BOT TOKEN`.
   - **Treat the token like a password.** Anyone with it can control your bot. You can only see it right
     after you reset it. If you lose it, click **Reset Token** again to get a new one.
7. Scroll down to **Privileged Gateway Intents** and turn **ON**:
   - **Message Content Intent** (required, otherwise the bot can't read chat from Discord)
   - Leave "Presence Intent" and "Server Members Intent" **off**.
8. Click **Save Changes** if the bar appears at the bottom.
9. (Recommended) On the same page, turn **OFF** "Public Bot" so only you can add this bot to servers.

> The proximity voice feature needs no extra intents in the portal.

## Part 2: Invite the bot to your Discord server

Open this link in your browser after replacing `YOUR_APPLICATION_ID` with the number from Part 1, step 4:

```
https://discord.com/oauth2/authorize?client_id=YOUR_APPLICATION_ID&scope=bot%20applications.commands&permissions=84992
```

- `permissions=84992` gives the bot: View Channels, Send Messages, Embed Links and Read Message History.
- If you want the **proximity voice** feature (Part 6c), use this link instead, which adds Manage Channels,
  Connect and Move Members:

```
https://discord.com/oauth2/authorize?client_id=YOUR_APPLICATION_ID&scope=bot%20applications.commands&permissions=17910800
```

Then:
1. Choose your Discord server from the drop-down and click **Continue**.
2. Click **Authorize** and complete the captcha.
3. In Discord, check that the bot now shows in your member list (it will be offline for now).

**Alternative to the links:** in the portal go to **OAuth2 > URL Generator**, tick scopes `bot` and
`applications.commands`, tick the permissions above, and copy the URL at the bottom.

## Part 3: Copy your Discord IDs

1. In Discord, open **User Settings** (the gear next to your name) > **Advanced** and turn on
   **Developer Mode**. (On the phone app it's also under Settings > Advanced.)
2. Now you can right-click things and choose **Copy ID** (long-press on a phone). Copy each of these into
   your notepad:

| Label it | How to copy it |
|---|---|
| `SERVER ID` | Right-click your **server icon** (top left) > **Copy Server ID** |
| `CHANNEL ID` | Right-click the **text channel** for chat and notifications > **Copy Channel ID** |
| `MY USER ID` | Right-click **your own name** in the member list > **Copy User ID** |
| `STAFF ROLE ID` (optional) | Server Settings > **Roles**, right-click a role > **Copy Role ID** |

Each ID is a long number like `123456789012345678`.

3. **If the channel is private**, give the bot access: right-click the channel > **Edit Channel** >
   **Permissions** > **Add members or roles**. Pick the bot's role (named like the bot), then allow
   **View Channel**, **Send Messages**, **Embed Links** and **Read Message History**.

## Part 4: Get the plugin jar

**Option A: download a release** (if the project is published on GitHub): go to the project's
**Releases** page and download `DiscordBridge-x.y.z.jar`. Then skip to Part 5.

**Option B: build it yourself from this folder.**

1. Install **Java 21** (JDK, not just the JRE). A free one is Eclipse Temurin from https://adoptium.net.
2. Install **Gradle** from https://gradle.org/install (or use IntelliJ IDEA, below).
3. Open a terminal (Command Prompt or PowerShell on Windows) in the `plugin` folder and run:

   ```
   gradle build
   ```
   The first run downloads libraries and can take a few minutes. It should end with `BUILD SUCCESSFUL`.
4. Your jar is at `plugin/build/libs/DiscordBridge-1.2.0.jar`.

**Using IntelliJ IDEA instead:** File > Open, choose the `plugin` folder, wait for the Gradle import to
finish, open the **Gradle** panel on the right, and double-click **Tasks > build > build**.

If the build fails, copy the first error line and see [Troubleshooting](#troubleshooting).

## Part 5: Install, configure and test

### 5.1 Install

1. Stop your Minecraft server.
2. Copy `DiscordBridge-1.2.0.jar` into the server's `plugins` folder.
3. Start the server. Wait until it says it's done loading.
4. In the console you will see a warning like `No bot token set in plugins/DiscordBridge/config.yml`.
   That is expected. It means the plugin loaded and created its config file.
5. Stop the server again.

### 5.2 Edit the config

Open `plugins/DiscordBridge/config.yml` in a text editor (Notepad, VS Code, nano). Change **only the first
three lines**. Keep the quotes:

```yaml
bot-token: "paste the BOT TOKEN here"
guild-id: "paste the SERVER ID here"
channel-id: "paste the CHANNEL ID here"
```

A filled-in example (these are fake numbers):

```yaml
bot-token: "MTIzNDU2Nzg5MDEyMzQ1Njc4.GabcDE.exampleexampleexampleexampleexample"
guild-id: "111111111111111111"
channel-id: "222222222222222222"
```

Rules:
- Keep the double quotes and don't add spaces inside them.
- Use the spaces (not tabs) the file already uses for indentation.
- Save the file as plain text.

Further down, find the `commands:` section. To let yourself use `/cmd` (Part 6b), put your user ID here:

```yaml
commands:
  enabled: true
  allowed-role-ids: []
  allowed-user-ids: ["paste MY USER ID here"]
  blocked-commands: ["op", "deop"]
```

### 5.3 Start and check

1. Start the server.
2. Look in the console for this line:

   ```
   [DiscordBridge] Connected to Discord as MC Bridge
   ```
3. In Discord, your bot should now show as **online**, and a card saying "The server is now online"
   should appear in your channel.

If you see a warning instead, jump to [Troubleshooting](#troubleshooting).

### 5.4 Test everything (checklist)

| Test | What you should see |
|---|---|
| Type `hello` in the Discord channel | In game: `[Discord] YourName » hello` in chat |
| Say `hi` in game chat | In Discord: `**YourName**: hi` from the bot |
| Join the server | A green card in Discord: "YourName joined the server", with the player head |
| Leave the server | A red card: "YourName left the server" |
| Type `/list` in Discord | A blue card with players online, the max, and TPS |
| Type `/cmd` in Discord, choose `command`, enter `list` | A reply "Ran `/list`" with the output (you must be in `allowed-user-ids`) |

Slash commands can take up to a minute to show up the first time. If `/list` doesn't appear, press
`Ctrl+R` (or `Cmd+R`) in Discord to reload it.

You now have the basics working.

---

## Part 6a: Optional: nicer in-game chat with a webhook

Without this, in-game chat appears as the bot saying `**Name**: message`. With a webhook, it appears as
the player, with their name and head, like a normal chat app.

1. In Discord, right-click your chat channel > **Edit Channel** > **Integrations** > **Webhooks** >
   **New Webhook**.
2. Click the new webhook, then **Copy Webhook URL**. (The name and picture don't matter. They're replaced
   per message.)
3. In `config.yml`, paste it:

   ```yaml
   chat:
     webhook-url: "https://discord.com/api/webhooks/123456789/abcdefg..."
   ```
4. In the game or console, run `/discordbridge reload`.
5. Say something in game. It should now show up under the player's name.

Notes:
- Treat the URL like a password. Anyone with it can post in that channel.
- It must be a webhook **for the same channel** as `channel-id`.
- Discord rejects a few names (for example ones containing "discord"). Those messages automatically fall back
  to plain bot text.

## Part 6b: Optional: run server commands from Discord (`/cmd`)

`/cmd` runs a command as the server console. **This is full control of your server**, so only list people
you completely trust.

1. Make sure your ID is listed in `config.yml` (see 5.2), or use a role ID in `allowed-role-ids` to allow
   everyone with that role.
2. Run `/discordbridge reload`.
3. In Discord type `/cmd`, pick the `command` option and enter a command **without** the leading `/`:

| You type | What happens |
|---|---|
| `say Server restarts in 5 minutes` | Broadcasts the message in game |
| `whitelist add Steve` | Adds Steve to the whitelist |
| `kick Steve` | Kicks Steve |
| `list` | Shows who's online |
| `stop` | Stops the server (use `/start` from the launcher to bring it back) |

- `op` and `deop` are blocked by default (`blocked-commands`). Add more command names to that list to block
  them too.
- Every use is logged in the server console with the Discord user who ran it.
- If both `allowed-role-ids` and `allowed-user-ids` are empty, **nobody** can use `/cmd`.

## Part 6c: Optional: Discord proximity voice rooms

This moves players between Discord voice channels so they hear the players near them in the game. It works
for Bedrock and Java players. Players use the Discord app for voice. (For real in-game voice, see
[BEDROCK-VOICE-CHAT-SETUP.md](BEDROCK-VOICE-CHAT-SETUP.md).)

### 6c.1 Prepare Discord

1. Make sure you invited the bot with the **voice** link in Part 2 (permissions `17910800`). If not,
   re-open that link. It updates the permissions.
2. In Discord, click the **+** next to your channel list > **Create Category**. Name it `Voice Rooms`.
   Leave "Private Category" off.
3. Right-click the `Voice Rooms` category > **Create Channel** > choose **Voice** > name it `Lobby` >
   **Create Channel**.
4. Copy the IDs: right-click the **Lobby** channel > **Copy Channel ID** (label it `LOBBY ID`), and
   right-click the **Voice Rooms** category > **Copy Channel ID** (label it `CATEGORY ID`).
5. Check permissions on the category: right-click it > **Edit Category** > **Permissions**.
   - `@everyone` should have **View Channel**, **Connect** and **Speak** allowed (the defaults).
   - The bot's role needs **View Channel**, **Connect**, **Manage Channels** and **Move Members**.
     If the category is public and you used the voice invite link, this is already true.

### 6c.2 Configure the plugin

In `config.yml`, find the `voice:` section at the bottom and fill it in:

```yaml
voice:
  enabled: true
  lobby-channel-id: "paste LOBBY ID here"
  category-id: "paste CATEGORY ID here"
  radius: 32
  leave-radius-multiplier: 1.3
  update-seconds: 5
  room-name: "Nearby %n%"
```

Then **restart the server** (a reload isn't enough for this part). In the console you should see:

```
[DiscordBridge] Proximity voice is on (radius 32.0 blocks).
```

### 6c.3 What your players do

Send your players these steps:

1. In Minecraft, type `/link`. You'll get a 6-character code. (It works on Bedrock too.)
2. In Discord, type `/link` and paste the code. It expires after 5 minutes and works once.
3. Join the **Lobby** voice channel in Discord. Keep Discord open (on a phone, PC or console) while you play.
4. Walk near other players. You'll be moved into the same room automatically. Walk away and you'll be moved
   out.
5. To stop, type `/unlink` in game or in Discord.

### 6c.4 Test it

1. Link two accounts and put both players in the Lobby voice channel.
2. Stand about 5 blocks apart. Within about 5-10 seconds both should be moved into a new channel named
   `Nearby 1`.
3. Walk about 50 blocks apart. They should be moved out (to the lobby).
4. Check that `Nearby 1` is deleted once it's empty.

Things to know: people must already be in a voice channel (the bot can't pull anyone in), everyone in a room
hears each other at the same volume, and moves are checked every `update-seconds`.

---

## Part 7: Optional: start the server from Discord (the launcher)

A plugin can't start a server that's turned off. The launcher is a small program that stays on, 24/7, **on
the same computer as your Minecraft server**, and starts it when someone types `/start`.

### 7.1 Make a second bot

Repeat Part 1 and Part 2 to create a **second** application named, for example, `MC Launcher`. Differences:
- You do **not** need Message Content Intent.
- Use this invite link (no special permissions needed):

```
https://discord.com/oauth2/authorize?client_id=LAUNCHER_APPLICATION_ID&scope=bot%20applications.commands&permissions=0
```
- Label its token `LAUNCHER TOKEN`.

### 7.2 Install Python

1. Go to https://www.python.org/downloads/ and install Python 3.10 or newer.
   - **On Windows, tick "Add python.exe to PATH"** on the first installer screen.
2. Check it: open a terminal and run `python --version` (on Linux/macOS, `python3 --version`).

### 7.3 Set up the launcher

In a terminal, go to the `launcher` folder.

**Windows:**
```
pip install -r requirements.txt
copy config.example.json config.json
```

**Linux (Ubuntu 24.04 and similar, which block system-wide pip):**
```
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
cp config.example.json config.json
```

### 7.4 Edit `config.json`

Windows example:

```json
{
  "bot_token": "paste the LAUNCHER TOKEN here",
  "guild_id": "paste the SERVER ID here",

  "server_dir": "C:/Minecraft/paper-server",
  "start_command": ["java", "-Xms2G", "-Xmx4G", "-jar", "paper-1.21.11-69.jar", "--nogui"],

  "server_host": "127.0.0.1",
  "server_port": 25565,
  "boot_timeout_seconds": 180,

  "allowed_role_ids": [],
  "allowed_user_ids": ["paste MY USER ID here"]
}
```

Linux example: `"server_dir": "/home/mc/server"`.

Fill in:
- `server_dir`: the folder that contains your Paper jar. On Windows use forward slashes (`C:/...`).
- `start_command`: the same command you use to start the server, split into pieces. **The jar file name must
  match your real file name exactly.** Change `-Xmx4G` to the memory you want to give the server.
- `server_port`: the port in your `server.properties` (`server-port=`). Default is 25565.
- `allowed_user_ids` / `allowed_role_ids`: who may use `/start`. If both are empty, nobody can.

Don't commit `config.json` anywhere public. It contains a token.

### 7.5 Run it

- Windows: `python launcher_bot.py`
- Linux: `.venv/bin/python launcher_bot.py`

Leave the window open. In Discord, type `/status`. It should say the server is online or offline. With the
server stopped, type `/start`. It replies "Starting the server...", then "The server is online!" when ready.
On Windows, a console window opens for the server.

### 7.6 Keep it running after a reboot

**Windows (Task Scheduler):**
1. Press Start, search **Task Scheduler**, open it, click **Create Task...** (not "Basic").
2. General: name `MC Launcher`; tick **Run whether user is logged on or not**.
3. Triggers > New > **At startup**.
4. Actions > New > Program: the full path to `python.exe` (find it with `where python`), Arguments:
   `launcher_bot.py`, Start in: the full path of the `launcher` folder.
5. OK, enter your Windows password if asked.

**Linux (systemd):** create `/etc/systemd/system/mc-launcher.service` with this (change `mc` and the paths to
yours):

```ini
[Unit]
Description=Minecraft launcher bot
After=network-online.target
Wants=network-online.target

[Service]
User=mc
WorkingDirectory=/home/mc/DiscordBridge/launcher
ExecStart=/home/mc/DiscordBridge/launcher/.venv/bin/python launcher_bot.py
Restart=always
RestartSec=10
# Important: if this service restarts, don't kill the Minecraft server it started.
KillMode=process

[Install]
WantedBy=multi-user.target
```

Then:
```
sudo systemctl daemon-reload
sudo systemctl enable --now mc-launcher
sudo systemctl status mc-launcher
```
View logs with `journalctl -u mc-launcher -f`.

To **stop** the server from Discord, use `/cmd command:stop` (Part 6b).

---

## Troubleshooting

### Console messages from the plugin

| What you see | What it means and what to do |
|---|---|
| `No bot token set in plugins/DiscordBridge/config.yml...` | The token is still `PASTE_BOT_TOKEN_HERE` or empty. Do 5.2, then restart. |
| `Could not connect to Discord: ... (is bot-token correct?)` | The token is wrong, has spaces, or was reset. Reset it in the portal (Bot > Reset Token) and paste the new one. Also check that **Message Content Intent** is ON. |
| `Could not find the channel. Check channel-id...` | Wrong channel ID, or the bot can't see the channel (see Part 3, step 3). Copy the ID from the **text channel** itself. |
| `Could not find the Discord server. Check guild-id...` | Wrong server ID, or the bot wasn't invited to that server (Part 2). |
| `Could not send to Discord: ...Missing Permissions` | The bot lacks Send Messages or Embed Links in that channel. Fix the channel permissions. |
| `Could not register slash commands` | The bot wasn't invited with the `applications.commands` scope. Re-do Part 2 with the link above. |
| `voice.enabled is true but voice.lobby-channel-id is not a voice channel...` | The lobby ID is wrong, or it's a text channel. Copy the ID of the **voice** channel. |
| `Proximity voice: the bot is missing a permission...` | Give the bot Manage Channels, Move Members and Connect on the lobby and the category (Part 6c.1). |

### Things that don't work

| Problem | Fix |
|---|---|
| Bot is offline in Discord | The server isn't running, or the token is wrong. Check the console. |
| Discord messages don't appear in game | Turn ON Message Content Intent (Part 1, step 7), restart, and type in the exact channel from `channel-id`. |
| Cards don't appear, only plain text, or nothing posts | The bot needs **Embed Links**. Or set `style.use-embeds: false`. |
| `/list` or `/cmd` don't show up | Wait a minute, press `Ctrl+R` in Discord. If still missing, re-invite with `applications.commands` and restart the server. |
| "You're not allowed to use this command." | Add your user ID or role ID under `commands:` in the config, save, then run `/discordbridge reload`. |
| In-game chat still shows as the bot, not the player | `chat.webhook-url` is empty or isn't a valid `https://discord.com/api/webhooks/...` URL. Reload after changing. |
| A player isn't moved in voice | They must be linked (`/link`), online in game, and already in the Lobby or a `Nearby` room. After a plugin restart, they may need to leave and rejoin voice. |
| `/link` says the code is invalid | Codes last 5 minutes and work once. Type `/link` in Minecraft for a new one. After 5 wrong tries you're locked out for 10 minutes. |
| Build fails: `Unsupported class file major version` or Java error | You need JDK **21** to build. Check `java -version` and `gradle -v`. |
| Build fails: can't download dependencies | Check your internet and try again. Some networks block `repo.papermc.io`. |
| `/start`: "The server exited right away" | `start_command` or `server_dir` is wrong. Run the same command by hand in `server_dir` to see the error. Check `logs/latest.log`. |
| `/start` says already online but you can't join | `server_port` doesn't match your `server.properties`. |

**Still stuck?** Copy the lines from the server console that contain `DiscordBridge`, plus what you did, and
ask for help.

## Updating and backups

**Updating the plugin**
1. Stop the server.
2. Replace the old jar in `plugins` with the new one (delete the old one).
3. Start the server. Your `config.yml` and `links.yml` are kept.
4. New versions may add new settings. The plugin does **not** add them to your existing `config.yml`. To see
   them, rename your `config.yml` (for example to `config.old.yml`), start the server to get a fresh one, and copy your
   values across.

**Back up these files** (copy them somewhere safe now and then):
- `plugins/DiscordBridge/config.yml` (your token and settings)
- `plugins/DiscordBridge/links.yml` (players' linked Discord accounts)
- `launcher/config.json` (if you use the launcher)

**If a token leaks:** go to the Developer Portal > your application > **Bot** > **Reset Token**, paste the new
token into the config, and restart.
