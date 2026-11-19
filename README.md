# CombatAWM

Plugin de gestión de combate inspirado en PvPManager y adaptado para **Nukkit PetteriM1 Edition**. CombatAWM no incluye dinero, recompensas, bonificaciones por asesinatos ni comandos de recompensas.

## Características

- Etiquetado de combate al causar daño entre jugadores, incluidos los proyectiles disparados por jugadores.
- Temporizador de combate configurable y barra de jefe que muestra el tiempo restante y el último oponente.
- Castigo por desconectarse durante el combate: eliminar al jugador al cerrar sesión (configurable).
- Activación y desactivación del PvP por jugador, con estado persistente y tiempo de espera configurable.
- Protección para jugadores nuevos con vencimiento persistente y opción para desactivarla manualmente.
- Protección al reaparecer, durante una cantidad configurable de segundos.
- Bloqueo de comandos de escape durante el combate (activado por defecto); el bypass requiere permiso explícito.\n- Protección opcional para jugadores nuevos (desactivada por defecto para que el combate funcione desde el primer momento).
- Restricciones opcionales para colocar, romper e interactuar con bloques mientras el jugador está en combate.
- Anti-escape entre mundos: impide que un jugador marcado en combate se teletransporte a mundos seguros configurados.
- Antiabuso de asesinatos: registra asesinatos repetidos dentro de un intervalo, advierte antes del límite y bloquea el daño PvP letal al alcanzar el máximo configurado.
- Mundos excluidos de las mecánicas de combate.
- Integración opcional con **AreaProtection 2.0.4**: respeta la bandera `pvp` de las áreas tanto para el atacante como para la víctima. También respeta el bypass del atacante en su propia área, sin permitir que ese bypass ignore la protección del área de la víctima.
- La integración con AreaProtection es opcional y no requiere añadirlo como dependencia de compilación.

## Comandos

| Comando | Función | Permiso |
|---|---|---|
| `/combat`, `/combat status`, `/pvpstatus`, `/tag` | Muestra el estado del PvP y del combate | `combatawm.command` |
| `/combat toggle`, `/pvp`, `/pvptoggle` | Activa o desactiva el PvP personal | `combatawm.command` |
| `/combat newbie`, `/newbie` | Muestra el tiempo restante de protección para jugadores nuevos | `combatawm.command` |
| `/combat newbie disable`, `/newbie disable` | Desactiva la protección para jugadores nuevos, si está permitido | `combatawm.command` |
| `/combat list`, `/pvplist` | Lista los jugadores conectados que tienen el PvP activado | `combatawm.command` |
| `/combat untag <jugador>` | Elimina la marca de combate de ambos jugadores | `combatawm.admin` |
| `/combat reload` | Recarga la configuración | `combatawm.admin` |

### Permisos de bypass

Por defecto, estos permisos están asignados a operadores:

- `combatawm.bypass.commands`: evita el bloqueo de comandos durante el combate.
- `combatawm.bypass.actions`: evita las restricciones de acciones durante el combate.
- `combatawm.bypass.borderhopping`: evita la restricción de teletransporte a mundos seguros.
- `combatawm.bypass.togglecooldown`: evita el tiempo de espera para cambiar el PvP.
- `combatawm.bypass.killabuse`: evita las restricciones contra el abuso de asesinatos.

## Configuración

Fragmento de las opciones principales de `config.yml`:

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

AreaProtectionIntegration:
  Enabled: true
  RespectBypass: true

ExcludedWorlds: []
```

El bloqueo de comandos está activo por defecto y bloquea los comandos configurados en `CombatTag.BlockCommands.List`. La protección de jugadores nuevos es opcional y viene desactivada por defecto. Las regiones indicadas en `AreaProtectionIntegration.CombatEntryBlockedAreas` impiden la entrada de jugadores marcados en combate, aunque sus banderas `pvp` estén en `true`.\n\nLos mensajes se pueden personalizar en la sección `Messages`. Las preferencias de PvP se guardan en `pvp.yml` y los tiempos de protección para jugadores nuevos se guardan en `protection.yml`.

### Integración con AreaProtection

1. Instala AreaProtection 2.0.4 en el servidor si deseas utilizar esta integración.
2. Mantén `AreaProtectionIntegration.Enabled: true` en la configuración de CombatAWM.
3. Configura la bandera `pvp` de cada área desde AreaProtection.
4. Si una de las dos posiciones (atacante o víctima) está en un área con `pvp: false`, CombatAWM cancela el daño y no aplica CombatTag.
5. Si AreaProtection no está instalado, CombatAWM continúa funcionando con normalidad.

Con `RespectBypass: true`, el bypass de AreaProtection permite al atacante ignorar la bandera PvP de su propia área. No permite ignorar la protección del área donde se encuentra la víctima.

## Instalación

1. Descarga `CombatAWM.jar` desde la sección de versiones o desde el artefacto de GitHub Actions.
2. Coloca el archivo en la carpeta `plugins` del servidor.
3. Inicia el servidor y edita `plugins/CombatAWM/config.yml`.
4. Ejecuta `/combat reload` o reinicia el servidor para aplicar los cambios.

## Compilación

Se utiliza Java 17. El flujo de GitHub Actions compila primero el código fuente de la API de Nukkit PetteriM1 Edition, lo instala en el repositorio local de Maven y después compila CombatAWM.

```bash
git clone --branch release https://github.com/BrunoJaegerK/NukkitPetteriM1Edition.git nukkit-server
mvn -f nukkit-server/pom.xml -DskipTests -Dmaven.test.skip=true install
mvn clean package
```

GitHub Actions publica `target/CombatAWM.jar` como artefacto al recibir cambios, solicitudes de integración o ejecuciones manuales.

## Notas

CombatAWM es un plugin nativo para Nukkit PetteriM1 Edition. La integración con AreaProtection se realiza de forma opcional mediante reflexión, por lo que no es una dependencia obligatoria para compilar o ejecutar el plugin. Prueba los cambios en un servidor de pruebas antes de instalarlos en producción.
