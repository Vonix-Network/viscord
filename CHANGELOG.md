# Viscord Changelog

## Version 1.0.2

### Bug Fixes
- Fixed !list command showing literal `\n` instead of newlines between player names
  - Changed from escaped `"\\n"` to proper newline `"\n"` in player list formatting
  - Replaced `.stream().collect()` with `StringBuilder` for better performance
  - Added bullet points (•) for more reliable Discord formatting
  - Affects all versions: NeoForge 1.21.1, Forge 1.21.1, and Forge 1.20.1
  - Players now consistently appear on separate lines in Discord

---

## Version 1.0.1
- Initial bug fixes and improvements

## Version 1.0.0
- Initial release
