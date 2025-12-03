# Viscord - Bidirectional Minecraft-Discord Chat Integration

**Multi-platform Minecraft mod for seamless Discord chat integration**

[![NeoForge](https://img.shields.io/badge/NeoForge-1.21.1-orange)]()
[![Forge](https://img.shields.io/badge/Forge-1.20.1%20%7C%201.21.1-blue)]()
[![License](https://img.shields.io/badge/license-MIT-green)]()

## 🎯 Overview

Viscord is a powerful mod that bridges communication between Minecraft servers and Discord channels. Perfect for multi-server communities, it supports multiple Minecraft servers sharing a single Discord channel with advanced loop prevention, custom formatting, and rich event notifications.

## ✨ Key Features

### 🔄 Bidirectional Communication
- Real-time chat relay between Minecraft and Discord
- Webhook-based messaging for fast performance
- Smart message filtering to prevent loops

### 🏷️ Multi-Server Support
- Custom server prefixes (e.g., `[BMC]`, `[Survival]`, `[Creative]`)
- Multiple servers in one Discord channel
- Configurable prefix filtering

### 📢 Rich Event Notifications
- 👋 Player join/leave messages
- 💀 Death messages with formatted embeds
- 🏆 Advancement/achievement announcements
- 📊 Live player count in bot status
- ⚡ Discord slash commands (`/list`)
- 📍 Optional: Route events to separate channel

### ⚙️ Highly Configurable
- Custom message formats
- Toggle individual event types
- Rate limiting configuration
- Debug logging
- Loop prevention options

## 📦 Available Versions

| Platform | Minecraft Version | Platform Version | Java | Status |
|----------|-------------------|------------------|------|--------|
| **NeoForge** | 1.21.1 | 21.1.200+ | 21 | ✅ Ready |
| **Forge** | 1.20.1 | 47.4.0+ | 17 | ✅ Ready |
| **Forge** | 1.21.1 | 52.1.0+ | 21 | ✅ Ready |

### Dependencies
**Required**: [Kotlin for Forge](https://www.curseforge.com/minecraft/mc-mods/kotlin-for-forge) (version 4.0+ recommended)
- Required for OkHttp 4.x compatibility
- Prevents version conflicts in modpacks
- Auto-detected from environment if not bundled

## 🚀 Quick Start

### 1. Download
Choose the appropriate version for your Minecraft installation:
- `viscord-template-1.21.1/` - NeoForge 1.21.1
- `forge-1.20.1-47.4.0-mdk/` - Forge 1.20.1
- `forge-1.21.1-52.1.0-mdk/` - Forge 1.21.1

### 2. Build
```bash
cd <version-directory>
gradlew build
```

The compiled JAR will be in `build/libs/viscord-1.0.3.jar`

### 3. Install
Copy the JAR to your server's `mods/` folder.

**Important**: Also install [Kotlin for Forge](https://www.curseforge.com/minecraft/mc-mods/kotlin-for-forge) (version 4.0+) as a dependency.

### 4. Configure
Start the server once to generate the config file, then edit `config/viscord-common.toml`:

```toml
# Discord Bot Settings
discordBotToken = "YOUR_BOT_TOKEN_HERE"
discordChannelId = "YOUR_CHANNEL_ID_HERE"
discordWebhookUrl = "https://discord.com/api/webhooks/YOUR_WEBHOOK_URL"

# Server Identity
serverPrefix = "[BMC]"
serverName = "Minecraft Server"

# Message Formats
minecraftToDiscordFormat = "{message}"
discordToMinecraftFormat = "§b[Discord] §f<{username}> {message}"

# Event Toggles
sendJoinMessages = true
sendLeaveMessages = true
sendDeathMessages = true
sendAdvancementMessages = true

# Event Channel (Optional - leave empty to use default channel)
eventChannelId = ""           # Separate channel for events
eventWebhookUrl = ""          # Separate webhook for events

# Loop Prevention
ignoreBots = true
ignoreWebhooks = false  # Set to true for single-server setups
ignoreOwnMessages = true
```

### 5. Discord Setup

1. **Create a Discord Bot**:
   - Go to [Discord Developer Portal](https://discord.com/developers/applications)
   - Create a new application
   - Go to "Bot" section and create a bot
   - Enable "Message Content Intent" under "Privileged Gateway Intents"
   - Copy the bot token

2. **Create a Webhook**:
   - In your Discord channel, go to Settings → Integrations → Webhooks
   - Create a new webhook
   - Copy the webhook URL

3. **Invite the Bot**:
   - Go to OAuth2 → URL Generator
   - Select scopes: `bot`, `applications.commands`
   - Select permissions: `Send Messages`, `Read Message History`, `Use Slash Commands`
   - Use the generated URL to invite the bot

4. **Get Channel ID**:
   - Enable Developer Mode in Discord (User Settings → Advanced)
   - Right-click your channel → Copy ID

### 6. Restart
Restart your Minecraft server. Check logs for successful Discord connection.

## 🔧 Configuration Guide

### Message Formats

**Minecraft → Discord**:
```toml
minecraftToDiscordFormat = "{message}"
webhookUsernameFormat = "{prefix}{username}"
webhookAvatarUrl = "https://crafatar.com/avatars/{uuid}?overlay"
```
- `{message}` - The chat message
- `{prefix}` - Server prefix
- `{username}` - Player name
- `{uuid}` - Player UUID

**Discord → Minecraft**:
```toml
discordToMinecraftFormat = "§b[Discord] §f<{username}> {message}"
```
- `{username}` - Discord username
- `{message}` - Discord message
- Use Minecraft formatting codes (§)

### Loop Prevention

**Single Server Setup**:
```toml
ignoreWebhooks = true    # Ignore ALL webhooks
filterByPrefix = true    # Only if you want prefix filtering
```

**Multi-Server Setup**:
```toml
ignoreWebhooks = false   # Allow other server messages
filterByPrefix = false   # See messages from all servers
showServerPrefixInGame = true  # Show which server sent the message
```

The mod **always** filters its own webhook to prevent loops.

### Advanced Settings

```toml
enableDebugLogging = false      # Enable detailed logging
messageQueueSize = 100          # Max queued messages
rateLimitDelay = 1000           # Delay between messages (ms)
setBotStatus = true             # Update bot status with player count
botStatusFormat = "{online}/{max} players online"
```

## Performance & Memory Notes

Viscord is designed to be lightweight, but high-traffic or multi-server setups can still push Discord and JVM memory. The following settings and behaviors are important for performance:

### Discord Intents

Viscord only requests the minimal Discord intents needed:

- `GUILD_MESSAGES`
- `MESSAGE_CONTENT`

This avoids the heavy `setAllIntents()` mode and significantly reduces:

- Number of events delivered to the bot
- Message cache size
- CPU and memory usage

### Message Queue

Outbound webhook messages are buffered through a bounded queue:

- `messageQueueSize` (config: `messageQueueSize`, default `100`)

This:

- Limits how many messages can be queued if Discord is slow or offline
- Prevents unbounded memory growth from pending messages
- Drops new messages (with a warning) if the queue is full

Tuning tips:

- Low RAM / small servers: lower to `50`
- Busy servers: keep at `100` or slightly higher if needed

### Rate Limiting

To avoid Discord rate limits and smooth bursty traffic:

- `rateLimitDelay` (config: `rateLimitDelay`, default `1000` ms)

Higher values:

- Smooth outbound traffic
- Reduce chance of HTTP 429 and backpressure from Discord

### Javacord Shutdown & Stuck Threads

On server stop or crash, Viscord performs a controlled shutdown:

1. Stops the internal message queue thread with a short timeout
2. Calls `discordApi.disconnect()` and waits (with a timeout)
3. Cleanly shuts down the HTTP client thread pool and connections

This prevents:

- Stuck `Javacord` threads reported by tools like AllTheLeaks
- Servers failing to fully stop / restart due to lingering Discord threads

If shutdown takes too long or fails, detailed warnings are logged so you can diagnose it.

### Debug Logging

`enableDebugLogging` (config: `enableDebugLogging`) should be:

- `false` in production for lower overhead and cleaner logs
- `true` only when actively troubleshooting (logs all Discord messages, filters, and webhook details)

### Multi-Server Event & Chat Routing

Multi-server setups add more Discord traffic. For best performance:

- Use `ignoreOtherWebhooks` and `filterByPrefix` to control how much cross-server chatter you ingest
- Use `showOtherServerEvents` to decide whether join/leave/death/advancement from other servers appear in your Minecraft chat
- Use `eventChannelId` / `eventWebhookUrl` to route events to a separate, dedicated channel to keep chat quieter

Properly tuning these helps keep both Minecraft and Discord usage efficient on large networks.

## 🏗️ Architecture

### Technology Stack
- **Javacord 3.8.0** - Discord bot API
- **OkHttp 4.12.0** - Webhook messaging (fast & efficient)
- **Kotlin Stdlib** - Required by OkHttp (provided by Kotlin for Forge mod)
- **Gson** - JSON processing (provided by Minecraft)
- **SLF4J** - Logging (provided by Minecraft)

### Message Flow

```
Minecraft → Discord:
Player Chat → MinecraftEventHandler → DiscordManager → Webhook → Discord

Discord → Minecraft:
Discord Message → Bot Listener → DiscordManager → MinecraftServer → Players
```

### Thread Safety
- Server operations execute on server thread (`server.execute()`)
- Discord operations use separate queue thread
- Proper resource cleanup on shutdown

## 📊 Features Comparison

| Feature | NeoForge 1.21.1 | Forge 1.20.1 | Forge 1.21.1 |
|---------|----------------|--------------|--------------|
| Bidirectional Chat | ✅ | ✅ | ✅ |
| Webhooks | ✅ | ✅ | ✅ |
| Bot Status | ✅ | ✅ | ✅ |
| Slash Commands | ✅ | ✅ | ✅ |
| Event Embeds | ✅ | ✅ | ✅ |
| Loop Prevention | ✅ | ✅ | ✅ |
| Multi-Server | ✅ | ✅ | ✅ |
| Advancement Types | Full | Generic | Full |

## 🐛 Troubleshooting

### Bot Not Connecting
1. Verify bot token is correct
2. Check bot has "Message Content Intent" enabled
3. Verify bot is invited to server
4. Check channel ID is correct

### Messages Not Sending
1. Verify webhook URL is correct
2. Check webhook permissions
3. Enable debug logging
4. Check rate limiting settings

### Message Loops
1. Verify `ignoreOwnMessages = true`
2. Check webhook ID is extracted correctly
3. For single-server: set `ignoreWebhooks = true`
4. Check logs for filtering details

### Enable Debug Mode
```toml
enableDebugLogging = true
```
Restart server and check logs for detailed information.

## 📝 Development

### Building from Source
```bash
# NeoForge 1.21.1
cd viscord-template-1.21.1
gradlew build

# Forge 1.20.1
cd forge-1.20.1-47.4.0-mdk
gradlew build

# Forge 1.21.1
cd forge-1.21.1-52.1.0-mdk
gradlew build
```

### Code Structure
```
src/main/java/network/vonix/viscord/
├── Viscord.java              # Main mod class
├── Config.java               # Configuration definitions
├── DiscordManager.java       # Discord integration
├── MinecraftEventHandler.java # Minecraft event handling
└── ViscordClient.java        # Client-side handler (NeoForge only)
```

### Key Improvements in This Version
✅ Removed unnecessary KotlinForForge dependency  
✅ Fixed thread management (no manual Thread creation)  
✅ Proper HTTP client resource cleanup  
✅ Non-blocking server shutdown  
✅ Updated dependencies (SLF4J 2.0.9)  
✅ Cross-platform compatibility  

## 🤝 Contributing

Contributions welcome! Please ensure:
- Code compiles on all three platforms
- Follow existing code style
- Test thoroughly
- Update documentation

## 📄 License

MIT License - See LICENSE file for details.

## 🙏 Acknowledgments

- Javacord team for excellent Discord API
- OkHttp team for reliable HTTP client
- Forge & NeoForge teams for mod platforms
- Minecraft modding community

## 📞 Support

For issues or questions:
1. Enable debug logging (`enableDebugLogging = true`)
2. Check server logs for detailed errors
3. Review configuration settings
4. Create an issue on GitHub with logs and configuration

## 🗂️ Repository Structure

```
viscord-template-1/
├── viscord-template-1.21.1/     # NeoForge 1.21.1 (Primary version)
├── forge-1.20.1-47.4.0-mdk/     # Forge 1.20.1
├── forge-1.21.1-52.1.0-mdk/     # Forge 1.21.1
├── Universal-Build/              # Cross-version build scripts
├── build-all-versions.bat        # Windows build script
├── build-all-versions.sh         # Linux/Mac build script
└── README.md                     # This file
```

---

**Built with ❤️ for the Minecraft community**

**Status**: All versions tested and ready for production! 🚀
