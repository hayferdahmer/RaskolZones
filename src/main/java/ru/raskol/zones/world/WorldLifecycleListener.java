package ru.raskol.zones.world;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
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
        registry.registerLoadedWorld(world.getUID(), world.getName())
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        logger.log(Level.SEVERE,
                                "Не удалось зарегистрировать загруженный мир " + world.getName(), failure);
                    }
                });
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        var world = event.getWorld();
        registry.markWorldUnloaded(world.getUID())
                .whenComplete((updated, failure) -> {
                    if (failure != null) {
                        logger.log(Level.SEVERE,
                                "Не удалось обновить состояние выгружаемого мира " + world.getName(), failure);
                    } else if (!updated) {
                        logger.warning("Выгружаемый мир отсутствует в реестре: " + world.getName()
                                + " (" + world.getUID() + ")");
                    }
                });
    }
}
