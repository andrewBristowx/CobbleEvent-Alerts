package com.andrewbristowx.cobbleeventalerts.config;

import com.andrewbristowx.cobbleeventalerts.CobbleEventAlerts;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AlertsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("cobbleeventalerts.json");

    private static AlertsConfig INSTANCE = new AlertsConfig();

    public boolean enabled = true;
    public boolean legendaryAlerts = true;
    public boolean shinyAlerts = true;

    /** Treat official mythical labels such as Deoxys as LEGENDARIO alerts too. */
    public boolean includeMythicalsAsLegendary = true;

    public int legendaryRadiusBlocks = 256;
    public int shinyRadiusBlocks = 192;

    public boolean showCoordinates = true;
    public boolean clickableCoordinates = true;
    public boolean showDistanceAndDirection = true;
    public boolean showDimension = false;

    /**
     * Tracking is intentionally restricted in code to the PlayerSpawner cause player.
     * Other nearby recipients still get the normal alert and coordinates.
     */
    public boolean trackingEnabled = true;
    public int trackingUpdateIntervalTicks = 20;
    public int maxTrackingMinutes = 10;
    public int disappearanceGraceSeconds = 15;
    public boolean announceCapture = true;
    public boolean announceDisappearance = true;

    public String legendarySound = "minecraft:ui.toast.challenge_complete";
    public String shinySound = "minecraft:block.amethyst_block.chime";
    public String legendaryShinySound = "minecraft:ui.toast.challenge_complete";

    public float soundVolume = 0.9F;
    public float soundPitch = 1.0F;

    public static AlertsConfig get() {
        return INSTANCE;
    }

    public static void load() {
        AlertsConfig loaded = new AlertsConfig();

        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                AlertsConfig parsed = GSON.fromJson(reader, AlertsConfig.class);
                if (parsed != null) {
                    loaded = parsed;
                }
            } catch (Exception exception) {
                CobbleEventAlerts.LOGGER.error("Could not read {}. Using safe defaults.", CONFIG_PATH, exception);
            }
        }

        loaded.normalize();
        INSTANCE = loaded;
        save();
    }

    public static void reload() {
        load();
    }

    private void normalize() {
        legendaryRadiusBlocks = Math.max(16, Math.min(legendaryRadiusBlocks, 4096));
        shinyRadiusBlocks = Math.max(16, Math.min(shinyRadiusBlocks, 4096));
        trackingUpdateIntervalTicks = Math.max(10, Math.min(trackingUpdateIntervalTicks, 200));
        maxTrackingMinutes = Math.max(1, Math.min(maxTrackingMinutes, 60));
        disappearanceGraceSeconds = Math.max(5, Math.min(disappearanceGraceSeconds, 120));
        soundVolume = Math.max(0.0F, Math.min(soundVolume, 4.0F));
        soundPitch = Math.max(0.5F, Math.min(soundPitch, 2.0F));

        if (legendarySound == null || legendarySound.isBlank()) {
            legendarySound = "minecraft:ui.toast.challenge_complete";
        }
        if (shinySound == null || shinySound.isBlank()) {
            shinySound = "minecraft:block.amethyst_block.chime";
        }
        if (legendaryShinySound == null || legendaryShinySound.isBlank()) {
            legendaryShinySound = "minecraft:ui.toast.challenge_complete";
        }
    }

    private static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException exception) {
            CobbleEventAlerts.LOGGER.error("Could not write {}", CONFIG_PATH, exception);
        }
    }
}
