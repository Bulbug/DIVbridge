"""
A tiny always-on Discord bot that can START your Minecraft server.

A Paper plugin can't start a server that isn't running, so this runs
separately, on the same machine as the server, and stays online 24/7.

Commands (slash commands):
  /start   start the server if it's off
  /status  say whether the server is online
"""
import asyncio
import json
import os
import subprocess
from pathlib import Path

import discord
from discord import app_commands

BASE = Path(__file__).parent
CFG = json.loads((BASE / "config.json").read_text(encoding="utf-8"))

TOKEN = CFG["bot_token"]
GUILD = discord.Object(id=int(CFG["guild_id"]))
SERVER_DIR = Path(CFG["server_dir"])
START_COMMAND = CFG["start_command"]  # list, e.g. ["java", "-Xmx4G", "-jar", "paper.jar", "--nogui"]
HOST = CFG.get("server_host", "127.0.0.1")
PORT = int(CFG.get("server_port", 25565))
BOOT_TIMEOUT = int(CFG.get("boot_timeout_seconds", 180))
ALLOWED_ROLES = {int(x) for x in CFG.get("allowed_role_ids", [])}
ALLOWED_USERS = {int(x) for x in CFG.get("allowed_user_ids", [])}

process = None  # the server process, if we started it


class Bot(discord.Client):
    def __init__(self):
        super().__init__(intents=discord.Intents.default())
        self.tree = app_commands.CommandTree(self)

    async def setup_hook(self):
        await self.tree.sync(guild=GUILD)


client = Bot()
tree = client.tree


async def port_open() -> bool:
    try:
        _, writer = await asyncio.wait_for(asyncio.open_connection(HOST, PORT), timeout=2)
        writer.close()
        return True
    except Exception:
        return False


def allowed(interaction: discord.Interaction) -> bool:
    if interaction.user.id in ALLOWED_USERS:
        return True
    roles = getattr(interaction.user, "roles", [])
    return any(r.id in ALLOWED_ROLES for r in roles)


def launch() -> subprocess.Popen:
    kwargs = {"cwd": str(SERVER_DIR)}
    if os.name == "nt":
        kwargs["creationflags"] = subprocess.CREATE_NEW_CONSOLE  # opens a console window
    else:
        kwargs.update(
            stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            start_new_session=True,  # keeps running even if this bot stops
        )
    return subprocess.Popen(START_COMMAND, **kwargs)


@tree.command(name="status", description="Check if the Minecraft server is online", guild=GUILD)
async def status(interaction: discord.Interaction):
    if await port_open():
        await interaction.response.send_message("🟢 The server is online.")
    elif process is not None and process.poll() is None:
        await interaction.response.send_message("🟡 The server is starting up.")
    else:
        await interaction.response.send_message("🔴 The server is offline. Use /start to start it.")


@tree.command(name="start", description="Start the Minecraft server", guild=GUILD)
async def start(interaction: discord.Interaction):
    global process

    if not allowed(interaction):
        await interaction.response.send_message("You're not allowed to do that.", ephemeral=True)
        return
    if await port_open():
        await interaction.response.send_message("🟢 The server is already online.")
        return
    if process is not None and process.poll() is None:
        await interaction.response.send_message("🟡 The server is already starting up, hang tight.")
        return

    await interaction.response.send_message("⏳ Starting the server...")
    try:
        process = launch()
    except Exception as e:
        await interaction.edit_original_response(content=f"❌ Could not start the server: {e}")
        return

    print(f"{interaction.user} started the server.")
    for _ in range(max(1, BOOT_TIMEOUT // 5)):
        await asyncio.sleep(5)
        if process.poll() is not None:
            await interaction.edit_original_response(
                content=f"❌ The server exited right away (code {process.returncode}). Check its logs.")
            return
        if await port_open():
            await interaction.edit_original_response(content="✅ The server is online! You can join now.")
            return
    await interaction.edit_original_response(
        content=f"⚠️ Still not up after {BOOT_TIMEOUT} seconds. Check the server logs.")


client.run(TOKEN)
