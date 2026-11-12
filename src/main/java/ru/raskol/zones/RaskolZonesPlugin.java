package ru.raskol.zones;

import org.bukkit.plugin.java.JavaPlugin;
import ru.raskol.zones.foundation.FoundationRuntime;
import ru.raskol.zones.foundation.config.ConfigurationService;
import ru.raskol.zones.foundation.health.HealthService;

public final class RaskolZonesPlugin extends JavaPlugin {

    private FoundationRuntime runtime;

    @Override
    public void onEnable() {
        var health = new HealthService();
        runtime = new FoundationRuntime(
                new ConfigurationService(this),
                health,
                getLogger()
        );

        try {
            runtime.start();
            getLogger().info("RaskolZones foundation готов. Состояние: " + health.snapshot().status());
        } catch (RuntimeException exception) {
            health.markFailed(exception.getMessage());
            getLogger().severe("Не удалось запустить RaskolZones: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (runtime != null) {
            runtime.stop();
        }
    }
}
