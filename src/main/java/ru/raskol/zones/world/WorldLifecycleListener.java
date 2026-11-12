package ru.raskol.zones.world;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public final class WorldLifecycleListener implements Listener {

    private final WorldRegistryService registry;
    private final Logger logger;

    public WorldLifecycleListener(WorldRegistryService registry, Logger logger) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        var world = event.getWorld();
        var worldId = world.getUID();
        var worldName = world.getName();
        registry.registerLoadedWorld(worldId, worldName)
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        logger.log(Level.SEVERE,
                                "Не удалось зарегистрировать загруженный мир " + worldName, failure);
                    }
                });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        var world = event.getWorld();
        var worldId = world.getUID();
        var worldName = world.getName();
        registry.markWorldUnloaded(worldId)
                .whenComplete((updated, failure) -> {
                    if (failure != null) {
                        logger.log(Level.SEVERE,
                                "Не удалось обновить состояние выгружаемого мира " + worldName, failure);
                    } else if (!updated) {
                        logger.warning("Выгружаемый мир отсутствует в реестре: " + worldName
                                + " (" + worldId + ")");
                    }
                });
    }
}
