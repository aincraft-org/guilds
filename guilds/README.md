# Towny - Enhanced Town & Guild Management

[![Java](https://img.shields.io/badge/Java-26-orange.svg)](https://openjdk.org/projects/jdk/26/)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-brightgreen.svg)](https://www.minecraft.net/)
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
- 🌳 **Tech Trees** - Unlock upgrades across 4 branches (Infrastructure, Defense, Commerce, Culture)
- 🏰 **Nation System** - Form alliances, declare enemies, manage diplomacy between towns
- 💬 **Town Chat** - Private chat channels with admin spy mode
- 💰 **Vault Economy** - Full economy integration with town banking and transaction logging
- 🎯 **Town Specializations** - Choose MINING, TRADE_HUB, MILITARY, ARCANE, or AGRICULTURAL perks
- 📋 **Weekly Quests** - Rotating town challenges with tech point rewards
- 📐 **Blueprints** - Save and paste building templates (WorldEdit integration)
- 📢 **Broadcast System** - Town-wide announcements and communication
- 📊 **Town Levels** - Leveling system with configurable benefits
- 💾 **SQLite Database** - Persistent storage with HikariCP connection pooling
- ⚡ **High Performance** - Caffeine caching for optimal performance

## 📦 Installation

### Requirements

- **Minecraft Server:** Paper 26.2 or later
- **Java:** JDK 26 or later
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

### Tech Tree Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/techtree` | Open tech tree GUI | `towny.techtree.view` |
| `/techtree info <node>` | Show node details | `towny.techtree.view` |
| `/techtree unlock <node>` | Unlock a tech node | `towny.techtree.unlock` |
| `/techtree list [branch]` | List nodes by branch | `towny.techtree.view` |
| `/tt` | Alias for /techtree | `towny.techtree.view` |

### Nation Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/nation create <name>` | Create a nation | `towny.nation.create` |
| `/nation invite <town>` | Invite a town | `towny.nation.invite` |
| `/nation join <nation>` | Join a nation | `towny.nation.join` |
| `/nation leave` | Leave nation | `towny.nation.leave` |
| `/nation list` | List all nations | `towny.commands.nation` |
| `/nation info [nation]` | Show nation details | `towny.commands.nation` |
| `/nation ally <nation>` | Form alliance | `towny.nation.ally` |
| `/nation enemy <nation>` | Declare enemy | `towny.nation.enemy` |
| `/nation kick <town>` | Kick a town | `towny.nation.kick` |
| `/nation set king <player>` | Transfer kingship | `towny.nation.set` |
| `/nation set tax <rate>` | Set tax rate | `towny.nation.set` |
| `/nation set open <true/false>` | Toggle open/closed | `towny.nation.set` |
| `/n` | Alias for /nation | `towny.commands.nation` |

### Chat Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/tc <message>` | Send town chat message | `towny.chat.town` |
| `/tc` | Toggle town chat channel | `towny.chat.town` |
| `/townchat` | Alias for /tc | `towny.chat.town` |

### Specialization Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/town specialize` | View specializations | `towny.town.specialize` |
| `/town specialize <type>` | Choose specialization | `towny.town.specialize` |
| `/town specialize reset` | Remove specialization | `towny.town.specialize` |

### Quest Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/town quests` | List active quests | `towny.commands.nation` |
| `/town quest progress` | Show quest progress | `towny.commands.nation` |
| `/town quest refresh` | Regenerate quests (admin) | `towny.admin.quest` |

### Blueprint Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/blueprint save <name>` | Save selection | `towny.commands.blueprint` |
| `/blueprint list` | List town blueprints | `towny.commands.blueprint` |
| `/blueprint load <name>` | View blueprint info | `towny.commands.blueprint` |
| `/blueprint apply <name>` | Paste at location | `towny.commands.blueprint` |
| `/blueprint delete <name>` | Delete blueprint | `towny.commands.blueprint` |
| `/bp` | Alias for /blueprint | `towny.commands.blueprint` |

## 🔐 Permission System

Towny uses standard Bukkit permission nodes (`towny.*`). These work with LuckPerms, PermissionsEx, or any Bukkit-compatible permission plugin. Nodes marked `default: op` require operator status; `default: true` are available to all players.

### Wildcard Permissions

| Node | Description |
|------|-------------|
| `towny.*` | Grants ALL permissions |
| `towny.admin.*` | Grants all admin permissions |
| `towny.town.*` | Grants all town permissions |
| `towny.resident.*` | Grants all resident permissions |
| `towny.plot.*` | Grants all plot permissions |
| `towny.general.*` | Grants all general permissions |
| `towny.broadcast.*` | Grants all broadcast permissions |
| `towny.nation.*` | Grants all nation permissions |
| `towny.chat.*` | Grants all chat permissions |
| `towny.techtree.*` | Grants all tech tree permissions |
| `towny.blueprint.*` | Grants all blueprint permissions |

### Town Permissions

| Node | Default | Description |
|------|---------|-------------|
| `towny.town.create` | true | Create a new town |
| `towny.town.delete` | true | Delete your town |
| `towny.town.join` | true | Join a town |
| `towny.town.leave` | true | Leave your town |
| `towny.town.claim` | true | Claim land for your town |
| `towny.town.unclaim` | true | Unclaim land |
| `towny.town.spawn` | true | Teleport to town spawn |
| `towny.town.set` | true | Configure town settings |
| `towny.town.kick` | true | Kick a player from town |
| `towny.town.invite` | true | Invite a player to town |
| `towny.town.mayor` | true | Mayor-only actions |
| `towny.town.assistant` | true | Assistant actions |
| `towny.town.specialize` | true | Set town specialization |

### Plot Permissions

| Node | Default | Description |
|------|---------|-------------|
| `towny.plot.claim` | true | Claim a plot |
| `towny.plot.unclaim` | true | Unclaim a plot |
| `towny.plot.info` | true | View plot information |
| `towny.plot.set` | true | Set plot type |
| `towny.plot.perms` | true | View/modify plot permissions |
| `towny.plot.toggle` | true | Toggle plot settings |
| `towny.plot.buy` | true | Buy plots for sale |
| `towny.plot.forsale` | true | Put plot up for sale |
| `towny.plot.list` | true | List town plots |
| `towny.plot.perm` | true | Set specific plot flags |

### Nation Permissions

| Node | Default | Description |
|------|---------|-------------|
| `towny.commands.nation` | true | Base nation command access |
| `towny.nation.create` | true | Create a nation |
| `towny.nation.invite` | true | Invite a town to nation |
| `towny.nation.join` | true | Join a nation |
| `towny.nation.leave` | true | Leave nation |
| `towny.nation.kick` | true | Kick a town from nation |
| `towny.nation.ally` | true | Form alliance |
| `towny.nation.enemy` | true | Declare enemy |
| `towny.nation.set` | true | Configure nation settings |

### Chat Permissions

| Node | Default | Description |
|------|---------|-------------|
| `towny.chat.town` | true | Send/receive town chat |
| `towny.chat.spy` | op | Spy on any town's chat |

### Tech Tree Permissions

| Node | Default | Description |
|------|---------|-------------|
| `towny.techtree.view` | true | View the tech tree |
| `towny.techtree.unlock` | true | Unlock tech nodes |

### Blueprint Permissions

| Node | Default | Description |
|------|---------|-------------|
| `towny.commands.blueprint` | true | Base blueprint command access |

### Quest Permissions

| Node | Default | Description |
|------|---------|-------------|
| `towny.quest` | true | View town quests |
| `towny.admin.quest` | op | Refresh/regenerate quests |

### General Permissions

| Node | Default | Description |
|------|---------|-------------|
| `towny.general.info` | true | View plugin info |
| `towny.general.chat` | true | Use general chat |
| `towny.general.top` | true | View town rankings |
| `towny.general.prices` | true | View prices |
| `towny.general.time` | true | View server time |
| `towny.general.universe` | true | View universe info |
| `towny.general.version` | true | View plugin version |
| `towny.map` | true | View town map |
| `towny.level` | true | View town level info |

### Admin Permissions

| Node | Default | Description |
|------|---------|-------------|
| `towny.admin.reload` | op | Reload config |
| `towny.admin.backup` | op | Backup database |
| `towny.admin.purge` | op | Purge data |
| `towny.admin.town` | op | Admin town management |
| `towny.admin.resident` | op | Admin resident management |
| `towny.admin.plot` | op | Admin plot management |
| `towny.admin.plottype` | op | Manage plot types |
| `towny.admin.unclaim` | op | Force unclaim |
| `towny.admin.claim` | op | Force claim |
| `towny.admin.bypass` | op | Bypass all restrictions |
| `towny.admin.nation` | op | Admin nation management |
| `towny.admin.blueprint` | op | Admin blueprint management |
| `towny.admin.quest` | op | Admin quest management |
| `towny.admin.perm` | op | Debug permissions |
| `towny.admin.specialize` | op | Force specialization change |

### Broadcast Permissions

| Node | Default | Description |
|------|---------|-------------|
| `towny.broadcast` | true | Base broadcast access |
| `towny.broadcast.create` | true | Create broadcasts |
| `towny.broadcast.read` | true | Read broadcasts |
| `towny.broadcast.manage` | true | Manage broadcasts |

### Resident Permissions

| Node | Default | Description |
|------|---------|-------------|
| `towny.resident.info` | true | View resident info |
| `towny.resident.list` | true | List residents |
| `towny.resident.friend` | true | Manage friends |
| `towny.resident.town` | true | View resident's town |

### In-Game Plot Flags

These are set per-plot via `/plot perms` and control who can do what on a specific plot:

- **BUILD** - Can place blocks
- **DESTROY** - Can break blocks
- **SWITCH** - Can use doors, levers, buttons
- **ITEM_USE** - Can use items
- **CLAIM** - Can claim the plot
- **UNCLAIM** - Can unclaim the plot
- **SPAWN** - Can teleport to plot
- **SET_SPAWN** - Can set plot spawn
- **INVITE** - Can invite players
- **KICK** - Can kick players
- **PROMOTE** - Can promote players
- **DEMOTE** - Can demote players
- **ADMIN** - Administrative access
- **BYPASS** - Bypass plot restrictions

### Permission Evaluation Order

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
# All player and town balances are provided by Vault. If Vault or its economy
# provider is unavailable, economy operations fail safely without a persisted
# town-balance fallback.

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

- **Language:** Java 26
- **Framework:** Paper API 26.2
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
- **TechTreeService** - Tech node unlocking and effects
- **EconomyService** - Vault economy integration with town banking
- **ChatService** - Town chat channels and admin spy
- **NationService** - Nation management and diplomacy
- **SpecializationService** - Town specialization perks
- **QuestService** - Weekly quest generation and tracking
- **BlueprintService** - Building template save/load/apply

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

This downloads Paper 26.2 and starts a server with the plugin loaded.

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
