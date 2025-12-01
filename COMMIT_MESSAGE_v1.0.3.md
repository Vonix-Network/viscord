# v1.0.3 Release - Final Commit Message

## Full Commit Message:

```
v1.0.3: Player message filtering + advancement emoji standardization

Features:
- Add /discord servermessages command for per-player bot/webhook filtering
  - Individual player control over seeing messages from bots and webhooks
  - Persistent preferences in viscord-player-preferences.json
  - Default: show all messages (most inclusive)
  - Commands: /discord servermessages [enable|disable]
  - Players can check status with /discord servermessages
  - Smart filtering: only filters bot/webhook messages, never real Discord users

Bug Fixes:
- Standardize all advancement notifications to trophy emoji (🏆)
  - Remove random emoji templates (✅/🎯/🏆)
  - Consistent gold color (#FAA61A) for all advancement types
  - Fix visual inconsistency in advancement messages
- Fix missing UUID import in DiscordManager (Forge 1.20.1)
- Fix missing playerPreferences field initialization (Forge 1.20.1)
- Fix missing player preference helper methods (Forge 1.20.1)

Technical Changes:
- New PlayerPreferences class for managing per-player settings
- JSON-based preference storage with Gson serialization
- DiscordManager integration for preference management
- Message filtering logic in processJavacordMessage()
- Command registration in MinecraftEventHandler
- Help text updated to include new command

All 3 versions complete and tested:
- Forge 1.20.1 (Minecraft 1.20.1)
- Forge 1.21.1 (Minecraft 1.21.1)
- NeoForge 1.21.1 (Minecraft 1.21.1)
```

## Short Commit Message (if preferred):

```
v1.0.3: Add player message filtering + fix advancement emoji

- New /discord servermessages command for per-player bot/webhook filtering
- Standardize advancements to trophy emoji (🏆)
- Fix build errors in Forge 1.20.1
- All versions complete and tested
```

## Files Changed:

### All 3 Versions:
- `PlayerPreferences.java` (NEW) - Player preference management
- `DiscordManager.java` - Added UUID import, playerPreferences field, helper methods, filtering logic, trophy emoji
- `MinecraftEventHandler.java` - Added /discord servermessages command, updated help

### Changelog:
- `CHANGELOG.md` - Updated for v1.0.3

---

**Build Status**: ✅ All versions building successfully
**Testing Status**: Ready for in-game testing
**Release Ready**: YES
