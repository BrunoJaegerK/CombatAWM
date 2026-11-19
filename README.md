# CombatAWM

Combat tagging, combat-log protection, configurable PvP toggle, and command restrictions for **Nukkit PetteriM1 Edition**.

## Features

- Combat tag when players damage each other (melee and supported projectiles).
- Configurable tag duration and boss-bar timer/opponent display.
- Combat-log punishment can be enabled or disabled.
- PvP toggle per player: `/combat toggle`; preferences persist in `pvp.yml`.
- PvP toggle state is checked on attacks so disabled players cannot attack or be attacked.
- Optional command restrictions during combat, configurable as a blacklist or whitelist.
- Excluded worlds.
- `/combat status` to view PvP and combat status.
- `/combat reload` to reload config (admin permission).
- Clears combat sessions and boss bars on death, expiration, and plugin shutdown.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/combat` | Displays status | `combatawm.command` |
| `/combat status` | Displays PvP enabled/disabled and combat state | `combatawm.command` |
| `/combat toggle` | Toggles your PvP preference | `combatawm.command` |
| `/combat reload` | Reloads config | `combatawm.admin` |

## Installation

1. Download the jar from the GitHub Actions artifact or Releases.
2. Place `CombatAWM.jar` into the server's `plugins` folder.
3. Start the server and edit `plugins/CombatAWM/config.yml`.
4. Restart or run `/combat reload`.

## Configuration

```yaml
CombatTag:
  Enabled: true
  DurationSeconds: 20
  TagOnlyWhenDamageIsApplied: true
  BlockCommands:
    Enabled: false
    WhitelistMode: false
    List:
      - spawn
      - home
      - tpa
      - tp
      - warp
      - lobby

CombatLog:
  KillOnLogout: true

PvP:
  DefaultEnabled: true

ExcludedWorlds: []
```

Command restriction behavior:
- `WhitelistMode: false`: commands listed in `List` are blocked while tagged.
- `WhitelistMode: true`: only commands listed in `List` are allowed while tagged.
- Set `Enabled: false` to disable restrictions.

Combat-bar messages are configurable under `Messages.CombatBar`. Other messages are configurable in the `Messages` section.

PvP preferences are stored in `pvp.yml` separately from the main configuration.

## Build

This project targets Java 17 and the Nukkit PetteriM1 Edition Maven artifact.

```bash
# Build and install the exact PM1E API locally first (Java 17)
git clone --branch release https://github.com/BrunoJaegerK/NukkitPetteriM1Edition.git nukkit-server
mvn -f nukkit-server/pom.xml -DskipTests -Dmaven.test.skip=true install
# Then build CombatAWM
mvn clean package
```

GitHub Actions checks out the PM1E source, installs its Maven artifact into the runner's local repository, builds CombatAWM, and uploads the compiled jar as the **CombatAWM** workflow artifact. It runs on pushes, pull requests, and manual workflow dispatch.

## Compatibility

Built against Nukkit PetteriM1 Edition API. It is not a Bukkit/Spigot/Paper plugin.

