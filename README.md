# CobbleEvent Alerts

Server-side Fabric addon for Cobblemon 1.7.3 on Minecraft 1.21.1.

## 0.1.0-alpha.1

The first alpha listens to Cobblemon's natural `POKEMON_ENTITY_SPAWN` event and alerts only nearby players when a special wild Pokémon appears.

### Alerts

- **LEGENDARIO**: official `legendary` species plus `mythical` species by default, so Pokémon such as Deoxys are included.
- **SHINY**: any naturally spawned shiny Pokémon.
- **LEGENDARIO SHINY**: combined special alert when both conditions are true.
- Personalized player name in every message.
- Exact X/Y/Z coordinates when enabled.
- Optional dimension line.
- Separate configurable alert radii.
- Separate configurable notification sounds.
- UUID de-duplication guard to avoid repeated announcements for the same spawned entity.

Example:

```text
✦ LEGENDARIO ✦
Emilyextacy, a tu alrededor ha aparecido DEOXYS
⌖ Coordenadas: X: 152  Y: 71  Z: -438
```

## Test commands

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
  "showDimension": false,
  "legendarySound": "minecraft:ui.toast.challenge_complete",
  "shinySound": "minecraft:block.amethyst_block.chime",
  "legendaryShinySound": "minecraft:ui.toast.challenge_complete",
  "soundVolume": 0.9,
  "soundPitch": 1.0
}
```

This mod is intended to be server-side; clients do not need CobbleEvent Alerts installed.
