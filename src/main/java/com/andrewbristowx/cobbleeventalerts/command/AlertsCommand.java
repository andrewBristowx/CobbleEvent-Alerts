package com.andrewbristowx.cobbleeventalerts.command;

import com.andrewbristowx.cobbleeventalerts.alert.AlertKind;
import com.andrewbristowx.cobbleeventalerts.alert.SpawnAlertService;
import com.andrewbristowx.cobbleeventalerts.config.AlertsConfig;
import com.andrewbristowx.cobbleeventalerts.tracking.TrackingService;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public final class AlertsCommand {
    private AlertsCommand() {}

    public static void register(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("cobbleeventalerts")
                        .then(Commands.literal("track")
                                .then(Commands.argument("pokemon", StringArgumentType.word())
                                        .executes(context -> track(
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "pokemon")
                                        ))))
                        .then(Commands.literal("untrack")
                                .executes(context -> TrackingService.stopTracking(
                                        context.getSource().getPlayerOrException(), true)))
                        .then(Commands.literal("test")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("legendary")
                                        .executes(context -> test(context.getSource().getPlayerOrException(), AlertKind.LEGENDARY)))
                                .then(Commands.literal("shiny")
                                        .executes(context -> test(context.getSource().getPlayerOrException(), AlertKind.SHINY)))
                                .then(Commands.literal("legendary_shiny")
                                        .executes(context -> test(context.getSource().getPlayerOrException(), AlertKind.LEGENDARY_SHINY))))
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    AlertsConfig.reload();
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("CobbleEvent Alerts: configuración recargada.")
                                                    .withStyle(ChatFormatting.GREEN), false);
                                    return 1;
                                }))
        );
    }

    private static int track(net.minecraft.server.level.ServerPlayer player, String value) {
        try {
            return TrackingService.startTracking(player, UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            player.sendSystemMessage(Component.literal("Identificador de seguimiento inválido.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int test(net.minecraft.server.level.ServerPlayer player, AlertKind kind) {
        SpawnAlertService.sendTest(player, kind);
        return 1;
    }
}
