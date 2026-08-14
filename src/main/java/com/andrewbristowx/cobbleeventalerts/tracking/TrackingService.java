package com.andrewbristowx.cobbleeventalerts.tracking;

import com.andrewbristowx.cobbleeventalerts.CobbleEventAlerts;
import com.andrewbristowx.cobbleeventalerts.alert.AlertKind;
import com.andrewbristowx.cobbleeventalerts.config.AlertsConfig;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TrackingService {
    private static final Map<UUID, ActiveAlert> ACTIVE_ALERTS = new HashMap<>();
    private static final Map<UUID, PlayerTrack> PLAYER_TRACKS = new HashMap<>();
    private static long serverTicks;

    private TrackingService() {
    }

    public static void register(
            PokemonEntity entity,
            AlertKind kind,
            String pokemonName,
            Collection<ServerPlayer> recipients,
            ServerPlayer trackerOwner
    ) {
        Set<UUID> recipientIds = new HashSet<>();
        for (ServerPlayer recipient : recipients) {
            recipientIds.add(recipient.getUUID());
        }

        UUID trackerOwnerId = trackerOwner == null ? null : trackerOwner.getUUID();
        ACTIVE_ALERTS.put(
                entity.getUUID(),
                new ActiveAlert(
                        entity.getUUID(),
                        entity.level().dimension(),
                        pokemonName,
                        kind,
                        trackerOwnerId,
                        recipientIds,
                        entity.blockPosition(),
                        0
                )
        );
    }

    public static boolean canTrack(ServerPlayer player, UUID pokemonUuid) {
        ActiveAlert alert = ACTIVE_ALERTS.get(pokemonUuid);
        return alert != null
                && AlertsConfig.get().trackingEnabled
                && player.getUUID().equals(alert.trackerOwner());
    }

    public static int startTracking(ServerPlayer player, UUID pokemonUuid) {
        AlertsConfig config = AlertsConfig.get();
        ActiveAlert alert = ACTIVE_ALERTS.get(pokemonUuid);
        if (!config.trackingEnabled || alert == null || !player.getUUID().equals(alert.trackerOwner())) {
            player.sendSystemMessage(
                    Component.literal("No tienes permiso para seguir este Pokémon.")
                            .withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        ServerLevel level = player.server.getLevel(alert.dimension());
        Entity entity = level == null ? null : level.getEntity(pokemonUuid);
        if (!(entity instanceof PokemonEntity) || entity.isRemoved()) {
            player.sendSystemMessage(
                    Component.literal("Ese Pokémon ya no está disponible para seguimiento.")
                            .withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        PLAYER_TRACKS.put(player.getUUID(), new PlayerTrack(pokemonUuid, serverTicks));
        player.sendSystemMessage(
                Component.literal("✦ Seguimiento iniciado: ")
                        .withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(alert.pokemonName()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                        .append(Component.literal(". Usa /cobbleeventalerts untrack para detenerlo.")
                                .withStyle(ChatFormatting.GRAY))
        );
        updateActionBar(player, alert, entity);
        return 1;
    }

    public static int stopTracking(ServerPlayer player, boolean notify) {
        PlayerTrack removed = PLAYER_TRACKS.remove(player.getUUID());
        if (removed == null) {
            if (notify) {
                player.sendSystemMessage(Component.literal("No estás siguiendo ningún Pokémon.").withStyle(ChatFormatting.GRAY));
            }
            return 0;
        }

        player.displayClientMessage(Component.empty(), true);
        if (notify) {
            player.sendSystemMessage(Component.literal("Seguimiento detenido.").withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }

    public static void handleCaptured(PokemonCapturedEvent event) {
        UUID pokemonUuid = event.getPokemon().getUuid();
        ActiveAlert alert = ACTIVE_ALERTS.remove(pokemonUuid);
        if (alert == null) {
            return;
        }

        clearTrackersFor(pokemonUuid, "El Pokémon fue capturado.");

        if (AlertsConfig.get().announceCapture) {
            MinecraftServer server = event.getPlayer().server;
            for (UUID recipientId : alert.recipients()) {
                ServerPlayer recipient = server.getPlayerList().getPlayer(recipientId);
                if (recipient == null) {
                    continue;
                }
                recipient.sendSystemMessage(
                        Component.literal("✦ " + alert.pokemonName() + " CAPTURADO ✦")
                                .withStyle(alert.kind() == AlertKind.SHINY ? ChatFormatting.AQUA : ChatFormatting.GOLD, ChatFormatting.BOLD)
                );
                recipient.sendSystemMessage(
                        Component.literal(alert.pokemonName())
                                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                                .append(Component.literal(" ha sido capturado por ").withStyle(ChatFormatting.GRAY))
                                .append(event.getPlayer().getName().copy().withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                );
            }
        }

        CobbleEventAlerts.LOGGER.info(
                "Tracked alert resolved by capture: {} {} captured by {}",
                alert.kind(),
                alert.pokemonName(),
                event.getPlayer().getGameProfile().getName()
        );
    }

    public static void tick(MinecraftServer server) {
        serverTicks++;
        AlertsConfig config = AlertsConfig.get();
        if (!config.trackingEnabled && ACTIVE_ALERTS.isEmpty()) {
            return;
        }

        int interval = Math.max(10, config.trackingUpdateIntervalTicks);
        if (serverTicks % interval != 0L) {
            return;
        }

        Iterator<Map.Entry<UUID, ActiveAlert>> alertIterator = ACTIVE_ALERTS.entrySet().iterator();
        while (alertIterator.hasNext()) {
            Map.Entry<UUID, ActiveAlert> entry = alertIterator.next();
            ActiveAlert alert = entry.getValue();
            ServerLevel level = server.getLevel(alert.dimension());
            Entity entity = level == null ? null : level.getEntity(alert.pokemonUuid());

            if (entity instanceof PokemonEntity && !entity.isRemoved()) {
                alert.lastKnownPosition = entity.blockPosition();
                alert.missingTicks = 0;
            } else {
                alert.missingTicks += interval;
                int graceTicks = Math.max(20, config.disappearanceGraceSeconds * 20);
                if (alert.missingTicks >= graceTicks) {
                    alertIterator.remove();
                    finishDisappeared(server, alert);
                }
            }
        }

        Iterator<Map.Entry<UUID, PlayerTrack>> trackIterator = PLAYER_TRACKS.entrySet().iterator();
        while (trackIterator.hasNext()) {
            Map.Entry<UUID, PlayerTrack> entry = trackIterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            PlayerTrack track = entry.getValue();
            ActiveAlert alert = ACTIVE_ALERTS.get(track.pokemonUuid());

            if (player == null || alert == null) {
                trackIterator.remove();
                continue;
            }

            long maxTicks = (long) Math.max(1, config.maxTrackingMinutes) * 60L * 20L;
            if (serverTicks - track.startedAtTick() >= maxTicks) {
                trackIterator.remove();
                player.displayClientMessage(Component.empty(), true);
                player.sendSystemMessage(
                        Component.literal("El seguimiento de " + alert.pokemonName() + " terminó por tiempo.")
                                .withStyle(ChatFormatting.YELLOW)
                );
                continue;
            }

            ServerLevel level = server.getLevel(alert.dimension());
            Entity entity = level == null ? null : level.getEntity(alert.pokemonUuid());
            if (entity == null || entity.isRemoved()) {
                continue;
            }

            updateActionBar(player, alert, entity);
        }
    }

    private static void updateActionBar(ServerPlayer player, ActiveAlert alert, Entity entity) {
        if (player.level().dimension() != alert.dimension()) {
            player.displayClientMessage(
                    Component.literal("✦ " + alert.pokemonName() + " ✦  otro mundo")
                            .withStyle(ChatFormatting.GRAY),
                    true
            );
            return;
        }

        double dx = entity.getX() - player.getX();
        double dz = entity.getZ() - player.getZ();
        int distance = (int) Math.round(Math.sqrt(player.distanceToSqr(entity)));
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        float relativeYaw = Mth.wrapDegrees((float) targetYaw - player.getYRot());
        String arrow = relativeArrow(relativeYaw);

        Component actionBar = Component.literal("✦ " + alert.pokemonName() + " ✦ ")
                .withStyle(alert.kind() == AlertKind.SHINY ? ChatFormatting.AQUA : ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(arrow + "  ").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal(distance + " bloques").withStyle(
                        distance <= 30 ? ChatFormatting.GREEN : ChatFormatting.YELLOW
                ));
        player.displayClientMessage(actionBar, true);
    }

    private static String relativeArrow(float angle) {
        float abs = Math.abs(angle);
        if (abs <= 22.5F) {
            return "↑";
        }
        if (abs >= 157.5F) {
            return "↓";
        }
        if (angle > 0.0F) {
            if (angle <= 67.5F) {
                return "↗";
            }
            if (angle <= 112.5F) {
                return "→";
            }
            return "↘";
        }
        if (angle >= -67.5F) {
            return "↖";
        }
        if (angle >= -112.5F) {
            return "←";
        }
        return "↙";
    }

    private static void finishDisappeared(MinecraftServer server, ActiveAlert alert) {
        clearTrackersFor(alert.pokemonUuid(), "El Pokémon ya no está disponible.");

        if (AlertsConfig.get().announceDisappearance) {
            for (UUID recipientId : alert.recipients()) {
                ServerPlayer recipient = server.getPlayerList().getPlayer(recipientId);
                if (recipient != null) {
                    recipient.sendSystemMessage(
                            Component.literal("✦ " + alert.pokemonName() + " ha desaparecido ✦")
                                    .withStyle(ChatFormatting.DARK_GRAY)
                    );
                }
            }
        }

        CobbleEventAlerts.LOGGER.info(
                "Tracked alert expired/disappeared: {} {} last seen at {} {} {}",
                alert.kind(),
                alert.pokemonName(),
                alert.lastKnownPosition.getX(),
                alert.lastKnownPosition.getY(),
                alert.lastKnownPosition.getZ()
        );
    }

    private static void clearTrackersFor(UUID pokemonUuid, String reason) {
        Iterator<Map.Entry<UUID, PlayerTrack>> iterator = PLAYER_TRACKS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PlayerTrack> entry = iterator.next();
            if (!entry.getValue().pokemonUuid().equals(pokemonUuid)) {
                continue;
            }
            iterator.remove();
        }
    }

    public static BlockPos currentOrLastPosition(UUID pokemonUuid) {
        ActiveAlert alert = ACTIVE_ALERTS.get(pokemonUuid);
        return alert == null ? null : alert.lastKnownPosition;
    }

    private static final class ActiveAlert {
        private final UUID pokemonUuid;
        private final ResourceKey<Level> dimension;
        private final String pokemonName;
        private final AlertKind kind;
        private final UUID trackerOwner;
        private final Set<UUID> recipients;
        private BlockPos lastKnownPosition;
        private int missingTicks;

        private ActiveAlert(
                UUID pokemonUuid,
                ResourceKey<Level> dimension,
                String pokemonName,
                AlertKind kind,
                UUID trackerOwner,
                Set<UUID> recipients,
                BlockPos lastKnownPosition,
                int missingTicks
        ) {
            this.pokemonUuid = pokemonUuid;
            this.dimension = dimension;
            this.pokemonName = pokemonName;
            this.kind = kind;
            this.trackerOwner = trackerOwner;
            this.recipients = Set.copyOf(recipients);
            this.lastKnownPosition = lastKnownPosition;
            this.missingTicks = missingTicks;
        }

        private UUID pokemonUuid() {
            return pokemonUuid;
        }

        private ResourceKey<Level> dimension() {
            return dimension;
        }

        private String pokemonName() {
            return pokemonName;
        }

        private AlertKind kind() {
            return kind;
        }

        private UUID trackerOwner() {
            return trackerOwner;
        }

        private Set<UUID> recipients() {
            return recipients;
        }
    }

    private record PlayerTrack(UUID pokemonUuid, long startedAtTick) {
    }
}
