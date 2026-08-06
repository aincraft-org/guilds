# Guilds - Enhanced Town & Guild Management

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-brightgreen.svg)](https://www.minecraft.net/)
[![Paper](https://img.shields.io/badge/Paper-API-blue.svg)](https://papermc.io/)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)](LICENSE)

> A modern, feature-rich town and nation management plugin for Minecraft Paper servers, built with performance and extensibility in mind.

## 🎯 Overview

Guilds is a comprehensive Minecraft plugin that enables players to create towns, manage land claims, and build communities. It features a sophisticated permission system, plot management, tech trees, and extensive customization options.

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

- **Minecraft Server:** Paper 1.21.4 or later
- **Java:** JDK 21 or later
- **Database:** SQLite (included) or MySQL (optional)

### Quick Start

1. Download the latest release from [Releases](https://github.com/mintychochip/guilds/releases)
2. Place `Guilds.jar` in your server's `plugins/` folder
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

# The compiled JAR will be in build/libs/Guilds.jar
```

## 🎮 Commands

### Town Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/town create <name>` | Create a new town | `guilds.town.create` |
| `/town delete` | Delete your town | `guilds.town.delete` |
| `/town join <town>` | Join a town | `guilds.town.join` |
| `/town leave` | Leave your current town | `guilds.town.leave` |
| `/town claim` | Claim land for your town | `guilds.town.claim` |
| `/town unclaim` | Unclaim land | `guilds.town.unclaim` |
| `/town spawn` | Teleport to town spawn | `guilds.town.spawn` |
| `/town set` | Configure town settings | `guilds.town.set` |
| `/town invite <player>` | Invite a player to your town | `guilds.town.invite` |
| `/town kick <player>` | Kick a player from your town | `guilds.town.kick` |

### Plot Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/plot claim` | Claim a plot for yourself | `guilds.plot.claim` |
| `/plot unclaim` | Unclaim your plot | `guilds.plot.unclaim` |
| `/plot info` | View plot information | `guilds.plot.info` |
| `/plot set <type>` | Set plot type | `guilds.plot.set` |
| `/plot perms` | View/modify plot permissions | `guilds.plot.perms` |
| `/plot toggle` | Toggle plot settings | `guilds.plot.toggle` |

### Permission Debug Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/perm check` | Check all permissions at location | `guilds.admin.perm` |
| `/perm build` | Test build permission | `guilds.admin.perm` |
| `/perm destroy` | Test destroy permission | `guilds.admin.perm` |
| `/perm plot [flag]` | Test specific plot permission | `guilds.admin.perm` |
| `/perm flags` | Show available permission flags | `guilds.admin.perm` |

### Other Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/map` | View town map | `guilds.map` |
| `/townlevel` | View town level info | `guilds.level` |
| `/guilds reload` | Reload configuration | `guilds.admin.reload` |

### Tech Tree Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/techtree` | Open tech tree GUI | `guilds.techtree.view` |
| `/techtree info <node>` | Show node details | `guilds.techtree.view` |
| `/techtree unlock <node>` | Unlock a tech node | `guilds.techtree.unlock` |
| `/techtree list [branch]` | List nodes by branch | `guilds.techtree.view` |
| `/tt` | Alias for /techtree | `guilds.techtree.view` |

### Nation Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/nation create <name>` | Create a nation | `guilds.nation.create` |
| `/nation invite <town>` | Invite a town | `guilds.nation.invite` |
| `/nation join <nation>` | Join a nation | `guilds.nation.join` |
| `/nation leave` | Leave nation | `guilds.nation.leave` |
| `/nation list` | List all nations | `guilds.commands.nation` |
| `/nation info [nation]` | Show nation details | `guilds.commands.nation` |
| `/nation ally <nation>` | Form alliance | `guilds.nation.ally` |
| `/nation enemy <nation>` | Declare enemy | `guilds.nation.enemy` |
| `/nation kick <town>` | Kick a town | `guilds.nation.kick` |
| `/nation set king <player>` | Transfer kingship | `guilds.nation.set` |
| `/nation set tax <rate>` | Set tax rate | `guilds.nation.set` |
| `/nation set open <true/false>` | Toggle open/closed | `guilds.nation.set` |
| `/n` | Alias for /nation | `guilds.commands.nation` |

### Chat Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/tc <message>` | Send town chat message | `guilds.chat.town` |
| `/tc` | Toggle town chat channel | `guilds.chat.town` |
| `/townchat` | Alias for /tc | `guilds.chat.town` |

### Specialization Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/town specialize` | View specializations | `guilds.town.specialize` |
| `/town specialize <type>` | Choose specialization | `guilds.town.specialize` |
| `/town specialize reset` | Remove specialization | `guilds.town.specialize` |

### Quest Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/town quests` | List active quests | `guilds.commands.nation` |
| `/town quest progress` | Show quest progress | `guilds.commands.nation` |
| `/town quest refresh` | Regenerate quests (admin) | `guilds.admin.quest` |

### Blueprint Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/blueprint save <name>` | Save selection | `guilds.commands.blueprint` |
| `/blueprint list` | List town blueprints | `guilds.commands.blueprint` |
| `/blueprint load <name>` | View blueprint info | `guilds.commands.blueprint` |
| `/blueprint apply <name>` | Paste at location | `guilds.commands.blueprint` |
| `/blueprint delete <name>` | Delete blueprint | `guilds.commands.blueprint` |
| `/bp` | Alias for /blueprint | `guilds.commands.blueprint` |

## 🔐 Permission System

Guilds uses standard Bukkit permission nodes (`guilds.*`). These work with LuckPerms, PermissionsEx, or any Bukkit-compatible permission plugin. Nodes marked `default: op` require operator status; `default: true` are available to all players.

### Wildcard Permissions

| Node | Description |
|------|-------------|
| `guilds.*` | Grants ALL permissions |
| `guilds.admin.*` | Grants all admin permissions |
| `guilds.town.*` | Grants all town permissions |
| `guilds.resident.*` | Grants all resident permissions |
| `guilds.plot.*` | Grants all plot permissions |
| `guilds.general.*` | Grants all general permissions |
| `guilds.broadcast.*` | Grants all broadcast permissions |
| `guilds.nation.*` | Grants all nation permissions |
| `guilds.chat.*` | Grants all chat permissions |
| `guilds.techtree.*` | Grants all tech tree permissions |
| `guilds.blueprint.*` | Grants all blueprint permissions |

### Town Permissions

| Node | Default | Description |
|------|---------|-------------|
| `guilds.town.create` | true | Create a new town |
| `guilds.town.delete` | true | Delete your town |
| `guilds.town.join` | true | Join a town |
| `guilds.town.leave` | true | Leave your town |
| `guilds.town.claim` | true | Claim land for your town |
| `guilds.town.unclaim` | true | Unclaim land |
| `guilds.town.spawn` | true | Teleport to town spawn |
| `guilds.town.set` | true | Configure town settings |
| `guilds.town.kick` | true | Kick a player from town |
| `guilds.town.invite` | true | Invite a player to town |
| `guilds.town.mayor` | true | Mayor-only actions |
| `guilds.town.assistant` | true | Assistant actions |
| `guilds.town.specialize` | true | Set town specialization |

### Plot Permissions

| Node | Default | Description |
|------|---------|-------------|
| `guilds.plot.claim` | true | Claim a plot |
| `guilds.plot.unclaim` | true | Unclaim a plot |
| `guilds.plot.info` | true | View plot information |
| `guilds.plot.set` | true | Set plot type |
| `guilds.plot.perms` | true | View/modify plot permissions |
| `guilds.plot.toggle` | true | Toggle plot settings |
| `guilds.plot.buy` | true | Buy plots for sale |
| `guilds.plot.forsale` | true | Put plot up for sale |
| `guilds.plot.list` | true | List town plots |
| `guilds.plot.perm` | true | Set specific plot flags |

### Nation Permissions

| Node | Default | Description |
|------|---------|-------------|
| `guilds.commands.nation` | true | Base nation command access |
| `guilds.nation.create` | true | Create a nation |
| `guilds.nation.invite` | true | Invite a town to nation |
| `guilds.nation.join` | true | Join a nation |
| `guilds.nation.leave` | true | Leave nation |
| `guilds.nation.kick` | true | Kick a town from nation |
| `guilds.nation.ally` | true | Form alliance |
| `guilds.nation.enemy` | true | Declare enemy |
| `guilds.nation.set` | true | Configure nation settings |

### Chat Permissions

| Node | Default | Description |
|------|---------|-------------|
| `guilds.chat.town` | true | Send/receive town chat |
| `guilds.chat.spy` | op | Spy on any town's chat |

### Tech Tree Permissions

| Node | Default | Description |
|------|---------|-------------|
| `guilds.techtree.view` | true | View the tech tree |
| `guilds.techtree.unlock` | true | Unlock tech nodes |

### Blueprint Permissions

| Node | Default | Description |
|------|---------|-------------|
| `guilds.commands.blueprint` | true | Base blueprint command access |

### Quest Permissions

| Node | Default | Description |
|------|---------|-------------|
| `guilds.quest` | true | View town quests |
| `guilds.admin.quest` | op | Refresh/regenerate quests |

### General Permissions

| Node | Default | Description |
|------|---------|-------------|
| `guilds.general.info` | true | View plugin info |
| `guilds.general.chat` | true | Use general chat |
| `guilds.general.top` | true | View town rankings |
| `guilds.general.prices` | true | View prices |
| `guilds.general.time` | true | View server time |
| `guilds.general.universe` | true | View universe info |
| `guilds.general.version` | true | View plugin version |
| `guilds.map` | true | View town map |
| `guilds.level` | true | View town level info |

### Admin Permissions

| Node | Default | Description |
|------|---------|-------------|
| `guilds.admin.reload` | op | Reload config |
| `guilds.admin.backup` | op | Backup database |
| `guilds.admin.purge` | op | Purge data |
| `guilds.admin.town` | op | Admin town management |
| `guilds.admin.resident` | op | Admin resident management |
| `guilds.admin.plot` | op | Admin plot management |
| `guilds.admin.plottype` | op | Manage plot types |
| `guilds.admin.unclaim` | op | Force unclaim |
| `guilds.admin.claim` | op | Force claim |
| `guilds.admin.bypass` | op | Bypass all restrictions |
| `guilds.admin.nation` | op | Admin nation management |
| `guilds.admin.blueprint` | op | Admin blueprint management |
| `guilds.admin.quest` | op | Admin quest management |
| `guilds.admin.perm` | op | Debug permissions |
| `guilds.admin.specialize` | op | Force specialization change |

### Broadcast Permissions

| Node | Default | Description |
|------|---------|-------------|
| `guilds.broadcast` | true | Base broadcast access |
| `guilds.broadcast.create` | true | Create broadcasts |
| `guilds.broadcast.read` | true | Read broadcasts |
| `guilds.broadcast.manage` | true | Manage broadcasts |

### Resident Permissions

| Node | Default | Description |
|------|---------|-------------|
| `guilds.resident.info` | true | View resident info |
| `guilds.resident.list` | true | List residents |
| `guilds.resident.friend` | true | Manage friends |
| `guilds.resident.town` | true | View resident's town |

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

Guilds supports configurable plot types:

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
    database: guilds
    username: guilds
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
    bonusPermissions: ["guilds.town.spawn"]
    
  - level: 3
    name: "Town"
    minResidents: 15
    maxClaims: 50
    bonusPermissions: ["guilds.town.set"]
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
│   ├── java/org/aincraft/guilds/
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
