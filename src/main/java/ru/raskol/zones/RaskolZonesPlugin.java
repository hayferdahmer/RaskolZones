package ru.raskol.zones;

import java.nio.file.Path;
import java.util.concurrent.CompletionException;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import ru.raskol.zones.foundation.FoundationRuntime;
import ru.raskol.zones.foundation.config.ConfigurationService;
import ru.raskol.zones.foundation.health.HealthService;
import ru.raskol.zones.foundation.storage.SqliteStorageService;
import ru.raskol.zones.world.WorldLifecycleListener;
import ru.raskol.zones.world.WorldRegistryService;

public final class RaskolZonesPlugin extends JavaPlugin {

    private FoundationRuntime runtime;
    private SqliteStorageService storage;
    private WorldRegistryService worldRegistry;

    @Override
    public void onEnable() {
        var health = new HealthService();
        Path databasePath = getDataFolder().toPath().resolve("raskolzones.db");
        storage = new SqliteStorageService(databasePath);
        worldRegistry = new WorldRegistryService(storage);

        getServer().getPluginManager().registerEvents(
                new WorldLifecycleListener(worldRegistry, getLogger()),
                this
        );

        for (var world : getServer().getWorlds()) {
            worldRegistry.registerLoadedWorld(world.getUID(), world.getName())
                    .whenComplete((ignored, failure) -> {
                        if (failure != null) {
                            getLogger().severe("Не удалось зарегистрировать мир "
                                    + world.getName() + ": " + failure.getMessage());
                        }
                    });
        }

        runtime = new FoundationRuntime(
                new ConfigurationService(this),
                health,
                getLogger(),
                storage
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
        } else if (storage != null) {
            storage.closeAsync().whenComplete((ignored, failure) -> {
                if (failure != null) {
                    getLogger().severe("Ошибка при закрытии SQLite: " + failure.getMessage());
                }
            });
        }
    }
}
