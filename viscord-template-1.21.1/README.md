# Viscord - Bidirectional Minecraft-Discord Chat Integration

A powerful NeoForge mod for Minecraft 1.21.1 that provides seamless bidirectional communication between Minecraft servers and Discord channels. Perfect for multi-server communities that want to share a single Discord chat channel!

## ⚠️ IMPORTANT: External Libraries Required

**Viscord requires 14 external library JAR files to be installed separately in your mods folder.** This prevents conflicts with other mods in large modpacks. See [INSTALLATION.md](INSTALLATION.md) for detailed instructions.

**Quick Install:**
1. Download `viscord-1.0.0.jar` (22 KB)
2. Download all 14 library JARs from `build/libs-external/` (~15 MB total)
3. Place ALL files in your server's `mods/` folder
4. Configure and start!

## ✨ Features

### Core Functionality
- **Bidirectional Chat**: Messages flow seamlessly between Minecraft and Discord in both directions
- **Custom Webhook Formatting**: Use custom prefixes like `[BMC]User` to identify different servers
- **Multi-Server Support**: Multiple Minecraft servers can communicate through one Discord channel without conflicts
- **Advanced Loop Prevention**: Smart message filtering prevents infinite message loops between servers

### Loop Prevention System
- ✅ Ignores bot messages (configurable)
- ✅ Ignores webhook messages (configurable)
- ✅ Ignores own messages
- ✅ Smart prefix filtering - only ignore webhooks with your server's prefix
- ✅ Allows messages from other servers' webhooks (multi-server support)

### Rich Event Notifications
- 👋 Player join/leave messages
- 💀 Death messages
- 🏆 Achievement/advancement notifications
- 💬 Real-time chat relay
- 📊 Bot status shows live player count

### Customization
- Fully configurable message formats
- Custom server prefixes and names
- Player avatar support using Crafatar
- Configurable rate limiting
- Debug logging for troubleshooting

## 📋 Requirements

- Minecraft 1.21.1
- NeoForge 21.1.200 or higher
- Java 21
- A Discord bot token (from Discord Developer Portal)
- A Discord webhook URL
- **14 external library JARs** (provided with mod, must be installed separately)

## 🚀 Installation

### ⚠️ Important: Read [INSTALLATION.md](INSTALLATION.md) First!

Viscord requires external libraries to avoid conflicts with other mods. **You must install 15 total JAR files** (1 mod + 14 libraries).

### Step 1: Install the Mod and Libraries

1. Download `viscord-1.0.0.jar` (the mod)
2. Download all 14 library JARs from `build/libs-external/`:
   - `JDA-5.0.0-beta.24.jar`
   - `okhttp-4.12.0.jar`
   - `okio-jvm-3.9.0.jar`
   - `kotlin-stdlib-1.9.24.jar`
   - `nv-websocket-client-2.14.jar`
   - `jackson-core-2.17.0.jar`
   - `jackson-databind-2.17.0.jar`
   - `jackson-annotations-2.17.0.jar`
   - `commons-collections4-4.4.jar`
   - `byte-buddy-1.14.9.jar`
   - `core-3.1.0.jar`
   - `annotations-13.0.jar`
   - `kotlin-stdlib-jdk7-1.8.21.jar`
   - `kotlin-stdlib-jdk8-1.8.21.jar`
3. Place **ALL 15 files** in your server's `mods` folder
4. Start your server once to generate the configuration file
5. Stop the server

### Step 2: Create a Discord Bot

1. Go to the [Discord Developer Portal](https://discord.com/developers/applications)
2. Click "New Application" and give it a name
3. Go to the "Bot" section
4. Click "Add Bot"
5. Under "Privileged Gateway Intents", enable:
   - ✅ Server Members Intent
   - ✅ Message Content Intent
6. Click "Reset Token" and copy your bot token (keep this secret!)
7. Go to OAuth2 → URL Generator
8. Select scopes:
   - ✅ `bot`
9. Select bot permissions:
   - ✅ Read Messages/View Channels
   - ✅ Send Messages
   - ✅ Read Message History
10. Copy the generated URL and open it in your browser to invite the bot to your server

### Step 3: Create a Discord Webhook

1. In Discord, go to your channel settings (right-click the channel → Edit Channel)
2. Go to "Integrations" → "Webhooks"
3. Click "New Webhook"
4. Give it a name and avatar (optional)
5. Click "Copy Webhook URL"

### Step 4: Configure the Mod

Edit `config/viscord-common.toml` in your server directory:

```toml
# Discord Bot Settings
discordBotToken = "YOUR_BOT_TOKEN_HERE"
discordChannelId = "YOUR_CHANNEL_ID_HERE"
discordWebhookUrl = "https://discord.com/api/webhooks/YOUR_WEBHOOK_URL"

# Server Identity
serverPrefix = "[BMC]"
serverName = "Minecraft Server"

# Message Formats
minecraftToDiscordFormat = "**{prefix}{username}**: {message}"
discordToMinecraftFormat = "§b[Discord] §f<{username}> {message}"

# Webhook Settings
webhookUsernameFormat = "{prefix}{username}"
webhookAvatarUrl = "https://crafatar.com/avatars/{uuid}?overlay"

# Loop Prevention (IMPORTANT!)
ignoreBots = true
ignoreWebhooks = true
ignoreOwnMessages = true
filterByPrefix = true  # Essential for multi-server setups!

# Features
sendJoinMessages = true
sendLeaveMessages = true
sendDeathMessages = true
sendAdvancementMessages = true

# Bot Status
setBotStatus = true
botStatusFormat = "{online}/{max} players online"

# Advanced
enableDebugLogging = false
messageQueueSize = 100
rateLimitDelay = 1000
```

### Step 5: Get Your Discord Channel ID

1. Enable Developer Mode in Discord (Settings → Advanced → Developer Mode)
2. Right-click your channel and click "Copy Channel ID"
3. Paste this into `discordChannelId` in the config

### Step 6: Start Your Server

1. Start your Minecraft server
2. Check the logs for successful connection:
   ```
   [Viscord] Discord integration started successfully!
   [Viscord] Server: Minecraft Server
   [Viscord] Prefix: [BMC]
   ```

## 🔧 Multi-Server Setup

To have multiple Minecraft servers communicate through one Discord channel:

### Server 1 Configuration
```toml
serverPrefix = "[Survival]"
serverName = "Survival Server"
filterByPrefix = true  # Only ignore webhooks with [Survival] prefix
showServerPrefixInGame = true  # Show other servers' prefixes in-game
```

### Server 2 Configuration
```toml
serverPrefix = "[Creative]"
serverName = "Creative Server"
filterByPrefix = true  # Only ignore webhooks with [Creative] prefix
showServerPrefixInGame = true  # Show other servers' prefixes in-game
```

### Server 3 Configuration
```toml
serverPrefix = "[BMC]"
serverName = "Main Server"
filterByPrefix = true  # Only ignore webhooks with [BMC] prefix
showServerPrefixInGame = true  # Show other servers' prefixes in-game
```

**How it works:**
- Each server uses the same bot and webhook URL
- Each server has a unique prefix
- `filterByPrefix = true` ensures each server only ignores its own webhook messages
- Messages from other servers' webhooks are displayed in-game
- No message loops occur because each server ignores only its own prefix

## 🎨 Message Format Placeholders

### Minecraft to Discord Format
- `{prefix}` - Server prefix (e.g., [BMC])
- `{username}` - Player username
- `{message}` - Chat message

### Discord to Minecraft Format
- `{username}` - Discord username or webhook name
- `{message}` - Discord message content

### Webhook Username Format
- `{prefix}` - Server prefix
- `{username}` - Player username

### Webhook Avatar URL
- `{uuid}` - Player UUID (without dashes)
- `{username}` - Player username

### Bot Status Format
- `{online}` - Current player count
- `{max}` - Maximum player count

## 🛠️ Troubleshooting

### Bot doesn't connect
- ✅ Check that your bot token is correct
- ✅ Ensure Message Content Intent is enabled
- ✅ Verify the bot is in your Discord server
- ✅ Check server logs for error messages

### Messages not appearing in Discord
- ✅ Verify webhook URL is correct
- ✅ Check that the webhook hasn't been deleted
- ✅ Ensure the bot has permissions to read the channel
- ✅ Enable debug logging to see detailed errors

### Messages from Discord not appearing in Minecraft
- ✅ Verify the channel ID is correct
- ✅ Check that `ignoreBots` isn't blocking legitimate messages
- ✅ Ensure the bot has "Read Messages" permission
- ✅ Enable debug logging to troubleshoot

### Message loops between servers
- ✅ Set `filterByPrefix = true`
- ✅ Ensure each server has a unique prefix
- ✅ Set `ignoreWebhooks = true`
- ✅ Set `ignoreOwnMessages = true`

### Debug Logging
Enable debug logging in config:
```toml
enableDebugLogging = true
```

This will show detailed information about:
- Which messages are being ignored and why
- Webhook messages being sent
- Discord messages being received
- Connection status

## 📝 Configuration Reference

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `discordBotToken` | String | - | Your Discord bot token |
| `discordChannelId` | String | - | Discord channel ID for chat |
| `discordWebhookUrl` | String | - | Webhook URL for sending messages |
| `serverPrefix` | String | `[BMC]` | Prefix for this server's messages |
| `serverName` | String | `Minecraft Server` | Server display name |
| `minecraftToDiscordFormat` | String | `**{prefix}{username}**: {message}` | Format for MC→Discord |
| `discordToMinecraftFormat` | String | `§b[Discord] §f<{username}> {message}` | Format for Discord→MC |
| `webhookUsernameFormat` | String | `{prefix}{username}` | Webhook display name format |
| `webhookAvatarUrl` | String | `https://crafatar.com/avatars/{uuid}?overlay` | Player avatar URL |
| `sendJoinMessages` | Boolean | `true` | Send join messages to Discord |
| `sendLeaveMessages` | Boolean | `true` | Send leave messages to Discord |
| `sendDeathMessages` | Boolean | `true` | Send death messages to Discord |
| `sendAdvancementMessages` | Boolean | `true` | Send advancement messages |
| `ignoreBots` | Boolean | `true` | Ignore Discord bot messages |
| `ignoreWebhooks` | Boolean | `true` | Ignore webhook messages |
| `ignoreOwnMessages` | Boolean | `true` | Ignore own bot messages |
| `filterByPrefix` | Boolean | `true` | Only ignore webhooks with own prefix |
| `showServerPrefixInGame` | Boolean | `true` | Show server prefix for other servers |
| `setBotStatus` | Boolean | `true` | Update bot status with player count |
| `botStatusFormat` | String | `{online}/{max} players online` | Bot status format |
| `enableDebugLogging` | Boolean | `false` | Enable detailed debug logs |
| `messageQueueSize` | Integer | `100` | Max queued messages |
| `rateLimitDelay` | Integer | `1000` | Delay between messages (ms) |

## 🤝 Credits

Inspired by DiscordSRV, rebuilt from scratch for NeoForge 1.21.1 with enhanced multi-server support and modern Discord API integration.

## 📄 License

All Rights Reserved

## 🐛 Support

If you encounter issues:
1. Enable debug logging
2. Check server logs for errors
3. Verify your Discord bot configuration
4. Ensure all required permissions are granted

---

**Enjoy your cross-platform community chat! 🎮💬**