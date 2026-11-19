# CombatAWM

Combat management plugin inspired by PvPManager, adapted for **Nukkit PetteriM1 Edition**. CombatAWM does not provide money, rewards, kill bonuses, or reward commands.

## Features

- Combat tagging on player-vs-player damage, including player-shot projectiles.
- Configurable combat timer and boss-bar text showing the remaining tag and last opponent.
- Combat-log punishment (kill on logout) can be enabled or disabled.
- PvP toggle, persistent per-player state, and configurable toggle cooldown.
- Newbie protection with persistent expiry and optional manual disable.
- Respawn protection for a configurable number of seconds.
- Command blacklist or whitelist during combat.
- Optional restrictions on block placement, breaking, and interaction while tagged.
- Anti-border-hopping: blocks teleporting tagged players into configured safe worlds.
- Anti-kill-abuse: records repeated kills within a time window, warns before the threshold, and blocks lethal PvP damage once the configured limit has already been reached.
- Excluded worlds.
- Player commands: `/combat`, `/combat status`, `/combat toggle`, `/combat newbie`, `/combat newbie disable`, `/combat list`.
- Aliases: `/pvp`, `/pvptoggle`, `/pvpstatus`, `/pvplist`, `/newbie`, `/tag`.
- Admin command: `/combat untag <player>`, `/combat reload`.
- Persisted PvP preferences and newbie-protection timers.

## Commands

| Command | Function | Permission |
|---|---|---|
| `/combat`, `/combat status`, `/pvpstatus`, `/tag` | PvP/combat status | `combatawm.command` |
| `/combat toggle`, `/pvp`, `/pvptoggle` | Toggle personal PvP | `combatawm.command` |
| `/combat newbie`, `/newbie` | Show newbie protection timer | `combatawm.command` |
| `/combat newbie disable` | Remove newbie protection if allowed | `combatawm.command` |
| `/combat list`, `/pvplist` | List online players with PvP enabled | `combatawm.command` |
| `/combat untag <player>` | Remove combat tag from both players | `combatawm.admin` |
| `/combat reload` | Reload config | `combatawm.admin` |

Bypass permissions (default operator):
- `combatawm.bypass.commands`
- `combatawm.bypass.actions`
- `combatawm.bypass.borderhopping`
- `combatawm.bypass.togglecooldown`
- `combatawm.bypass.killabuse`

## Config

```yaml
CombatTag:
  Enabled: true
  DurationSeconds: 20
  TagOnlyWhenDamageIsApplied: true
  BlockCommands:
    Enabled: false
    WhitelistMode: false
    List: [spawn, home, tpa, tp, warp, lobby]
  BlockPlace:
    Enabled: false
  BlockBreak:
    Enabled: false
  BlockInteract:
    Enabled: false

CombatLog:
  KillOnLogout: true

PvP:
  DefaultEnabled: true
  ToggleCooldownSeconds: 15

NewbieProtection:
  Enabled: true
  DurationSeconds: 600
  AllowPlayerDisable: true
  RemoveOnDeath: true

RespawnProtection:
  DurationSeconds: 3

AntiBorderHopping:
  Enabled: false
  SafeWorlds: []

AntiKillAbuse:
  Enabled: true
  MaxKills: 3
  WindowSeconds: 3600
  WarnBeforeLimit: true

ExcludedWorlds: []
```

Messages are customizable under `Messages`. Player settings are saved to `pvp.yml`; newbie expiry timestamps are saved in `protection.yml`.

## Installation

1. Download `CombatAWM.jar` from Releases or the GitHub Actions artifact.
2. Place it in the server's `plugins` directory.
3. Start the server and edit `plugins/CombatAWM/config.yml`.
4. Run `/combat reload` or restart.

## Build

Java 17. CI builds the Nukkit PetteriM1 Edition API source first, installs it into the runner's Maven repository, and then compiles CombatAWM.

```bash
git clone --branch release https://github.com/BrunoJaegerK/NukkitPetteriM1Edition.git nukkit-server
mvn -f nukkit-server/pom.xml -DskipTests -Dmaven.test.skip=true install
mvn clean package
```

The workflow uploads `target/CombatAWM.jar` as an artifact on pushes, pull requests, and manual runs.

## Notes

This is a native Nukkit plugin. Bukkit/Spigot/Paper APIs and features requiring WorldGuard, PlaceholderAPI, Bukkit teams, or NPCs are not used. Safe-world checks use world names configured in CombatAWM. Please test on a staging server before production use.
