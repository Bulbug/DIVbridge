# In-game proximity voice for Bedrock players (Paper + Geyser): complete setup guide

This sets up **Bedrock Voice Chat (BVC)**, a separate open-source project, so Bedrock players on your Geyser
server can talk in game and **only hear the players near them**, with voices fading as people walk apart.

> DiscordBridge does not include this voice system. This guide is based on the BVC wiki
> (https://github.com/Alaydriem/bedrock-voice-chat/wiki) and has not been tested by the author. BVC changes
> over time. If something here disagrees with the wiki, trust the wiki. Places where I'm unsure are marked
> **(check)**.

## Contents

1. [How it works](#1-how-it-works)
2. [What you need](#2-what-you-need)
3. [Get a server and a domain](#3-get-a-server-and-a-domain)
4. [Prepare the server (Ubuntu)](#4-prepare-the-server-ubuntu)
5. [Get a TLS certificate](#5-get-a-tls-certificate)
6. [Create the access token and config](#6-create-the-access-token-and-config)
7. [Run the BVC server](#7-run-the-bvc-server)
8. [Install the Paper plugin](#8-install-the-paper-plugin)
9. [Allow your players](#9-allow-your-players)
10. [Message to send your players](#10-message-to-send-your-players)
11. [Test with two people](#11-test-with-two-people)
12. [Tune who can hear whom](#12-tune-who-can-hear-whom)
13. [Hosting at home instead](#13-hosting-at-home-instead)
14. [Maintenance](#14-maintenance)
15. [Troubleshooting](#15-troubleshooting)
16. [Final checklist](#16-final-checklist)

---

## 1. How it works

```
 Player's phone/PC            Your BVC voice server              Your Minecraft server
  (BVC app)  <--- voice --->  voice.yourdomain.com  <--- positions --- Paper + Geyser
                                   (a VPS)                    + BedrockVoiceChat plugin
```

BVC has three parts and all three are required:

1. **A plugin on your Paper server** that reports every player's position.
2. **The BVC server**, a separate program that carries the voice. It is **not** a plugin.
3. **The BVC app** on each player's device. Players connect the app to the **BVC server**, not to Minecraft.

Players still join Minecraft through Geyser as usual and keep the app open while they play.

## 2. What you need

| You need | Details |
|---|---|
| Paper server with **Geyser and Floodgate** | Check with `/plugins`: you should see `Geyser-Spigot` and `floodgate`. Floodgate is detected automatically, so Bedrock players are matched by Xbox gamertag. |
| A server for the BVC program | A small VPS is easiest (steps below use Ubuntu 24.04). Most game hosts won't let you run it. |
| A domain name | You'll point a name like `voice.yourdomain.com` at the VPS. |
| A TLS certificate from a trusted authority | Free from Let's Encrypt (step 5). Self-signed certificates are refused by the apps. |
| Open ports | TCP **and** UDP on 443 (plus TCP 80 briefly, to get the certificate). |
| A device per player | Windows, Linux, macOS, iPhone/iPad, or Android. Console players (Xbox, PlayStation, Switch) use their **phone**. |

**Small server shortcut:** the Paper plugin can run the voice server *inside* Minecraft ("embedded mode").
The BVC wiki recommends external mode (this guide) for 15-20+ players or when hosting several servers, and
says embedded mode suits only a few players. Embedded mode still needs the certificate and open ports on the
Minecraft machine. See the "Minecraft Java Support" wiki page for its settings.

## 3. Get a server and a domain

**Server (VPS):**
- Operating system: **Ubuntu 24.04 LTS**.
- Size: start with **2 CPU cores and 2-4 GB RAM**. BVC uses CPU more than memory, so add cores before memory
  if it struggles.
- Location: pick a region close to most of your players (this lowers voice delay).
- Any provider works (the BVC wiki mentions DigitalOcean and Hetzner as examples).
- Write down the server's **public IP address**, for example `203.0.113.10`.

**Domain:**
1. Use a domain you own, or buy one from any registrar.
2. In the registrar's DNS settings, add an **A record**:
   - Name/host: `voice`
   - Value: your VPS IP (`203.0.113.10`)
   - TTL: automatic or 300
3. Wait a few minutes, then check from your own computer: `nslookup voice.yourdomain.com`
   It should print your VPS IP.

## 4. Prepare the server (Ubuntu)

Connect to the VPS from your computer's terminal (Windows 10/11 has `ssh` built in):

```
ssh root@203.0.113.10
```

Then run these one at a time.

**Update the system:**
```
apt update && apt upgrade -y
```

**Install Docker** (this is Docker's official install script):
```
curl -fsSL https://get.docker.com | sh
```
Check it works: `docker --version`

**Set up the firewall.** Allow SSH **first** so you don't lock yourself out:
```
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw enable
ufw status
```
Type `y` when asked to enable. `ufw status` should list all four rules.

> If your VPS provider has its own firewall in its web dashboard (a "cloud firewall"), open the same ports
> there too: TCP 22, TCP 80, TCP 443, UDP 443.

## 5. Get a TLS certificate

We'll use Let's Encrypt's `certbot`.

```
apt install -y certbot
certbot certonly --standalone -d voice.yourdomain.com
```

- Enter your email when asked and agree to the terms.
- Port 80 must be reachable and nothing else may be using it. If it fails, re-check the DNS record and the
  firewall rules.
- On success, your files are here:
  - `/etc/letsencrypt/live/voice.yourdomain.com/fullchain.pem`
  - `/etc/letsencrypt/live/voice.yourdomain.com/privkey.pem`

Certificates last 90 days and certbot renews them automatically. Test that renewal works:
```
certbot renew --dry-run
```

BVC only reads the certificate when it starts, so make it restart after each renewal. Create this file:
```
nano /etc/letsencrypt/renewal-hooks/deploy/restart-bvc.sh
```
with this content:
```
#!/bin/sh
docker restart bvc
```
Save (Ctrl+O, Enter, Ctrl+X) and make it runnable:
```
chmod +x /etc/letsencrypt/renewal-hooks/deploy/restart-bvc.sh
```

## 6. Create the access token and config

**Make the access token** (a shared secret, 32 letters/numbers). On the VPS:
```
openssl rand -hex 16
```
It prints something like `9f2c41a7b0d3e85c6a1f7d29b3e04c58`. Copy it and keep it private. You'll use the
**same** value in two places (the server config below and the Paper plugin in step 8).

**Create the BVC folder and config:**
```
mkdir -p /opt/bvc
nano /opt/bvc/config.hcl
```

Paste this, replacing the three highlighted values (domain, IP, token):

```hcl
server {
    port      = 443
    quic_port = 443

    tls {
        names       = ["voice.yourdomain.com"]
        ips         = ["203.0.113.10"]
        certificate = "/etc/letsencrypt/live/voice.yourdomain.com/fullchain.pem"
        key         = "/etc/letsencrypt/live/voice.yourdomain.com/privkey.pem"
        certs_path  = "/data/certificates"
    }

    minecraft {
        access_token = "9f2c41a7b0d3e85c6a1f7d29b3e04c58"
    }

    features {
        code_login = true
        # By default BVC sends anonymous usage numbers (no player names or IDs).
        # Remove the "#" on the next line to turn that off.
        # telemetry = false
    }
}
```

What each part is:
- `names` / `ips`: the domain and IP clients use to reach the server.
- `certificate` / `key`: the Let's Encrypt files from step 5.
- `certs_path`: where BVC keeps its own internal certificate authority. It's inside `/data`, which we save to
  your disk in the next step. Don't delete it.
- `access_token`: the secret you just made.

Save and close (Ctrl+O, Enter, Ctrl+X).

## 7. Run the BVC server

1. Find the newest **server** version on the BVC GitHub Releases page:
   https://github.com/Alaydriem/bedrock-voice-chat/releases
   Note the version number (call it `X.Y.Z`).
2. Run it (replace `X.Y.Z`):

```
docker run -d --name bvc --restart=always \
  -p 443:443/tcp -p 443:443/udp \
  -v /opt/bvc:/data \
  -v /etc/letsencrypt:/etc/letsencrypt:ro \
  ghcr.io/alaydriem/bedrock-voice-chat/server:X.Y.Z
```

What it does: starts BVC, restarts it after reboots, opens port 443 for TCP and UDP, saves its files to
`/opt/bvc`, and lets it read your certificates (read-only).

3. Check the logs for errors:
```
docker logs -f bvc
```
Press Ctrl+C to leave the logs. The container keeps running.

4. Check it from outside. On your **own computer**, open this in a browser:
```
https://voice.yourdomain.com/api/config
```
You should get a page of text (JSON) with **no certificate warning**.

- Certificate warning: the certificate doesn't match the domain, or step 5 didn't finish.
- Page doesn't load: the firewall, the DNS record, or the cloud firewall (check TCP **and** UDP 443).

> Docker not for you? The wiki also supports running the server program directly on Linux or Windows. See
> "BVC Server Installation" on the wiki. The config file is the same.

## 8. Install the Paper plugin

On your Minecraft server:

1. Download the **PaperMC** plugin from https://modrinth.com/mod/bedrock-voice-chat and pick the file for
   Paper. On that page, check which Minecraft/Paper versions it lists and that yours is among them **(check)**.
2. Stop the server.
3. Copy the jar into the server's `plugins` folder.
4. Create the folder `plugins/BedrockVoiceChat` if it isn't there.
5. Inside it create a file named exactly `config.yaml` (note: **`.yaml`**, not `.yml`) containing:

```yaml
bvc-server: "https://voice.yourdomain.com"
access-token: "9f2c41a7b0d3e85c6a1f7d29b3e04c58"
minimum-players: 1
use-embedded-server: false
```

   - `bvc-server`: your voice server's address, with `https://`, no trailing slash.
   - `access-token`: **exactly** the token from step 6.
   - `minimum-players: 1`: position updates start once at least this many players are online.
   - `use-embedded-server: false`: use the separate server (external mode).
6. Start the server. Run `/plugins`. `BedrockVoiceChat` should be green. Look through the console startup lines
   for any red errors mentioning `BedrockVoiceChat`.

## 9. Allow your players

BVC denies everyone by default. For each player, run on the BVC server:

```
docker exec bvc bedrock-voice-chat-server user add --player TheirGamertag --game minecraft
```

**(check)** I haven't confirmed that exact program name and path inside the container. If you get
"executable not found", read the wiki's **Command Line Interface** page for the right way to run it with
Docker. That page also explains login codes and creating the first admin.

Use the player's real Xbox **gamertag** (the name Floodgate shows for Bedrock players, without any prefix
dot).

## 10. Message to send your players

Copy this, fill in the blanks, and send it:

> **Voice chat setup (about 5 minutes)**
>
> 1. Install the **Bedrock Voice Chat** app:
>    - Windows / Linux: download from https://www.bedrockvoicechat.com
>    - iPhone / iPad / Mac: the TestFlight link on the BVC wiki
>    - Android: Google Play (search "Bedrock Voice Chat")
>    - On Xbox / PlayStation / Switch: use the app on your **phone** while you play on the console.
> 2. Open the app and enter the voice server address: **voice.yourdomain.com**
> 3. Log in with the same Microsoft/Xbox account you play Minecraft with.
> 4. Join our Minecraft server as normal, and keep the app open while you play.
> 5. Allow the microphone when your device asks. On a phone, use earbuds so the game sound doesn't echo
>    into your mic.
> 6. You'll only hear players near you. Walk closer to talk to someone.
>
> If it doesn't work, tell me your gamertag and what you see on screen.

The exact login screen steps are on the wiki page "Logging In to Bedrock Voice Chat Client" **(check)**.

## 11. Test with two people

1. Both players join Minecraft (through Geyser) and open the app, connected to your voice server.
2. In the app, check you're connected, your mic is allowed, and you aren't muted.
3. Stand about 5 blocks apart and talk. You should hear each other clearly.
4. Walk about 25 blocks apart. The voice should be quieter.
5. Walk about 50+ blocks apart. You should hear nothing.
6. Test again after a player rejoins Minecraft.

With the default settings, voices are at full volume within 12 blocks and fade to silence by 48 blocks.

## 12. Tune who can hear whom

These settings are in `/opt/bvc/config.hcl`, in the `voice` block under `spatial_audio`. Defaults:

| Setting | Default | What it does |
|---|---|---|
| `broadcast_range` | 48 | Beyond this many blocks, audio isn't sent at all |
| `close_threshold` | 12 | Within this distance, voices are at full volume |
| `falloff_distance` | 48 | The distance over which the voice fades to silence |
| `steepen_start` | 38 | Where the fade gets faster |
| `max_attenuation_db` | 40 | How quiet it gets at the far end |

**Starting points I suggest for "only hear the nearest"** (my suggestions, not from the BVC docs):

| Style | broadcast_range | close_threshold | falloff_distance | steepen_start |
|---|---|---|---|---|
| Default | 48 | 12 | 48 | 38 |
| Nearby | 24 | 8 | 24 | 19 |
| Very close only | 16 | 5 | 16 | 13 |

Make `steepen_start` a bit lower than `falloff_distance`. After editing, restart the voice server:
```
docker restart bvc
```
**(check)** The wiki lists these names but I haven't seen the exact `config.hcl` layout for this block, so
confirm the syntax on the "BVC Server Installation" page before editing.

## 13. Hosting at home instead

You can run the BVC server on a PC at home, but it's more work:
- Give the PC a fixed local IP address, and **port-forward TCP and UDP 443** from your router to it.
- Your home IP changes, so use a dynamic DNS service (DuckDNS, No-IP) so `voice.yourdomain.com` always
  points at it.
- Get the certificate the same way, but note certbot needs port 80 forwarded too during issuing.
- Some internet providers (CGNAT) don't allow incoming connections at all. In that case home hosting won't
  work and a VPS is the answer.
- The PC must stay on whenever people want voice.

## 14. Maintenance

| Task | Command |
|---|---|
| See logs | `docker logs -f bvc` |
| Restart | `docker restart bvc` |
| Update BVC | Stop and remove it (`docker stop bvc && docker rm bvc`), then run step 7 again with the new version number. Your files in `/opt/bvc` stay. |
| Check certificate renewal | `certbot renew --dry-run` |
| Back up | Copy the whole `/opt/bvc` folder somewhere safe |

Keep the Minecraft plugin and the voice server on matching, recent versions. Check the wiki's release notes
when you update either one.

**Privacy:** BVC encrypts voice traffic. Its anonymous usage numbers can be turned off (step 6). Players can
mute or adjust each other in the app.

## 15. Troubleshooting

The wiki says most problems are the voice server being unreachable. Work through these in order.

| Problem | What to check |
|---|---|
| App can't connect | Open `https://voice.yourdomain.com/api/config` from your phone on mobile data (not your home Wi-Fi). If it fails: check DNS, `ufw status`, your VPS provider's cloud firewall, and that **both TCP and UDP 443** are open. Check `docker ps` shows `bvc` running. |
| Certificate error | Must come from a trusted authority and match the domain exactly. Re-run step 5. Make sure the paths in `config.hcl` are right and `docker restart bvc` was run after getting it. |
| `docker logs bvc` shows a certificate or file error | A path in `config.hcl` is wrong, or `/etc/letsencrypt` isn't mounted. Compare with step 7. |
| Connected, but nobody hears anyone | The access token must be **identical** in `/opt/bvc/config.hcl` (`access_token`) and the Paper plugin's `config.yaml` (`access-token`). Check the Minecraft console for `BedrockVoiceChat` errors. Check `bvc-server` has `https://` and the right domain. |
| Player can't log in | Add them (step 9) with their exact gamertag. Check the wiki login page. |
| Bedrock players aren't recognised | Geyser and Floodgate must both be installed and Bedrock players must join through them. |
| Some players hear, some don't | Everyone must be connected to the **voice server** in the app (not the Minecraft address), with the mic allowed and not muted. |
| Voice cuts out or lags | The VPS is probably CPU-bound. Check `top`, and add CPU cores. Pick a VPS region closer to players. |
| Plugin is red or missing in `/plugins` | Wrong Paper version for that plugin build, or a bad `config.yaml`. Read the startup errors. |
| Something changed after updating Minecraft | BVC may need an update for new versions. Check the wiki and Modrinth page. |

For other problems, the BVC wiki links a Discord server for support.

## 16. Final checklist

- [ ] Domain `voice.yourdomain.com` points to the VPS (`nslookup` shows the IP)
- [ ] Ports open: TCP 22, TCP 80, TCP 443, UDP 443 (server firewall **and** cloud firewall)
- [ ] Certificate issued, and the renewal hook `restart-bvc.sh` is in place
- [ ] `/opt/bvc/config.hcl` has the right domain, IP, certificate paths and a 32-character token
- [ ] `docker ps` shows `bvc` running, and `https://voice.yourdomain.com/api/config` loads with no warning
- [ ] Paper plugin installed, `plugins/BedrockVoiceChat/config.yaml` has the **same** token
- [ ] Geyser and Floodgate installed
- [ ] Every player added with `user add`
- [ ] Players have the app, logged in, connected to `voice.yourdomain.com`
- [ ] Two-person test passed (close = hear, far = silence)
- [ ] If you also use DiscordBridge proximity voice, turned it off (`voice.enabled: false`) so there's only
      one voice system
