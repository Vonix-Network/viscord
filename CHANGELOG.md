# Viscord Changelog

## Version 2.0.0 - 2026-03-19

### 🎯 Major Refactor - Complete Rewrite

Viscord 2.0 represents a complete architectural rewrite from the ground up, porting all Discord functionality from VonixCore into a standalone, optimized, multi-loader mod.

### ✨ New Architecture

#### **Multi-Loader Support (Architectury)**
- **Fabric** support added (1.18.2, 1.19.2, 1.20.1, 1.21.1)
- **Forge** support maintained (1.18.2, 1.19.2, 1.20.1)
- **NeoForge** support maintained (1.21.1)
- Unified codebase using Architectury API for cross-platform compatibility

#### **Optimized Core Systems**
- **Non-blocking async operations** throughout - no main thread blocking
- **CachedThreadPool executor** for Discord operations
- **Timeout protection** on Discord initialization (10 seconds max)
- **Proper lifecycle management** with graceful shutdown

### 🔧 New Configuration System

#### **JSON-Based Config** (`config/viscord.json`)
Replaced Forge config with simple JSON configuration for easier public use:
```json
{
  "enabled": true,
  "bot_token": "YOUR_BOT_TOKEN_HERE",
  "channel_id": "YOUR_CHANNEL_ID_HERE",
  "webhook_url": "https://discord.com/api/webhooks/...",
  "server_prefix": "[MC]",
  "server_name": "Minecraft Server",
  "avatar_url": "https://minotar.net/armor/bust/{uuid}/100.png",
  "offline_mode_avatar_fix": true,
  "offline_avatar_url": "https://minotar.net/armor/bust/{username}/100.png"
}
```

#### **New Config Options**
- `offline_mode_avatar_fix` - Detects offline/cracked servers and uses username-based avatars
- `offline_avatar_url` - Configurable username-based avatar service URL
- `message_queue_size` - Maximum queued messages (default: 100)
- `rate_limit_delay` - Webhook rate limiting (default: 1000ms)

### 🎮 New Features

#### **Offline Mode Avatar Support**
- **Automatic detection** of offline/cracked servers via UUID version checking
- **Fallback to username-based avatars** (Minotar) when offline mode detected
- **Type 3 UUID detection** (name-based MD5 hashes vs Type 4 random Mojang UUIDs)
- Fixes profile pictures not working on cracked/offline servers

#### **Fluxer Integration**
- **Full Fluxer support** for cross-server communication
- `FluxerIntegration` class with hooks for:
  - `onCrossServerMessage()` - Forward cross-server chat to Discord
  - `onCrossServerJoin()` - Notify Discord of cross-server joins
  - `onCrossServerLeave()` - Notify Discord of cross-server leaves
  - `getServerId()` - Server identification for multi-server setups

### 📦 Dependencies

Updated all dependencies to latest stable versions:
- **Javacord** 3.8.0 (Discord Bot API)
- **OkHttp** 4.12.0 (HTTP client for webhooks)
- **Gson** 2.10.1 (JSON serialization)

### 🔒 Security & Stability

- **Graceful error handling** throughout Discord operations
- **Connection timeouts** prevent server startup hangs
- **Proper resource cleanup** on shutdown (executor, HTTP client, bot connection)
- **No Kotlin dependency** - pure Java implementation for broader compatibility

### 🐛 Bug Fixes from Original

- **Embed format 1:1 match** with original VonixCore implementation
- **Footer text consistency** - "VonixCore · Advancement" preserved
- **Advancement embed structure** matches original exactly

### 📝 Version Compatibility
- ✅ **Fabric 1.21.1**: Full feature set
- ✅ **Fabric 1.20.1**: Full feature set
- ✅ **Fabric 1.19.2**: Full feature set
- ✅ **Fabric 1.18.2**: Full feature set
- ✅ **NeoForge 1.21.1**: Full feature set
- ✅ **Forge 1.20.1**: Full feature set
- ✅ **Forge 1.19.2**: Full feature set
- ✅ **Forge 1.18.2**: Full feature set

### 🔄 Migration Notes

**From Viscord 1.x:**
- Config location changed: `config/viscord.json` (was Forge config)
- Commands reorganized under `/viscord` namespace
- No data migration needed - fresh install recommended

---

## Version 1.0.3 - 2025-12-03

### ✨ New Features

#### 🎛️ Per-Player Message Filtering System
- **Reorganized `/viscord messages` command** - Individual players can now control whether they see messages from bots and webhooks (other servers)
  - `/viscord messages` - Check your current filter status
  - `/viscord messages enable` - Show ALL messages including bots/other servers (default)
  - `/viscord messages disable` - Only show messages from Discord users and your own server
- **New `/viscord events` command** - Control event messages (achievements, join/leave)
  - `/viscord events` - Check your current event filter status
  - `/viscord events enable` - Show achievements and join/leave messages (default)
  - `/viscord events disable` - Hide achievements and join/leave messages
- **Pure player preference system** - No global config option, fully player-controlled
- **Persistent storage** - Preferences saved to `config/viscord-player-preferences.json`
- **Default behavior** - All players see everything by default (most inclusive)
- **Smart filtering** - Only filters bot/webhook messages and events, never filters real Discord users
- **Command restructuring** - Moved filtering commands from `/discord` to `/viscord` for better organization
- **Status**: ✅ Implemented in NeoForge 1.21.1, Forge 1.21.1, and Forge 1.20.1

### 🐛 Bug Fixes
- **Fixed duplicate advancement messages** - Advancements were sometimes sending twice with different emoji templates
  - Removed duplicate `sendAdvancementEmbed()` call in event handler
  - Standardized on trophy emoji (🏆) for all advancement types (Challenge/Goal/Task)
  - Single consistent embed format for all advancement notifications
  - **Affects**: All versions (Forge 1.20.1, Forge 1.21.1, NeoForge 1.21.1)

### ⚙️ Configuration Changes
- **Changed `ignoreBots` default from `true` to `false`**
  - Loop prevention is now handled by webhook ID filtering in code
  - No need to blanket ignore all bot messages
  - Users can still manually enable if needed for specific use cases
  - **Affects**: All versions (Forge 1.20.1, Forge 1.21.1, NeoForge 1.21.1)

### 📝 Version Compatibility
- ✅ **Forge 1.20.1**: Full feature set implemented
- ✅ **Forge 1.21.1**: Full feature set implemented
- ✅ **NeoForge 1.21.1**: Full feature set implemented

---

## Version 1.0.2 - 2025-11-22

### ✨ New Features

#### 🔗 Account Linking System
- **Link Minecraft and Discord accounts** with 6-digit verification codes
  - New `LinkedAccountsManager` with persistent JSON storage (`viscord-linked-accounts.json`)
  - Configurable code expiry time (default: 5 minutes)
  - Prevents duplicate links (one Minecraft account per Discord user)
  - Persistent storage across server restarts
  - **Status**: ✅ Fully implemented in NeoForge 1.21.1, utilities ready for Forge ports

#### 🎮 New Minecraft Commands (NeoForge 1.21.1)
- `/discord link` - Generate a 6-digit link code with instructions
- `/discord unlink` - Unlink your Discord account
- `/viscord help` - Show all available Viscord commands
- `/viscord reload` - Reload config without restart (ops only, permission level 2+)

#### 💬 New Discord Slash Commands (NeoForge 1.21.1)
- `/link <code>` - Link your Minecraft account using the 6-digit code from in-game
- `/unlink` - Unlink your Minecraft account from Discord
- `/list` - Show online players (refactored, unchanged behavior)

#### 🔄 Automatic Update Checker
- **GitHub Releases integration** with semantic version comparison
- Checks for updates on server startup (async, non-blocking)
- Logs available updates with download URLs
- Optional in-game notifications for server operators (planned)
- Config options: `updateChecker.enabled`, `updateChecker.notifyOpsInGame`
- **Status**: ✅ Implemented in all versions

#### ⚙️ New Configuration Options
**Account Linking Section:**
- `accountLinking.enabled` - Enable/disable account linking (default: true)
- `accountLinking.showLinkedNames` - Show linked Discord names in event embeds (default: true)
- `accountLinking.linkCodeExpiry` - Code lifetime in seconds (default: 300, range: 60-600)

**Update Checker Section:**
- `updateChecker.enabled` - Enable automatic update checking (default: true)
- `updateChecker.notifyOpsInGame` - Show update notifications to ops on join (default: true)

### 🏗️ Code Improvements & Optimizations

#### New Utility Classes
- **`EmbedFactory`** - Centralized Discord embed creation
  - Reduces 130+ lines of duplicated JSON building code
  - Methods: `createSimpleEmbed()`, `createPlayerEventEmbed()`, `createAdvancementEmbed()`, `createServerStatusEmbed()`
  - Used by startup/shutdown/join/leave/advancement embeds

- **`ConfigValidator`** - Standardized config validation
  - Reduces 15+ lines of validation boilerplate
  - Methods: `isConfigured()`, `requireConfigured()`, `warnIfNotConfigured()`
  - Used for token, webhook URL, and channel ID validation

#### Refactored Code (NeoForge 1.21.1)
- **DiscordManager**
  - Consolidated webhook ID extraction into single reusable method
  - Integrated ConfigValidator for cleaner validation
  - Integrated EmbedFactory for all webhook embeds
  - Added account linking initialization and command registration
  - **Reduction**: ~175 lines saved

- **MinecraftEventHandler**
  - Added `shouldProcessEvent()` guard method to eliminate repeated checks
  - Refactored all event handlers (join/leave/death/advancement) to use guard
  - Cleaner, more maintainable event handling
  - **Reduction**: ~84 lines saved

**Total Code Reduction**: ~259 lines (22% reduction) while adding new features

### 🐛 Bug Fixes
- Fixed !list command showing literal `\n` instead of newlines between player names
  - Changed from escaped `"\\n"` to proper newline `"\n"` in player list formatting
  - Replaced `.stream().collect()` with `StringBuilder` for better performance
  - Added bullet points (•) for more reliable Discord formatting
  - Consolidated `/list` and `!list` logic to use shared code and direct Javacord API
  - Affects all versions: NeoForge 1.21.1, Forge 1.21.1, and Forge 1.20.1
  - Players now consistently appear on separate lines in Discord

- Fixed bot status not updating correctly on player join
  - Player join was updating status immediately before player was added to list
  - Now uses scheduled update (100ms delay) for both join and leave events
  - Ensures player count is always accurate in bot status
  - Affects all versions: NeoForge 1.21.1, Forge 1.21.1, and Forge 1.20.1

- Fixed crash on server start: `NoClassDefFoundError: kotlin/jvm/internal/Intrinsics`
  - Added Kotlin standard library as compileOnly dependency (not bundled)
  - Requires `kotlinforforge` mod to be installed in modpacks (for compatibility)
  - Prevents version conflicts when using modpacks that already include kotlinforforge
  - Affects all versions: NeoForge 1.21.1, Forge 1.21.1, and Forge 1.20.1

- Fixed build error with `server.getServerDirectory().toPath()` - removed redundant `.toPath()` call
  
### 📦 Dependencies
- **Required**: [Kotlin for Forge](https://www.curseforge.com/minecraft/mc-mods/kotlin-for-forge) (version 4.0+ recommended)
- **For modpacks**: Install alongside Viscord for optimal compatibility
- **For standalone**: Kotlin stdlib auto-provided if using compatible Java environment

### 📝 Version Compatibility
- ✅ **NeoForge 1.21.1**: Full feature set implemented (account linking, commands, update checker, optimizations)
- ⚠️ **Forge 1.20.1**: Utility classes and config ready, command integration pending
- ⚠️ **Forge 1.21.1**: Utility classes and config ready, command integration pending

### 🔗 Links
- [GitHub Repository](https://github.com/Vonix-Network/Viscord)
- [Documentation](https://github.com/Vonix-Network/Viscord#readme)
- [Issue Tracker](https://github.com/Vonix-Network/Viscord/issues)

---

## Version 1.0.1
- Initial bug fixes and improvements

## Version 1.0.0
- Initial release
