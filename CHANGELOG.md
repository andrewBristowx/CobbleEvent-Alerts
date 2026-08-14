# Changelog

## 0.1.0-alpha.3

- Fixes announced Pokémon being reported as disappeared after a successful capture.
- Tracks Cobblemon's persistent Pokémon UUID separately from the temporary Minecraft entity UUID.
- Uses the persistent Pokémon UUID for `POKEMON_CAPTURED` matching and tracker commands.
- Keeps the entity UUID exclusively for live world position lookup.
- Clears the active Action Bar immediately when the announced Pokémon is captured.

## 0.1.0-alpha.2

- Adds owner-only live tracking for special wild Pokémon.
- Uses Cobblemon's natural PlayerSpawner cause to identify the exact player whose spawn cycle created the Pokémon.
- All nearby recipients still receive the normal alert and exact coordinates, but only the spawn-owner receives the clickable tracking button.
- Adds `/cobbleeventalerts track <uuid>` with server-side ownership validation and `/cobbleeventalerts untrack`.
- Adds a live Action Bar tracker with relative direction arrow and current distance.
- Adds initial distance and cardinal direction to alerts.
- Makes coordinates clickable/copyable when enabled.
- Adds capture resolution messages for previously announced Pokémon.
- Adds disappearance handling with a configurable grace period.
- Adds configurable tracking duration and update interval.
- Logs tracker owner, capture resolution and disappearance details to the server log.

## 0.1.0-alpha.1

- Adds nearby personalized alerts for naturally spawned legendary Pokémon.
- Treats Cobblemon mythical labels as LEGENDARIO by default so Pokémon such as Deoxys are included.
- Adds nearby personalized alerts for shiny Pokémon.
- Adds a combined LEGENDARIO SHINY alert.
- Shows exact spawn coordinates by default.
- Adds configurable legendary and shiny alert radii.
- Adds configurable notification sounds, volume and pitch.
- Adds optional dimension display.
- Adds a bounded UUID de-duplication guard.
- Adds admin preview commands for all three alert types.
- Adds runtime config reload command.
