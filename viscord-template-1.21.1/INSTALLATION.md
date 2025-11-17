# Viscord Installation Guide

## ⚠️ Important: External Libraries Required

Due to conflicts with other mods in large modpacks, Viscord requires **external library files** to be installed separately. This prevents package conflicts with mods that use Kotlin, Apache Commons, or other common libraries.

## Quick Installation (5 Steps)

### Step 1: Download Files

You need TWO things:
1. **viscord-1.0.0.jar** - The mod itself (22 KB)
2. **External libraries** - Required dependencies (15 MB total, 14 JAR files)

### Step 2: Locate Your Server's Mods Folder

Navigate to your Minecraft server directory and find the `mods/` folder:
```
your-server/
├── mods/              ← You are here
├── config/
├── world/
└── server.jar
```

### Step 3: Install the Mod

Place `viscord-1.0.0.jar` into the `mods/` folder.

### Step 4: Install External Libraries

Place ALL 14 library JAR files into the `mods/` folder alongside viscord-1.0.0.jar:

**Required Libraries:**
1. `JDA-5.0.0-beta.24.jar` (2.1 MB) - Discord API
2. `okhttp-4.12.0.jar` (772 KB) - HTTP client
3. `okio-jvm-3.9.0.jar` (364 KB) - I/O library
4. `kotlin-stdlib-1.9.24.jar` (1.7 MB) - Kotlin standard library
5. `kotlin-stdlib-jdk7-1.8.21.jar` (1 KB)
6. `kotlin-stdlib-jdk8-1.8.21.jar` (1 KB)
7. `nv-websocket-client-2.14.jar` (123 KB) - WebSocket support
8. `jackson-core-2.17.0.jar` (568 KB) - JSON processing
9. `jackson-databind-2.17.0.jar` (1.6 MB) - JSON data binding
10. `jackson-annotations-2.17.0.jar` (77 KB) - JSON annotations
11. `commons-collections4-4.4.jar` (735 KB) - Apache Commons
12. `byte-buddy-1.14.9.jar` (4.1 MB) - Code generation
13. `core-3.1.0.jar` (2.5 MB) - Core utilities
14. `annotations-13.0.jar` (18 KB) - JetBrains annotations

**Your mods folder should look like this:**
```
mods/
├── viscord-1.0.0.jar                    ← The mod
├── JDA-5.0.0-beta.24.jar                ← Library 1
├── okhttp-4.12.0.jar                    ← Library 2
├── okio-jvm-3.9.0.jar                   ← Library 3
├── kotlin-stdlib-1.9.24.jar             ← Library 4
├── kotlin-stdlib-jdk7-1.8.21.jar        ← Library 5
├── kotlin-stdlib-jdk8-1.8.21.jar        ← Library 6
├── nv-websocket-client-2.14.jar         ← Library 7
├── jackson-core-2.17.0.jar              ← Library 8
├── jackson-databind-2.17.0.jar          ← Library 9
├── jackson-annotations-2.17.0.jar       ← Library 10
├── commons-collections4-4.4.jar         ← Library 11
├── byte-buddy-1.14.9.jar                ← Library 12
├── core-3.1.0.jar                       ← Library 13
├── annotations-13.0.jar                 ← Library 14
└── (other mods...)
```

### Step 5: Configure and Start

1. Start your server once to generate the config file
2. Stop the server
3. Edit `config/viscord-common.toml` (see QUICKSTART.md or SETUP_GUIDE.md)
4. Restart the server

✅ **Installation complete!**

---

## Building from Source

If you're building Viscord yourself, the external libraries are automatically downloaded to `build/libs-external/`:

```bash
./gradlew clean build
```

The libraries will be in:
```
build/libs-external/
├── JDA-5.0.0-beta.24.jar
├── okhttp-4.12.0.jar
├── okio-jvm-3.9.0.jar
├── kotlin-stdlib-1.9.24.jar
├── (and 10 more...)
```

Copy all these files to your server's `mods/` folder along with `build/libs/viscord-1.0.0.jar`.

---

## Why External Libraries?

### The Problem

Large modpacks often contain multiple mods that use:
- Kotlin (very common in modern mods)
- Apache Commons
- Jackson (JSON processing)
- OkHttp

When Viscord bundles these libraries inside its JAR ("fat JAR"), it causes **module conflicts**:
```
java.lang.module.ResolutionException: 
Modules viscord and kotlin.stdlib export package kotlin.random to module snowrealmagic
```

### The Solution

By installing libraries as **separate JAR files**, the Java module system can resolve them properly without conflicts. Each library loads independently and mods can share them without package collision issues.

### Alternatives Tried

1. ❌ **Fat JAR** - Bundled everything in one file → Module conflicts
2. ❌ **JarJar** - NeoForge's jar-in-jar system → Not working on 21.1.214
3. ❌ **Shadow with Relocation** - Rename packages → Breaks with NeoForge's build system
4. ✅ **External Libraries** - Separate JARs → Works perfectly!

---

## Troubleshooting

### "NoClassDefFoundError" when starting

**Problem:** Missing library files

**Solution:**
1. Verify ALL 14 library JARs are in the `mods/` folder
2. Check the file names match exactly (case-sensitive)
3. Ensure files aren't corrupted (re-download if needed)

### "Module Resolution Exception"

**Problem:** Conflicting versions with other mods

**Solution:**
1. Remove any duplicate library JARs (keep only the versions listed above)
2. Check if other mods are bundling conflicting versions
3. Try removing mods one by one to identify conflicts

### Libraries Missing

**Problem:** Don't have the library files

**Solution:**
If you downloaded just the mod JAR, you need to:
1. Build from source: `./gradlew clean build`
2. Or download a distribution package that includes all libraries
3. Libraries will be in `build/libs-external/`

---

## File Size Reference

| File | Size | Purpose |
|------|------|---------|
| `viscord-1.0.0.jar` | 22 KB | Main mod |
| `JDA-5.0.0-beta.24.jar` | 2.1 MB | Discord bot API |
| `byte-buddy-1.14.9.jar` | 4.1 MB | Code generation for JDA |
| `core-3.1.0.jar` | 2.5 MB | Core utilities |
| `jackson-databind-2.17.0.jar` | 1.6 MB | JSON data binding |
| `kotlin-stdlib-1.9.24.jar` | 1.7 MB | Kotlin standard library |
| `okhttp-4.12.0.jar` | 772 KB | HTTP client |
| `commons-collections4-4.4.jar` | 735 KB | Apache Commons |
| `jackson-core-2.17.0.jar` | 568 KB | JSON core |
| `okio-jvm-3.9.0.jar` | 364 KB | Okio I/O library |
| `nv-websocket-client-2.14.jar` | 123 KB | WebSocket client |
| `jackson-annotations-2.17.0.jar` | 77 KB | JSON annotations |
| `annotations-13.0.jar` | 18 KB | JetBrains annotations |
| `kotlin-stdlib-jdk8-1.8.21.jar` | ~1 KB | Kotlin JDK 8 compat |
| `kotlin-stdlib-jdk7-1.8.21.jar` | ~1 KB | Kotlin JDK 7 compat |
| **Total** | **~15 MB** | All dependencies |

---

## Verification Checklist

Before starting your server:

- [ ] `viscord-1.0.0.jar` is in `mods/` folder
- [ ] All 14 library JARs are in `mods/` folder
- [ ] No duplicate versions of libraries
- [ ] Config file created and edited
- [ ] Discord bot token configured
- [ ] Discord channel ID configured
- [ ] Discord webhook URL configured

---

## Next Steps

After installation:
1. **Configure Discord Bot** - See [SETUP_GUIDE.md](SETUP_GUIDE.md) for detailed instructions
2. **Quick Setup** - See [QUICKSTART.md](QUICKSTART.md) for 5-minute setup
3. **Features** - See [README.md](README.md) for feature overview

---

## Support

If you're still having issues:
1. Enable debug logging: `enableDebugLogging = true` in config
2. Check server logs for [Viscord] messages
3. Verify all library files are present
4. Make sure you're using NeoForge 21.1.200+

---

**Happy chatting! 🎮💬**