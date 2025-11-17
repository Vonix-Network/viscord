# Viscord - Bidirectional Minecraft-Discord Chat Integration (Forge 1.21.1)

A powerful Forge mod for Minecraft 1.21.1 that provides seamless bidirectional communication between Minecraft servers and Discord channels. Perfect for multi-server communities that want to share a single Discord chat channel!

## ✨ Features

### Core Functionality
- **Bidirectional Chat**: Messages flow seamlessly between Minecraft and Discord in both directions
- **Custom Webhook Formatting**: Use custom prefixes like `[BMC]User` to identify different servers
- **Multi-Server Support**: Multiple Minecraft servers can communicate through one Discord channel without conflicts
- **Advanced Loop Prevention**: Smart message filtering prevents infinite message loops between servers

### Rich Event Notifications
- 👋 Player join/leave messages
- 💀 Death messages
- 🏆 Achievement/advancement notifications
- 💬 Real-time chat relay
- 📊 Bot status shows live player count
- ⚡ Discord slash commands (/list)

### Customization
- Fully configurable message formats
- Custom server prefixes and names
- Player avatar support using Crafatar
- Configurable rate limiting
- Debug logging for troubleshooting

## 📋 Requirements

- Minecraft 1.21.1
- Forge 52.1.0 or higher
- Java 21
- A Discord bot token (from Discord Developer Portal)
- A Discord webhook URL

## 🚀 Quick Start

1. Download `viscord-1.0.0.jar` and place it in your server's `mods/` folder
2. Start the server once to generate the config file
3. Edit `config/viscord-common.toml` with your Discord credentials:
   - Discord bot token
   - Discord channel ID
   - Discord webhook URL
4. Restart the server

## 📖 Full Documentation

For detailed setup instructions, see the main documentation.

## 🔧 Version Information

- **Minecraft Version**: 1.21.1
- **Forge Version**: 52.1.0+
- **Mod Version**: 1.0.0
- **Java Version**: 21

## 📄 License

MIT License - See LICENSE file for details.

## 🐛 Support

Enable debug logging in the config and check server logs for troubleshooting.

---

**Enjoy your cross-platform community chat! 🎮💬**
