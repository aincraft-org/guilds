# Towny - Enhanced Town & Guild Management

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-brightgreen.svg)](https://www.minecraft.net/)
[![Paper](https://img.shields.io/badge/Paper-API-blue.svg)](https://papermc.io/)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)](LICENSE)

> A modern, feature-rich town and nation management plugin for Minecraft Paper servers, built with performance and extensibility in mind.

## 🎯 Overview

Towny is a comprehensive Minecraft plugin that enables players to create towns, manage land claims, and build communities. It features a sophisticated permission system, plot management, tech trees, and extensive customization options.

### Key Features

- 🏘️ **Town Management** - Create, manage, and grow towns with configurable settings
- 🗺️ **Land Claiming** - Claim and protect land chunks for your town
- 🔐 **Advanced Permissions** - Granular permission system for plots, towns, and residents
- 🏪 **Plot Types** - Configurable plot types (residential, commercial, farm, etc.)
- 🌳 **Tech Trees** - Unlock upgrades and features through tech tree progression
- 📢 **Broadcast System** - Town-wide announcements and communication
- 🗣️ **Town Chat** - Private chat channels for town members
- 📊 **Town Levels** - Leveling system with configurable benefits
- 💾 **SQLite Database** - Persistent storage with HikariCP connection pooling
- ⚡ **High Performance** - Caffeine caching for optimal performance

## 📦 Installation

### Requirements

- **Minecraft Server:** Paper 1.21.4 or later
- **Java:** JDK 21 or later
- **Database:** SQLite (included) or MySQL (optional)

### Quick Start

1. Download the latest release from [Releases](https://github.com/mintychochip/guilds/releases)
2. Place `Towny.jar` in your server's `plugins/` folder
3. Start the server
4. Configure `config.yml` to your liking
5. Restart the server

### Building from Source

```bash
# Clone the repository
git clone https://github.com/mintychochip/guilds.git
cd guilds

# Build with Gradle
./gradlew build

# The compiled JAR will be in build/libs/Towny.jar
```

## 🎮 Commands

### Town Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/town create <name>` | Create a new town | `towny.town.create` |
| `/town delete` | Delete your town | `towny.town.delete` |
| `/town join <town>` | Join a town | `towny.town.join` |
| `/town leave` | Leave your current town | `towny.town.leave` |
| `/town claim` | Claim land for your town | `towny.town.claim` |
| `/town unclaim` | Unclaim land | `towny.town.unclaim` |
| `/town spawn` | Teleport to town spawn | `towny.town.spawn` |
| `/town set` | Configure town settings | `towny.town.set` |
| `/town invite <player>` | Invite a player to your town | `towny.town.invite` |
| `/town kick <player>` | Kick a player from your town | `towny.town.kick` |

### Plot Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/plot claim` | Claim a plot for yourself | `towny.plot.claim` |
| `/plot unclaim` | Unclaim your plot | `towny.plot.unclaim` |
| `/plot info` | View plot information | `towny.plot.info` |
| `/plot set <type>` | Set plot type | `towny.plot.set` |
| `/plot perms` | View/modify plot permissions | `towny.plot.perms` |
| `/plot toggle` | Toggle plot settings | `towny.plot.toggle` |

### Permission Debug Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/perm check` | Check all permissions at location | `towny.admin.perm` |
| `/perm build` | Test build permission | `towny.admin.perm` |
| `/perm destroy` | Test destroy permission | `towny.admin.perm` |
| `/perm plot [flag]` | Test specific plot permission | `towny.admin.perm` |
| `/perm flags` | Show available permission flags | `towny.admin.perm` |

### Other Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/map` | View town map | `towny.map` |
| `/townlevel` | View town level info | `towny.level` |
| `/towny reload` | Reload configuration | `towny.admin.reload` |

## 🔐 Permission System

Towny features a comprehensive permission system with multiple layers:

### Permission Flags

- **BUILD** - Can place blocks
- **DESTROY** - Can break blocks
- **SWITCH** - Can use doors, levers, buttons
- **ITEM_USE** - Can use items
- **CLAIM** - Can claim land
- **UNCLAIM** - Can unclaim land
- **SPAWN** - Can teleport to town
- **SET_SPAWN** - Can set town spawn
- **INVITE** - Can invite players
- **KICK** - Can kick players
- **PROMOTE** - Can promote players
- **DEMOTE** - Can demote players
- **ADMIN** - Administrative access
- **BYPASS** - Bypass permissions

### Permission Hierarchy

1. **Plot Owner** - Highest priority on owned plots
2. **Town Rank** - Mayor, Assistant, etc.
3. **Plot Type Defaults** - Default permissions per plot type
4. **Town Defaults** - Default permissions for town members
5. **Wilderness** - Default world permissions

## 🗺️ Plot Types

Towny supports configurable plot types:

- **Residential** - Standard player housing
- **Commercial** - Shops and businesses
- **Farm** - Agricultural plots
- **Embassy** - Diplomatic plots
- **Arena** - PvP-enabled areas
- **Jail** - Prison plots
- **Inn** - Public lodging
- **Wilds** - Wilderness plots

Each plot type has configurable:
- Permission defaults
- Price
- Tax rate
- Size limits

## ⚙️ Configuration

### Main Configuration (`config.yml`)

```yaml
# Database settings
database:
  type: sqlite  # sqlite or mysql
  mysql:
    host: localhost
    port: 3306
    database: towny
    username: towny
    password: secret

# Town settings
towns:
  maxResidents: 100
  minDistanceFromOtherTowns: 5
  maxClaimRadius: 100
  
# Economy settings
economy:
  enabled: true
  townCreationCost: 1000.0
  claimCost: 100.0
  dailyTax: 10.0

# Permission settings
permissions:
  defaultBuild: false
  defaultDestroy: false
  defaultSwitch: true
  defaultItemUse: true
```

### Town Levels (`config.yml`)

Configure town levels with increasing benefits:

```yaml
townLevels:
  - level: 1
    name: "Hamlet"
    minResidents: 0
    maxClaims: 10
    bonusPermissions: []
    
  - level: 2
    name: "Village"
    minResidents: 5
    maxClaims: 25
    bonusPermissions: ["towny.town.spawn"]
    
  - level: 3
    name: "Town"
    minResidents: 15
    maxClaims: 50
    bonusPermissions: ["towny.town.set"]
```

## 🏗️ Architecture

### Technology Stack

- **Language:** Java 21
- **Framework:** Paper API 1.21.4
- **DI Framework:** Google Guice 7.0.0
- **Database:** SQLite with HikariCP 5.1.0
- **Caching:** Caffeine 3.1.8
- **Build Tool:** Gradle 8.x
- **Testing:** JUnit 5, MockBukkit

### Project Structure

```
src/
├── main/
│   ├── java/org/aincraft/towny/
│   │   ├── commands/          # Command handlers
│   │   ├── services/          # Business logic
│   │   │   └── impl/          # Service implementations
│   │   ├── models/            # Data models
│   │   ├── listeners/         # Event listeners
│   │   ├── database/          # Database access
│   │   ├── config/            # Configuration loaders
│   │   ├── dependency/        # Guice modules
│   │   └── gui/               # GUI implementations
│   └── resources/
│       ├── plugin.yml         # Plugin metadata
│       └── config.yml         # Default config
└── test/                      # Test sources
```

### Key Components

#### Services Layer

- **PermissionService** - Permission evaluation and caching
- **PlotService** - Plot management and operations
- **TownService** - Town CRUD operations
- **ResidentService** - Player data management
- **BroadcastService** - Town announcements
- **TownLevelService** - Level progression system

#### Dependency Injection

All services are managed by Google Guice for:
- Loose coupling
- Easy testing
- Clean architecture
- Singleton management

## 🧪 Development

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests PermissionServiceTest

# Run with coverage
./gradlew test jacocoTestReport
```

### Development Setup

1. Clone the repository
2. Open in IntelliJ IDEA (recommended)
3. Import Gradle project
4. Enable annotation processing for Guice
5. Run `./gradlew build` to verify setup

### Test Server

```bash
# Start a test server with the plugin
./gradlew runServer
```

This downloads Paper 1.21.4 and starts a server with the plugin loaded.

## 🐛 Bug Reports

Found a bug? Please open an issue with:
- Server version
- Plugin version
- Steps to reproduce
- Expected behavior
- Actual behavior
- Logs/error messages

## 🤝 Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Write/update tests
5. Submit a pull request

### Code Style

- Follow Java naming conventions
- Use meaningful variable names
- Add JavaDoc for public methods
- Keep methods focused and small
- Write unit tests for new features

## 📄 License

This project is proprietary software. All rights reserved.

## 👥 Credits

- **Author:** Aincraft
- **Contributors:** See [Contributors](https://github.com/mintychochip/guilds/graphs/contributors)

## 🔗 Links

- [GitHub Repository](https://github.com/mintychochip/guilds)
- [Issue Tracker](https://github.com/mintychochip/guilds/issues)
- [Wiki](https://github.com/mintychochip/guilds/wiki)

## 📊 Stats

![Lines of Code](https://img.shields.io/tokei/lines/github/mintychochip/guilds)
![Files](https://img.shields.io/github/directory-file-count/mintychochip/guilds)
![Last Commit](https://img.shields.io/github/last-commit/mintychochip/guilds)

---

**Made with ❤️ for Minecraft communities**
