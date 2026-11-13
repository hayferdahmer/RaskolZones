package ru.raskol.zones.territory.transition;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import ru.raskol.zones.territory.transition.event.TerritoryTransitionEvent;

public final class TerritoryTransitionListener implements Listener {
    private final TerritoryTransitionEngine engine;
    private final PluginManager pluginManager;
    private final Logger logger;

    public TerritoryTransitionListener(
            TerritoryTransitionEngine engine,
            PluginManager pluginManager,
            Logger logger
    ) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.pluginManager = Objects.requireNonNull(pluginManager, "pluginManager");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        publish(engine.initialize(
                event.getPlayer().getUniqueId(),
                position(event.getPlayer().getLocation()),
                TransitionReason.JOIN
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || samePosition(from, to)) {
            return;
        }
        Player player = event.getPlayer();
        publish(engine.move(player.getUniqueId(), position(to), TransitionReason.MOVEMENT));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || samePosition(from, to)) {
            return;
        }
        TransitionReason reason = switch (event.getCause()) {
            case NETHER_PORTAL, END_PORTAL -> TransitionReason.PORTAL;
            default -> TransitionReason.TELEPORT;
        };
        publish(engine.move(event.getPlayer().getUniqueId(), position(to), reason));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        engine.forget(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        engine.clear();
    }

    private void publish(Iterable<TerritoryTransition> transitions) {
        for (TerritoryTransition transition : transitions) {
            try {
                pluginManager.callEvent(new TerritoryTransitionEvent(transition));
            } catch (RuntimeException failure) {
                logger.log(Level.SEVERE, "Ошибка публикации события перехода территории", failure);
            }
        }
    }

    private static boolean samePosition(Location from, Location to) {
        return from.getWorld().getUID().equals(to.getWorld().getUID())
                && Double.compare(from.getX(), to.getX()) == 0
                && Double.compare(from.getY(), to.getY()) == 0
                && Double.compare(from.getZ(), to.getZ()) == 0;
    }

    private static PlayerPosition position(Location location) {
        return new PlayerPosition(
                location.getWorld().getUID(),
                location.getX(),
                location.getBlockY(),
                location.getZ()
        );
    }
}
