package ru.raskol.zones.foundation.config;

import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigurationService implements SettingsProvider {

    private final JavaPlugin plugin;

    public ConfigurationService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public PluginSettings load() {
        plugin.saveDefaultConfig();
        return readCurrentConfiguration();
    }

    @Override
    public PluginSettings reload() {
        plugin.reloadConfig();
        return readCurrentConfiguration();
    }

    private PluginSettings readCurrentConfiguration() {
        FileConfiguration config = plugin.getConfig();
        Object language = config.get("language", "ru");
        Object diagnostics = config.get("diagnostics.enabled", false);

        return PluginSettings.from(Map.of(
                "language", language,
                "diagnostics.enabled", diagnostics
        ));
    }
}
