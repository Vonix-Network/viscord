# Viscord Dependencies Documentation

## Overview

Viscord uses NeoForge's **Jar-in-Jar (JarJar)** packaging system to embed required dependencies directly into the mod JAR. This approach ensures compatibility across different server environments while avoiding conflicts with other mods.

## Dependency Categories

### 1. Embedded Dependencies (Included in Mod JAR)

These dependencies are bundled inside the mod JAR file in `META-INF/jarjar/`:

| Dependency | Version | Size | Purpose |
|------------|---------|------|---------|
| **JDA** | 5.0.0-beta.24 | ~2.8 MB | Java Discord API - Core Discord integration |
| **OkHttp** | 4.12.0 | ~800 KB | HTTP client used by JDA |
| **Okio** | 3.9.0 | ~400 KB | I/O library required by OkHttp |
| **nv-websocket-client** | 2.14 | ~100 KB | WebSocket support for Discord real-time events |
| **Trove4j** | 3.0.3 | ~600 KB | High-performance primitive collections used by JDA |

**Total Embedded Size:** ~5.3 MB

#### Why These Are Embedded

1. **Not available in Minecraft/NeoForge** - These libraries are not part of the standard Minecraft environment
2. **Specific versions required** - JDA requires exact versions of its dependencies
3. **Conflict avoidance** - Embedding prevents version conflicts with other mods
4. **Self-contained deployment** - Single JAR installation for users

### 2. Required External Dependencies

These must be installed separately by the user:

| Dependency | Version | Source | Required? |
|------------|---------|--------|-----------|
| **KotlinForForge** | 5.4.0+ | [GitHub](https://github.com/thedarkcolour/KotlinForForge) | ✅ YES |

#### KotlinForForge Details

- **Purpose:** Provides Kotlin standard library and runtime for NeoForge mods
- **Why Required:** JDA and many of its dependencies are written in Kotlin
- **Installation:** Download from CurseForge/Modrinth and place in `mods/` folder
- **Compatibility:** Must match your NeoForge version (use 5.4.0+ for NeoForge 21.1.x)

**⚠️ Server will crash without KotlinForForge installed!**

### 3. Provided by Environment (Excluded from Embedding)

These dependencies are already available in Minecraft/NeoForge:

| Dependency | Provided By | Notes |
|------------|-------------|-------|
| **SLF4J** | NeoForge | Logging framework |
| **Gson** | Minecraft | JSON parsing |
| **Log4j** | Minecraft | Logging implementation |
| **JetBrains Annotations** | Optional | Development-only, not needed at runtime |

#### Why These Are Excluded

1. **Already in classpath** - No need to duplicate
2. **Version management** - Let Minecraft control these versions
3. **Smaller JAR size** - Reduces final mod size
4. **Compatibility** - Ensures we use the same versions as other mods

## Dependency Details

### JDA (Java Discord API)

**Group:** `net.dv8tion`  
**Artifact:** `JDA`  
**Version:** `5.0.0-beta.24`  
**License:** Apache 2.0

JDA is the core library that handles all Discord communication:
- Bot authentication and connection
- WebSocket event handling
- REST API calls (send messages, get channels, etc.)
- Rate limiting and retry logic
- Message parsing and formatting

**Why Beta Version?**
- Latest stable features for Discord's current API
- Better rate limiting
- Improved thread safety
- Active development and bug fixes

**Excluded Transitive Dependencies:**
```gradle
exclude group: 'org.slf4j', module: 'slf4j-api'        // Provided by NeoForge
exclude group: 'club.minnced', module: 'opus-java'     // Audio not needed
exclude group: 'org.jetbrains.kotlin'                  // Provided by KotlinForForge
exclude group: 'org.jetbrains', module: 'annotations'  // Dev-only
```

### Trove4j

**Group:** `net.sf.trove4j`  
**Artifact:** `trove4j`  
**Version:** `3.0.3`  
**License:** LGPL 2.1

**Critical Dependency Added:** November 13, 2024

Trove4j provides high-performance primitive collections that JDA uses internally:
- `TLongObjectMap` - Maps primitive longs to objects (no boxing overhead)
- `TLongSet` - Sets of primitive longs
- `TIntObjectMap` - Maps primitive ints to objects

**Why JDA Needs It:**
- Discord IDs are 64-bit longs (snowflake format)
- JDA maintains large caches of users, channels, guilds
- Primitive collections avoid object boxing, saving memory and improving performance
- Critical for high-traffic servers

**Historical Note:**
This dependency was initially missing, causing the error:
```
java.lang.NoClassDefFoundError: gnu/trove/map/TLongObjectMap
```
The fix was added to `build.gradle` and is now included in all builds.

### OkHttp

**Group:** `com.squareup.okhttp3`  
**Artifact:** `okhttp`  
**Version:** `4.12.0`  
**License:** Apache 2.0

Modern HTTP client used by JDA for REST API calls:
- Connection pooling
- HTTP/2 support
- Automatic retries
- Request/response interceptors

### Okio

**Group:** `com.squareup.okio`  
**Artifact:** `okio-jvm`  
**Version:** `3.9.0`  
**License:** Apache 2.0

I/O library that OkHttp depends on:
- Efficient buffering
- Byte string handling
- Timeout management

### nv-websocket-client

**Group:** `com.neovisionaries`  
**Artifact:** `nv-websocket-client`  
**Version:** `2.14`  
**License:** Apache 2.0

WebSocket implementation used by JDA:
- Real-time Discord gateway connection
- Event streaming (messages, joins, leaves)
- Automatic reconnection
- Compression support

## Build Configuration

### build.gradle Dependencies Section

```gradle
dependencies {
    // KotlinForForge provides Kotlin stdlib - mark as required dependency
    compileOnly 'thedarkcolour:kotlinforforge:5.4.0'

    // Use JDA with excluded slf4j and Kotlin (provided by KotlinForForge)
    implementation('net.dv8tion:JDA:5.0.0-beta.24') {
        exclude group: 'org.slf4j', module: 'slf4j-api'
        exclude group: 'club.minnced', module: 'opus-java'
        exclude group: 'org.jetbrains.kotlin'
        exclude group: 'org.jetbrains', module: 'annotations'
    }

    // Use jarJar to bundle JDA and its dependencies (excluding Kotlin)
    jarJar('net.dv8tion:JDA:5.0.0-beta.24') {
        exclude group: 'org.slf4j', module: 'slf4j-api'
        exclude group: 'club.minnced', module: 'opus-java'
        exclude group: 'org.jetbrains.kotlin'
        exclude group: 'org.jetbrains', module: 'annotations'
    }

    // Bundle OkHttp and Okio
    jarJar 'com.squareup.okhttp3:okhttp:4.12.0'
    jarJar 'com.squareup.okio:okio-jvm:3.9.0'
    jarJar 'com.neovisionaries:nv-websocket-client:2.14'

    // Bundle Trove4j (required by JDA)
    implementation 'net.sf.trove4j:trove4j:3.0.3'
    jarJar 'net.sf.trove4j:trove4j:3.0.3'

    // Gson is provided by Minecraft
    compileOnly 'com.google.code.gson:gson:2.10.1'
}
```

### Why `implementation` + `jarJar`?

Both declarations are needed:
1. **`implementation`** - Makes the library available during compilation and in your IDE
2. **`jarJar`** - Embeds the library into the final mod JAR using NeoForge's Jar-in-Jar system

### Repository Configuration

```gradle
repositories {
    mavenCentral()
    maven {
        name = 'Kotlin for Forge'
        url = 'https://thedarkcolour.github.io/KotlinForForge/'
    }
}
```

## JarJar Metadata

The mod includes `META-INF/jarjar/metadata.json` which tells NeoForge about embedded dependencies:

```json
{
  "jars": [
    {
      "identifier": {
        "group": "net.dv8tion",
        "artifact": "JDA"
      },
      "version": {
        "range": "[5.0.0-beta.24,)",
        "artifactVersion": "5.0.0-beta.24"
      },
      "path": "META-INF/jarjar/JDA-5.0.0-beta.24.jar",
      "isObfuscated": false
    },
    // ... other dependencies
  ]
}
```

This metadata enables:
- NeoForge to load the embedded JARs at runtime
- Version conflict detection
- Proper classloader isolation

## Verification

### Check Embedded Dependencies

```bash
# List all embedded dependencies
jar -tf build/libs/viscord-1.0.0.jar | grep META-INF/jarjar/

# Expected output:
# META-INF/jarjar/JDA-5.0.0-beta.24.jar
# META-INF/jarjar/metadata.json
# META-INF/jarjar/nv-websocket-client-2.14.jar
# META-INF/jarjar/okhttp-4.12.0.jar
# META-INF/jarjar/okio-jvm-3.9.0.jar
# META-INF/jarjar/trove4j-3.0.3.jar
```

### View Dependency Tree

```bash
./gradlew dependencies --configuration runtimeClasspath
```

## Troubleshooting

### ClassNotFoundException or NoClassDefFoundError

**Symptom:** Server crashes with missing class errors

**Common Causes:**
1. **Missing KotlinForForge** - Install KotlinForForge mod
2. **Corrupted JAR** - Re-download or rebuild the mod
3. **Version mismatch** - Ensure KotlinForForge version matches your NeoForge version

**Solution:**
```bash
# Verify JAR contents
jar -tf viscord-1.0.0.jar | grep -E "(JDA|trove|okhttp)"

# Should show embedded JARs in META-INF/jarjar/
```

### Kotlin Version Conflicts

**Symptom:** Errors mentioning Kotlin stdlib or runtime

**Solution:**
- Ensure only ONE version of KotlinForForge is installed
- Remove any mods that embed their own Kotlin stdlib
- Use KotlinForForge 5.4.0+ for NeoForge 21.1.x

### JDA Connection Failures

**Symptom:** Bot fails to connect to Discord

**Check:**
1. Internet connectivity
2. Discord bot token is valid
3. Bot has proper intents enabled in Discord Developer Portal
4. No firewall blocking WebSocket connections

## Updating Dependencies

### When to Update

- **Security vulnerabilities** - Update immediately
- **Discord API changes** - Update JDA when Discord releases new features
- **Bug fixes** - Update when critical bugs are fixed
- **NeoForge updates** - May require updating KotlinForForge

### How to Update

1. Edit `build.gradle` with new version numbers
2. Run `./gradlew clean build`
3. Test thoroughly in development environment
4. Check for breaking API changes in library changelogs

### Version Compatibility Matrix

| Viscord | NeoForge | JDA | KotlinForForge |
|---------|----------|-----|----------------|
| 1.0.0   | 21.1.200+ | 5.0.0-beta.24 | 5.4.0+ |

## License Compliance

All embedded dependencies are permissively licensed:
- **JDA:** Apache 2.0
- **OkHttp:** Apache 2.0
- **Okio:** Apache 2.0
- **nv-websocket-client:** Apache 2.0
- **Trove4j:** LGPL 2.1

**Note:** Trove4j uses LGPL 2.1, which requires:
- Notice that Trove4j is used (this document satisfies that)
- Users can replace Trove4j if needed (JarJar system allows this)
- Source code availability (Trove4j is open source)

## Resources

- [JDA Documentation](https://jda.wiki/)
- [NeoForge JarJar Documentation](https://docs.neoforged.net/docs/gettingstarted/jarjar/)
- [KotlinForForge](https://github.com/thedarkcolour/KotlinForForge)
- [Trove4j Project](https://trove4j.sourceforge.net/)
- [OkHttp](https://square.github.io/okhttp/)

## Related Documents

- [TROVE_FIX.md](../TROVE_FIX.md) - Details on the Trove4j dependency fix
- [INSTALLATION.md](INSTALLATION.md) - User installation guide
- [README.md](../README.md) - Main project documentation

---

**Last Updated:** November 13, 2024  
**Mod Version:** 1.0.0  
**NeoForge Version:** 21.1.200+