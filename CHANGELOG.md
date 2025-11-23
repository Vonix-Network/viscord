# Viscord Changelog

## Version 1.0.2

### Bug Fixes
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
  
### Dependencies
- **For modpacks**: Install `Kotlin for Forge` mod (version 4.0+ for optimal compatibility)
- **For standalone use**: Kotlin stdlib will be auto-provided if using compatible Java environment

---

## Version 1.0.1
- Initial bug fixes and improvements

## Version 1.0.0
- Initial release
