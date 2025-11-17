# Contributing to Viscord

Thank you for your interest in contributing to Viscord! This document provides guidelines and instructions for contributing.

## 🤝 How to Contribute

### Reporting Bugs

If you find a bug, please create an issue with:
- **Clear title** describing the problem
- **Steps to reproduce** the issue
- **Expected behavior** vs actual behavior
- **Minecraft version** and **Forge/NeoForge version**
- **Viscord version** you're using
- **Relevant logs** (enable debug logging if possible)
- **Configuration** (sanitize tokens/webhooks)

### Suggesting Features

Feature requests are welcome! Please include:
- **Clear description** of the feature
- **Use case** - why is this feature needed?
- **Proposed implementation** (if you have ideas)
- **Compatibility** - which versions should support it?

### Pull Requests

We love pull requests! Before submitting:

1. **Fork the repository**
2. **Create a feature branch** (`git checkout -b feature/amazing-feature`)
3. **Test your changes** on all supported versions
4. **Follow code style** (see below)
5. **Update documentation** if needed
6. **Commit your changes** (`git commit -m 'Add amazing feature'`)
7. **Push to your branch** (`git push origin feature/amazing-feature`)
8. **Open a Pull Request**

## 🏗️ Development Setup

### Prerequisites

- **Java 17** (for Forge 1.20.1)
- **Java 21** (for Forge 1.21.1 and NeoForge 1.21.1)
- **Gradle** (wrapper included)
- **Git**

### Building

```bash
# Build specific version
cd viscord-template-1.21.1  # or other version directory
./gradlew build

# Build all versions (Linux/Mac)
./build-all-versions.sh

# Build all versions (Windows)
build-all-versions.bat
```

### Testing

1. Build the mod
2. Copy JAR to a test server's `mods/` folder
3. Configure Discord credentials
4. Test all features:
   - Bidirectional chat
   - Join/leave messages
   - Death messages
   - Advancement messages
   - Loop prevention
   - Multi-server functionality (if applicable)

## 📝 Code Style

- **Use existing code style** - match the formatting of surrounding code
- **Add comments** for complex logic
- **Use meaningful variable names**
- **Keep methods focused** - one responsibility per method
- **Handle exceptions properly** - log errors appropriately
- **Thread safety** - use `server.execute()` for server operations

### Java Conventions

```java
// Class names: PascalCase
public class DiscordManager {
    // Constants: UPPER_SNAKE_CASE
    private static final String DEFAULT_PREFIX = "[Server]";
    
    // Fields: camelCase
    private String botToken;
    
    // Methods: camelCase
    public void sendMessage(String content) {
        // Method body
    }
}
```

## 🧪 Version Compatibility

When contributing, ensure your changes work across all supported versions:
- **NeoForge 1.21.1** (primary version)
- **Forge 1.20.1**
- **Forge 1.21.1**

### API Differences

Be aware of API differences between versions:
- **Advancement events** differ between 1.20.1 and 1.21.1
- **Event bus registration** differs between Forge and NeoForge
- **Networking APIs** have changed

## 📦 Dependencies

When adding dependencies:
- **Minimize new dependencies** - only add if absolutely necessary
- **Check compatibility** across all versions
- **Update build.gradle** in all version directories
- **Document in README** if it requires external JARs

## 🐛 Debugging

Enable debug logging in `config/viscord-common.toml`:
```toml
enableDebugLogging = true
```

This will show:
- Message filtering decisions
- Discord connection status
- Webhook operations
- Error details

## 🔒 Security

- **Never commit** Discord tokens or webhook URLs
- **Sanitize logs** before sharing
- **Report security issues** privately
- **Follow Discord ToS** and rate limits

## 📄 Documentation

When making changes:
- Update **README.md** if adding features
- Update **INSTALLATION.md** if changing setup process
- Add **inline comments** for complex code
- Update **configuration examples** if adding config options

## ✅ Checklist Before Submitting PR

- [ ] Code compiles on all three platforms
- [ ] Tested in actual Minecraft server
- [ ] No hardcoded credentials
- [ ] Follows existing code style
- [ ] Documentation updated
- [ ] No unnecessary files included
- [ ] Commit messages are clear

## 💬 Questions?

If you have questions about contributing:
- Open a **Discussion** on GitHub
- Check existing **Issues** and **Pull Requests**
- Read the **documentation** in each version directory

## 🙏 Thank You!

Your contributions make Viscord better for everyone. We appreciate your time and effort!

---

**Happy coding!** 🚀
