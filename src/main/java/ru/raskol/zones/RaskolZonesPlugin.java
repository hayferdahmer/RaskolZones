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
import ru.raskol.zones.territory.SqliteTerritoryRepository;
import ru.raskol.zones.territory.TerritoryService;
import ru.raskol.zones.territory.type.TerritoryTypeRegistry;
import ru.raskol.zones.territory.spatial.SpatialQueryService;

public final class RaskolZonesPlugin extends JavaPlugin {

    private FoundationRuntime runtime;
    private SqliteStorageService storage;
    private WorldRegistryService worldRegistry;
    private TerritoryService territoryService;
    private SpatialQueryService spatialQueryService;

    @Override
    public void onEnable() {
        var health = new HealthService();
        Path databasePath = getDataFolder().toPath().resolve("raskolzones.db");
        storage = new SqliteStorageService(databasePath);
        worldRegistry = new WorldRegistryService(storage);
        var territoryRepository = new SqliteTerritoryRepository(storage);
        spatialQueryService = new SpatialQueryService(territoryRepository);
        territoryService = new TerritoryService(
                territoryRepository,
                TerritoryTypeRegistry.standard(),
                spatialQueryService
        );

        getServer().getPluginManager().registerEvents(
                new WorldLifecycleListener(worldRegistry, spatialQueryService, getLogger()),
                this
        );

        var initialWorldRegistrations = new ArrayList<CompletableFuture<Void>>();
        var initialWorldIds = new ArrayList<java.util.UUID>();
        for (var world : getServer().getWorlds()) {
            var worldId = world.getUID();
            var worldName = world.getName();
            initialWorldIds.add(worldId);
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
        CompletableFuture<Void> spatialReady = registryReady.thenCompose(ignored ->
                CompletableFuture.allOf(initialWorldIds.stream()
                        .map(spatialQueryService::refreshWorld)
                        .map(CompletableFuture::completedFuture)
                        .toArray(CompletableFuture[]::new))
        );
        runtime.startAsync(spatialReady).whenComplete((ignored, failure) -> {
            if (failure != null) {
                Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                        ? failure.getCause()
                        : failure;
                getLogger().severe("Не удалось запустить RaskolZones: " + cause.getMessage());
                if (isEnabled()) {
                    getServer().getScheduler().runTask(this, () -> {
                        if (isEnabled()) {
                            getServer().getPluginManager().disablePlugin(this);
                        }
                    });
                }
                return;
            }

            if (isEnabled() && health.snapshot().status()
                    == ru.raskol.zones.foundation.health.HealthStatus.READY) {
                getLogger().info("RaskolZones готов. Состояние: " + health.snapshot().status());
            }
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
