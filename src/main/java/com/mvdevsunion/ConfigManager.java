package com.mvdevsunion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("mvdevsunionbettersmp.json");

    public static ModConfig load() {
        if (!Files.exists(CONFIG_PATH)) {
            ModConfig defaults = new ModConfig();
            save(defaults);
            return defaults;
        }
        try {
            String json = Files.readString(CONFIG_PATH);
            ModConfig config = GSON.fromJson(json, ModConfig.class);
            return config != null ? config : new ModConfig();
        } catch (IOException e) {
            MvDevsUnionBetterSMP.LOGGER.error("Failed to load config, using defaults", e);
            return new ModConfig();
        }
    }

    public static void save(ModConfig config) {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(config));
        } catch (IOException e) {
            MvDevsUnionBetterSMP.LOGGER.error("Failed to save config", e);
        }
    }
}
