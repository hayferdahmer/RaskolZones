package ru.raskol.zones.foundation.config;

import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigurationService {

    private final JavaPlugin plugin;

    public ConfigurationService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public PluginSettings load() {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();

        return PluginSettings.from(Map.of(
                "language", config.getString("language", "ru"),
                "diagnostics.enabled", config.getBoolean("diagnostics.enabled", false)
        ));
    }
}
