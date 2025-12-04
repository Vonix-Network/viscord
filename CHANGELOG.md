# Viscord Changelog

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
