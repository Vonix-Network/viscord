# Viscord - Universal Build Package

This folder contains pre-built Viscord JARs for all supported Minecraft versions.

---

## 📦 Available Versions

### 1. **viscord-1.0.0-neoforge-1.21.1.jar** (~5.6 MB)
- **Platform**: NeoForge 21.1.200+
- **Minecraft**: 1.21.1
- **Java**: 21
- **Build Method**: JarJar (NeoForge's official bundling system)
- **Status**: ✅ Original, fully tested and optimized

### 2. **viscord-1.0.0-forge-1.20.1.jar** (~9.9 MB)
- **Platform**: Forge 47.4.0+
- **Minecraft**: 1.20.1
- **Java**: 17
- **Build Method**: Shadow plugin with package relocation
- **Status**: ✅ Ported and fully tested

### 3. **viscord-1.0.0-forge-1.21.1.jar** (~9.4 MB)
- **Platform**: Forge 52.1.0+
- **Minecraft**: 1.21.1
- **Java**: 21
- **Build Method**: Direct JAR bundling (Shadow incompatible)
- **Status**: ✅ Ported and fully tested

---

## 🚀 Quick Start

1. **Choose your version** based on your server's Minecraft version
2. **Download the appropriate JAR** from this folder
3. **Place it** in your server's `mods/` folder
4. **Start your server** to generate the config file
5. **Configure** `config/viscord-common.toml` with your Discord credentials:
   ```toml
   [discord]
       bot_token = "YOUR_BOT_TOKEN_HERE"
       channel_id = "YOUR_CHANNEL_ID_HERE"
       webhook_url = "YOUR_WEBHOOK_URL_HERE"
   ```
6. **Restart your server**

---

## ⚙️ Requirements

### All Versions Require:
- A Discord bot token (create at https://discord.com/developers/applications)
- A Discord channel ID
- A webhook URL for the target channel
- Proper bot permissions:
  - Read Messages/View Channels
  - Send Messages
  - Embed Links
  - Use Slash Commands

### Platform-Specific Requirements:

| Version | Minecraft | Platform | Java |
|---------|-----------|----------|------|
| NeoForge 1.21.1 | 1.21.1 | NeoForge 21.1.200+ | Java 21 |
| Forge 1.20.1 | 1.20.1 | Forge 47.4.0+ | Java 17 |
| Forge 1.21.1 | 1.21.1 | Forge 52.1.0+ | Java 21 |

---

## ✨ Features

All versions include identical functionality:

### Core Features
- ✅ **Bidirectional Chat** - Messages flow both ways between Discord and Minecraft
- ✅ **Event Notifications** - Player join/leave, deaths, advancements
- ✅ **Rich Embeds** - Beautiful Discord embeds with colors and footers
- ✅ **Slash Commands** - `/list` command to see online players
- ✅ **Loop Prevention** - Filters own messages to prevent infinite loops
- ✅ **Multi-Server Support** - Server prefixes for managing multiple servers
- ✅ **Bot Status** - Shows current player count as bot activity
- ✅ **Configurable** - Extensive configuration options

### Embed Footers
All embeds include branded footers:
- "Viscord · Join"
- "Viscord · Leave"
- "Viscord · Death"
- "Viscord · Advancement"
- "Viscord · Startup"
- "Viscord · Shutdown"
- "Viscord · Player List"

---

## 📝 Configuration

After first run, edit `config/viscord-common.toml`:

```toml
[discord]
    # Your Discord bot token
    bot_token = "YOUR_BOT_TOKEN_HERE"
    
    # The Discord channel ID to relay messages to/from
    channel_id = "YOUR_CHANNEL_ID_HERE"
    
    # Webhook URL for sending messages to Discord
    webhook_url = "YOUR_WEBHOOK_URL_HERE"
    
    # Optional: Manually specify webhook ID for filtering
    webhook_id = ""

[server]
    # Server name shown in Discord
    server_name = "Minecraft Server"
    
    # Prefix shown before server name (useful for multi-server setups)
    server_prefix = "[Server]"

[messages]
    # Message formats (supports placeholders like {player}, {message}, etc.)
    format_chat = "**{player}**: {message}"
    format_join = "➡️ **{player}** joined the server"
    format_leave = "⬅️ **{player}** left the server"
    format_death = "💀 {message}"
    format_advancement = "🏆 **{player}** has made the advancement **{title}**"

[events]
    # Toggle individual event types
    enable_chat = true
    enable_join = true
    enable_leave = true
    enable_death = true
    enable_advancement = true
    enable_startup = true
    enable_shutdown = true

[loop_prevention]
    # Ignore messages from webhooks (recommended)
    ignore_webhooks = true
    
    # Filter messages by server prefix (for multi-server channels)
    filter_by_prefix = false

[advanced]
    # Enable debug logging
    enable_debug_logging = false
    
    # Message queue size
    message_queue_size = 100
    
    # Rate limit delay (milliseconds)
    rate_limit_delay = 1000
```

---

## 🔧 Technical Details

### NeoForge 1.21.1
- Uses NeoForge's JarJar system for dependency bundling
- Dependencies stored with metadata in JAR manifest
- Smallest file size due to efficient bundling
- All dependencies properly managed by NeoForge

### Forge 1.20.1
- Uses Shadow plugin with full package relocation
- All dependencies relocated to `network.vonix.viscord.libs.*`
- Includes `mergeServiceFiles()` for ServiceLoader compatibility
- Jackson excluded to prevent module conflicts
- Reobfuscated for production use

### Forge 1.21.1
- Uses direct JAR bundling (Shadow incompatible with Forge's source set merging)
- Dependencies bundled without relocation
- Compatible with Forge's official mappings system
- Jackson excluded to prevent module conflicts
- No reobfuscation needed (official mappings at runtime)

### Bundled Dependencies (All Versions)
- **Javacord 3.8.0** - Discord API library
- **OkHttp 4.12.0** - HTTP client for webhooks
- **Okio 3.9.0** - OkHttp dependency
- **Kotlin stdlib** - Javacord dependency

### Platform-Provided Dependencies
- **Gson 2.10.1** - JSON parsing (provided by Minecraft)
- **SLF4J 2.0.9** - Logging (provided by Minecraft)
- **Jackson** - Excluded (provided by platform or other mods)

---

## 🐛 Troubleshooting

### Mod doesn't load
- ✅ Check you're using the correct version for your Minecraft/platform
- ✅ Verify Java version matches requirements
- ✅ Check for conflicts with other mods

### Discord connection fails
- ✅ Verify bot token is correct
- ✅ Check channel ID is correct
- ✅ Ensure bot has proper permissions
- ✅ Check webhook URL is valid

### Events don't fire
- ✅ Check event toggles in config
- ✅ Verify bot is connected (check server logs)
- ✅ Ensure config file syntax is correct

### Module conflicts (Jackson errors)
- ✅ This should be resolved in all versions (Jackson excluded)
- ✅ If issues persist, check for other mods bundling Jackson

### Messages appear twice
- ✅ Ensure `ignore_webhooks = true` in config
- ✅ Check webhook ID is correctly filtered

---

## 📜 Version History

### v1.0.0 - Initial Release
- ✅ NeoForge 1.21.1 support (original)
- ✅ Forge 1.20.1 support (ported)
- ✅ Forge 1.21.1 support (ported)
- ✅ All core features implemented
- ✅ Full feature parity across all versions
- ✅ Production-ready builds

---

## 🔗 Links

- **Source Code**: Check parent directories for source
- **Documentation**: See main README.md in project root
- **Build Info**: See FORGE_BUILD_SUMMARY.md for technical details

---

## 📄 License

All versions maintain the same license as specified in the project root.

---

## 🙏 Credits

**Original Version**: NeoForge 1.21.1  
**Ported Versions**: Forge 1.20.1, Forge 1.21.1  
**Build System**: Gradle with platform-specific configurations  

Built with ❤️ for the Minecraft modding community.
