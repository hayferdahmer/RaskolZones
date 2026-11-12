package ru.raskol.zones;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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

        var initialWorldRegistrations = new ArrayList<CompletableFuture<Void>>();
        for (var world : getServer().getWorlds()) {
            var worldId = world.getUID();
            var worldName = world.getName();
            var registration = worldRegistry.registerLoadedWorld(worldId, worldName)
                    .toCompletableFuture()
                    .whenComplete((ignored, failure) -> {
                        if (failure != null) {
                            getLogger().severe("Не удалось зарегистрировать мир "
                                    + worldName + ": " + failure.getMessage());
                        }
                    });
            initialWorldRegistrations.add(registration);
        }

        runtime = new FoundationRuntime(
                new ConfigurationService(this),
                health,
                getLogger(),
                storage
        );

        CompletableFuture<Void> registryReady = CompletableFuture.allOf(
                initialWorldRegistrations.toArray(CompletableFuture[]::new)
        );
        runtime.startAsync(registryReady).whenComplete((ignored, failure) -> {
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
            var finalWorldState = worldRegistry == null
                    ? CompletableFuture.<Integer>completedFuture(0)
                    : worldRegistry.markAllWorldsUnloaded();
            finalWorldState.whenComplete((updated, failure) -> {
                if (failure != null) {
                    getLogger().severe("Не удалось сохранить состояние миров при выключении: "
                            + failure.getMessage());
                }
                runtime.stopAsync().whenComplete((ignored, shutdownFailure) -> {
                    if (shutdownFailure != null) {
                        getLogger().severe("Ошибка при завершении RaskolZones: "
                                + shutdownFailure.getMessage());
                    }
                });
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
