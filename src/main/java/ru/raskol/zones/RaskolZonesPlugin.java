package ru.raskol.zones;

import java.nio.file.Path;
import java.util.concurrent.CompletionException;
import org.bukkit.plugin.java.JavaPlugin;
import ru.raskol.zones.foundation.FoundationRuntime;
import ru.raskol.zones.foundation.config.ConfigurationService;
import ru.raskol.zones.foundation.health.HealthService;
import ru.raskol.zones.foundation.storage.SqliteStorageService;

public final class RaskolZonesPlugin extends JavaPlugin {

    private FoundationRuntime runtime;

    @Override
    public void onEnable() {
        var health = new HealthService();
        Path databasePath = getDataFolder().toPath().resolve("raskolzones.db");
        runtime = new FoundationRuntime(
                new ConfigurationService(this),
                health,
                getLogger(),
                new SqliteStorageService(databasePath)
        );

        runtime.startAsync().whenComplete((ignored, failure) -> {
            if (failure != null) {
                Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                        ? failure.getCause()
                        : failure;
                getLogger().severe("Не удалось запустить RaskolZones: " + cause.getMessage());
                getServer().getScheduler().runTask(this, () -> {
                    if (isEnabled()) {
                        getServer().getPluginManager().disablePlugin(this);
                    }
                });
                return;
            }

            getLogger().info("RaskolZones готов. Состояние: " + health.snapshot().status());
        });
    }

    @Override
    public void onDisable() {
        if (runtime != null) {
            runtime.stopAsync().whenComplete((ignored, failure) -> {
                if (failure != null) {
                    getLogger().severe("Ошибка при завершении RaskolZones: " + failure.getMessage());
                }
            });
        }
    }
}
