# ✅ Version 1.0.3 - Complete Version Update

## All Version Numbers Updated to 1.0.3

### Files Updated:

1. **NeoForge 1.21.1**
   - ✅ `neoforge-1.21.1/gradle.properties` → `mod_version=1.0.3`

2. **Forge 1.20.1**
   - ✅ `forge-1.20.1-47.4.0-mdk/gradle.properties` → `mod_version=1.0.3`

3. **Forge 1.21.1**
   - ✅ `forge-1.21.1-52.1.0-mdk/gradle.properties` → `mod_version=1.0.3`

4. **Documentation**
   - ✅ `README.md` → Updated JAR filename example to `viscord-1.0.3.jar`
   - ✅ `CHANGELOG.md` → Contains v1.0.3 entry (v1.0.2 kept for history)

5. **Build Script**
   - ✅ `build-all-versions.bat` → Already using 1.0.3
     - NeoForge: `viscord-1.0.3-neoforge-1.21.1.jar`
     - Forge 1.20.1: `viscord-1.0.3-forge-1.20.1.jar`
     - Forge 1.21.1: `viscord-1.0.3-forge-1.21.1.jar`

## Verification

Searched entire codebase:
- ✅ All `mod_version` properties set to `1.0.3`
- ✅ Build script references `1.0.3`
- ✅ README examples use `1.0.3`
- ✅ Only 1.0.2 reference is in CHANGELOG history (correct)

## Build Output Names

When you run `build-all-versions.bat`, you'll get:
```
Universal-Build/
  ├── viscord-1.0.3-neoforge-1.21.1.jar
  ├── viscord-1.0.3-forge-1.20.1.jar
  └── viscord-1.0.3-forge-1.21.1.jar
```

## Ready for Release

All version numbers are consistent across the entire codebase at **1.0.3**.

Next steps:
1. Build all versions: `.\build-all-versions.bat`
2. Test in-game
3. Commit with v1.0.3 message
4. Tag release: `git tag v1.0.3`
5. Push and publish
