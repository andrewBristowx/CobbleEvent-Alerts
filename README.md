# CobbleEvent Alerts

Server-side Fabric addon for Cobblemon 1.7.3 on Minecraft 1.21.1.

## 0.1.0-alpha.2

CobbleEvent Alerts listens to Cobblemon's natural `POKEMON_ENTITY_SPAWN` event and alerts nearby players when a special wild Pokémon appears.

### Alerts

- **LEGENDARIO**: official `legendary` species plus `mythical` species by default, so Pokémon such as Deoxys are included.
- **SHINY**: any naturally spawned shiny Pokémon.
- **LEGENDARIO SHINY**: combined special alert when both conditions are true.
- Personalized player name in every message.
- Exact X/Y/Z coordinates when enabled.
- Clickable coordinates that can be copied from chat.
- Initial distance and cardinal direction.
- Optional dimension line.
- Separate configurable alert radii and sounds.
- UUID de-duplication guard to avoid repeated announcements for the same spawned entity.

Example:

```text
✦ LEGENDARIO ✦
Emilyextacy, a tu alrededor ha aparecido DEOXYS
⌖ Coordenadas: X: 152  Y: 71  Z: -438
↗ Distancia: 143 bloques • Noroeste
[ ✦ SEGUIR POKÉMON ✦ ]
```

### Owner-only live tracking

Cobblemon's natural `PlayerSpawner` records the player who caused the spawn attempt. CobbleEvent Alerts uses that exact player as the tracking owner.

- Everyone inside the alert radius receives the message and coordinates.
- Only the player whose spawn cycle produced the Pokémon receives `[ ✦ SEGUIR POKÉMON ✦ ]`.
- The tracking command validates ownership server-side, so another player cannot type the command manually to steal access.
- If a custom/addon spawner does not expose a player cause, no player receives private tracking access.

While tracking, the owner's Action Bar updates with the Pokémon's real current position:

```text
✦ DEOXYS ✦  ↖  138 bloques
```

The arrow is relative to the direction the player is currently looking. The tracker follows the exact announced Pokémon UUID, so movement is reflected in the distance and direction.

Public player commands:

```text
/cobbleeventalerts track <uuid>
/cobbleeventalerts untrack
```

Normally the player starts tracking by clicking the chat button rather than typing the UUID manually.

### Capture and disappearance

Previously announced Pokémon are kept in a lightweight server-side registry. When one is captured, the original alert recipients are told who captured it. If it disappears and remains unavailable beyond the configured grace period, the alert is closed and active tracking ends.

## Admin test commands

Requires permission level 2:

```text
/cobbleeventalerts test legendary
/cobbleeventalerts test shiny
/cobbleeventalerts test legendary_shiny
/cobbleeventalerts reload
```

The test commands only preview the message and sound for the executing admin; they do not spawn a Pokémon.

## Configuration

Generated automatically at:

```text
config/cobbleeventalerts.json
```

Important defaults:

```json
{
  "enabled": true,
  "legendaryAlerts": true,
  "shinyAlerts": true,
  "includeMythicalsAsLegendary": true,
  "legendaryRadiusBlocks": 256,
  "shinyRadiusBlocks": 192,
  "showCoordinates": true,
  "clickableCoordinates": true,
  "showDistanceAndDirection": true,
  "showDimension": false,
  "trackingEnabled": true,
  "trackingUpdateIntervalTicks": 20,
  "maxTrackingMinutes": 10,
  "disappearanceGraceSeconds": 15,
  "announceCapture": true,
  "announceDisappearance": true,
  "legendarySound": "minecraft:ui.toast.challenge_complete",
  "shinySound": "minecraft:block.amethyst_block.chime",
  "legendaryShinySound": "minecraft:ui.toast.challenge_complete",
  "soundVolume": 0.9,
  "soundPitch": 1.0
}
```

This mod is intended to be server-side; clients do not need CobbleEvent Alerts installed.
