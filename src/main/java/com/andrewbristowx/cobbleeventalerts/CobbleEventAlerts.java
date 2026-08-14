package com.andrewbristowx.cobbleeventalerts;

import com.andrewbristowx.cobbleeventalerts.alert.SpawnAlertService;
import com.andrewbristowx.cobbleeventalerts.command.AlertsCommand;
import com.andrewbristowx.cobbleeventalerts.config.AlertsConfig;
import com.andrewbristowx.cobbleeventalerts.tracking.TrackingService;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import kotlin.Unit;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CobbleEventAlerts implements ModInitializer {
    public static final String MOD_ID = "cobbleeventalerts";
    public static final Logger LOGGER = LoggerFactory.getLogger("CobbleEvent Alerts");

    @Override
    public void onInitialize() {
        AlertsConfig.load();

        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(Priority.LOWEST, event -> {
            SpawnAlertService.handleSpawn(event);
            return Unit.INSTANCE;
        });

        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.LOWEST, event -> {
            TrackingService.handleCaptured(event);
            return Unit.INSTANCE;
        });

        ServerTickEvents.END_SERVER_TICK.register(TrackingService::tick);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                AlertsCommand.register(dispatcher)
        );

        LOGGER.info("CobbleEvent Alerts 0.1.0-alpha.3 enabled: capture UUID mapping hotfix active.");
    }
}
