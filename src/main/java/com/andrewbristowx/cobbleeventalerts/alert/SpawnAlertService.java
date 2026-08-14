package com.andrewbristowx.cobbleeventalerts.alert;

import com.andrewbristowx.cobbleeventalerts.CobbleEventAlerts;
import com.andrewbristowx.cobbleeventalerts.config.AlertsConfig;
import com.andrewbristowx.cobbleeventalerts.tracking.TrackingService;
import com.cobblemon.mod.common.api.events.entity.SpawnEvent;
import com.cobblemon.mod.common.api.pokemon.labels.CobblemonPokemonLabels;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SpawnAlertService {
    private static final int MAX_REMEMBERED_SPAWNS = 4096;
    private static final Map<UUID, Boolean> ANNOUNCED = new LinkedHashMap<>();

    private SpawnAlertService() {
    }

    public static void handleSpawn(SpawnEvent<PokemonEntity> event) {
        AlertsConfig config = AlertsConfig.get();
        if (!config.enabled || event.isCanceled()) {
            return;
        }

        PokemonEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        Pokemon pokemon = entity.getPokemon();
        Set<String> labels = pokemon.getSpecies().getLabels();

        boolean legendary = labels.contains(CobblemonPokemonLabels.LEGENDARY)
                || (config.includeMythicalsAsLegendary && labels.contains(CobblemonPokemonLabels.MYTHICAL));
        boolean shiny = pokemon.getShiny();

        if ((!legendary || !config.legendaryAlerts) && (!shiny || !config.shinyAlerts)) {
            return;
        }

        AlertKind kind;
        if (legendary && config.legendaryAlerts && shiny && config.shinyAlerts) {
            kind = AlertKind.LEGENDARY_SHINY;
        } else if (legendary && config.legendaryAlerts) {
            kind = AlertKind.LEGENDARY;
        } else if (shiny && config.shinyAlerts) {
            kind = AlertKind.SHINY;
        } else {
            return;
        }

        if (!markAnnounced(entity.getUUID())) {
            return;
        }

        int radius = switch (kind) {
            case LEGENDARY -> config.legendaryRadiusBlocks;
            case SHINY -> config.shinyRadiusBlocks;
            case LEGENDARY_SHINY -> Math.max(config.legendaryRadiusBlocks, config.shinyRadiusBlocks);
        };

        String pokemonName = pokemon.getSpecies().getName().toUpperCase(Locale.ROOT);
        BlockPos pos = entity.blockPosition();
        double radiusSquared = (double) radius * radius;

        List<ServerPlayer> recipients = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(entity) <= radiusSquared) {
                recipients.add(player);
            }
        }

        if (recipients.isEmpty()) {
            return;
        }

        ServerPlayer trackerOwner = resolveTrackerOwner(event, recipients);
        TrackingService.register(entity, kind, pokemonName, recipients, trackerOwner);

        for (ServerPlayer player : recipients) {
            boolean canTrack = trackerOwner != null && player.getUUID().equals(trackerOwner.getUUID());
            sendAlert(player, kind, pokemonName, pos, level, entity.getUUID(), canTrack);
        }

        CobbleEventAlerts.LOGGER.info(
                "Sent {} alert for {} at {} {} {} to {} nearby player(s); tracker owner={}",
                kind,
                pokemonName,
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                recipients.size(),
                trackerOwner == null ? "none" : trackerOwner.getGameProfile().getName()
        );
    }

    public static void sendTest(ServerPlayer player, AlertKind kind) {
        String pokemonName = switch (kind) {
            case LEGENDARY -> "DEOXYS";
            case SHINY -> "EEVEE";
            case LEGENDARY_SHINY -> "RAYQUAZA";
        };
        sendAlert(player, kind, pokemonName, player.blockPosition(), player.serverLevel(), null, false);
    }

    private static ServerPlayer resolveTrackerOwner(
            SpawnEvent<PokemonEntity> event,
            List<ServerPlayer> recipients
    ) {
        if (!AlertsConfig.get().trackingEnabled) {
            return null;
        }

        Entity causeEntity = event.getSpawnablePosition().getCause().getEntity();
        if (!(causeEntity instanceof ServerPlayer causePlayer)) {
            return null;
        }

        for (ServerPlayer recipient : recipients) {
            if (recipient.getUUID().equals(causePlayer.getUUID())) {
                return causePlayer;
            }
        }
        return null;
    }

    private static void sendAlert(
            ServerPlayer player,
            AlertKind kind,
            String pokemonName,
            BlockPos pos,
            ServerLevel level,
            UUID pokemonUuid,
            boolean canTrack
    ) {
        AlertsConfig config = AlertsConfig.get();

        player.sendSystemMessage(title(kind));

        MutableComponent body = Component.literal(player.getName().getString())
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.literal(", a tu alrededor ha aparecido ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(pokemonName).withStyle(nameColor(kind), ChatFormatting.BOLD));
        player.sendSystemMessage(body);

        if (config.showCoordinates) {
            String coordinatesText = "X: " + pos.getX() + "  Y: " + pos.getY() + "  Z: " + pos.getZ();
            MutableComponent coordinates = Component.literal("⌖ Coordenadas: ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(coordinatesText).withStyle(style -> {
                        style = style.withColor(ChatFormatting.WHITE);
                        if (config.clickableCoordinates) {
                            style = style.withClickEvent(new ClickEvent(
                                    ClickEvent.Action.COPY_TO_CLIPBOARD,
                                    pos.getX() + " " + pos.getY() + " " + pos.getZ()
                            ));
                            style = style.withHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Haz clic para copiar las coordenadas")
                            ));
                        }
                        return style;
                    }));
            player.sendSystemMessage(coordinates);
        }

        if (config.showDistanceAndDirection) {
            int distance = (int) Math.round(Math.sqrt(player.distanceToSqr(
                    pos.getX() + 0.5D,
                    pos.getY() + 0.5D,
                    pos.getZ() + 0.5D
            )));
            player.sendSystemMessage(
                    Component.literal("↗ Distancia: ")
                            .withStyle(ChatFormatting.DARK_GRAY)
                            .append(Component.literal(distance + " bloques • " + cardinalDirection(player, pos))
                                    .withStyle(ChatFormatting.YELLOW))
            );
        }

        if (config.showDimension) {
            player.sendSystemMessage(
                    Component.literal("Mundo: ")
                            .withStyle(ChatFormatting.DARK_GRAY)
                            .append(Component.literal(level.dimension().location().toString()).withStyle(ChatFormatting.GRAY))
            );
        }

        if (canTrack && pokemonUuid != null) {
            player.sendSystemMessage(
                    Component.literal("[ ✦ SEGUIR POKÉMON ✦ ]")
                            .withStyle(style -> style
                                    .withColor(ChatFormatting.GREEN)
                                    .withBold(true)
                                    .withClickEvent(new ClickEvent(
                                            ClickEvent.Action.RUN_COMMAND,
                                            "/cobbleeventalerts track " + pokemonUuid
                                    ))
                                    .withHoverEvent(new HoverEvent(
                                            HoverEvent.Action.SHOW_TEXT,
                                            Component.literal("Solo tú puedes activar este seguimiento")
                                    )))
            );
        }

        SoundEvent sound = resolveSound(kind, config);
        player.playNotifySound(sound, SoundSource.MASTER, config.soundVolume, config.soundPitch);
    }

    private static String cardinalDirection(ServerPlayer player, BlockPos target) {
        double dx = target.getX() + 0.5D - player.getX();
        double dz = target.getZ() + 0.5D - player.getZ();
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        angle = (angle + 360.0D) % 360.0D;

        if (angle < 22.5D || angle >= 337.5D) return "Norte";
        if (angle < 67.5D) return "Noreste";
        if (angle < 112.5D) return "Este";
        if (angle < 157.5D) return "Sureste";
        if (angle < 202.5D) return "Sur";
        if (angle < 247.5D) return "Suroeste";
        if (angle < 292.5D) return "Oeste";
        return "Noroeste";
    }

    private static MutableComponent title(AlertKind kind) {
        return switch (kind) {
            case LEGENDARY -> Component.literal("✦ LEGENDARIO ✦")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            case SHINY -> Component.literal("✨ SHINY ✨")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
            case LEGENDARY_SHINY -> Component.literal("✦✨ LEGENDARIO SHINY ✨✦")
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
        };
    }

    private static ChatFormatting nameColor(AlertKind kind) {
        return switch (kind) {
            case LEGENDARY -> ChatFormatting.YELLOW;
            case SHINY -> ChatFormatting.AQUA;
            case LEGENDARY_SHINY -> ChatFormatting.LIGHT_PURPLE;
        };
    }

    private static SoundEvent resolveSound(AlertKind kind, AlertsConfig config) {
        String configured = switch (kind) {
            case LEGENDARY -> config.legendarySound;
            case SHINY -> config.shinySound;
            case LEGENDARY_SHINY -> config.legendaryShinySound;
        };

        ResourceLocation id = ResourceLocation.tryParse(configured);
        if (id != null) {
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(id);
            if (sound != null) {
                return sound;
            }
        }

        return SoundEvents.EXPERIENCE_ORB_PICKUP;
    }

    private static synchronized boolean markAnnounced(UUID uuid) {
        if (ANNOUNCED.containsKey(uuid)) {
            return false;
        }

        ANNOUNCED.put(uuid, Boolean.TRUE);
        if (ANNOUNCED.size() > MAX_REMEMBERED_SPAWNS) {
            UUID oldest = ANNOUNCED.keySet().iterator().next();
            ANNOUNCED.remove(oldest);
        }
        return true;
    }
}
