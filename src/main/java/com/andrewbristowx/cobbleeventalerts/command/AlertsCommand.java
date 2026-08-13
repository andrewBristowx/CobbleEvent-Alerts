package com.andrewbristowx.cobbleeventalerts.command;

import com.andrewbristowx.cobbleeventalerts.alert.AlertKind;
import com.andrewbristowx.cobbleeventalerts.alert.SpawnAlertService;
import com.andrewbristowx.cobbleeventalerts.config.AlertsConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class AlertsCommand {
    private AlertsCommand() {
    }

    public static void register(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("cobbleeventalerts")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("test")
                                .then(Commands.literal("legendary")
                                        .executes(context -> test(context.getSource().getPlayerOrException(), AlertKind.LEGENDARY)))
                                .then(Commands.literal("shiny")
                                        .executes(context -> test(context.getSource().getPlayerOrException(), AlertKind.SHINY)))
                                .then(Commands.literal("legendary_shiny")
                                        .executes(context -> test(context.getSource().getPlayerOrException(), AlertKind.LEGENDARY_SHINY))))
                        .then(Commands.literal("reload")
                                .executes(context -> {
                                    AlertsConfig.reload();
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("CobbleEvent Alerts: configuración recargada.")
                                                    .withStyle(ChatFormatting.GREEN),
                                            false
                                    );
                                    return 1;
                                }))
        );
    }

    private static int test(net.minecraft.server.level.ServerPlayer player, AlertKind kind) {
        SpawnAlertService.sendTest(player, kind);
        return 1;
    }
}
