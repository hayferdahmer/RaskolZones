package ru.raskol.zones.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import ru.raskol.zones.foundation.config.PluginSettings;
import ru.raskol.zones.foundation.health.HealthService;
import ru.raskol.zones.foundation.health.HealthStatus;

class FoundationRuntimeTest {

    @Test
    void startsAndStopsUsingSettingsProvider() {
        var health = new HealthService();
        var runtime = new FoundationRuntime(
                () -> new PluginSettings("ru", false),
                health,
                Logger.getLogger("RaskolZonesTest")
        );

        runtime.start();

        assertEquals(HealthStatus.READY, health.snapshot().status());
        assertEquals("ru", runtime.settings().language());

        runtime.stop();

        assertEquals(HealthStatus.STOPPED, health.snapshot().status());
        assertThrows(IllegalStateException.class, runtime::settings);
    }

    @Test
    void rejectsRepeatedStart() {
        var runtime = new FoundationRuntime(
                PluginSettings::defaults,
                new HealthService(),
                Logger.getLogger("RaskolZonesTest")
        );

        runtime.start();

        assertThrows(IllegalStateException.class, runtime::start);
    }

    @Test
    void marksHealthFailedWhenConfigurationFails() {
        var health = new HealthService();
        var runtime = new FoundationRuntime(
                () -> {
                    throw new IllegalStateException("broken config");
                },
                health,
                Logger.getLogger("RaskolZonesTest")
        );

        assertThrows(IllegalStateException.class, runtime::start);
        assertEquals(HealthStatus.FAILED, health.snapshot().status());
        assertEquals("broken config", health.snapshot().detail());
    }

    @Test
    void reloadPublishesNewSettingsAtomically() {
        var version = new AtomicInteger();
        var runtime = new FoundationRuntime(
                () -> new PluginSettings(version.get() == 0 ? "ru" : "en", false),
                new HealthService(),
                Logger.getLogger("RaskolZonesTest")
        );

        runtime.start();
        version.set(1);

        assertEquals("en", runtime.reload().language());
        assertEquals("en", runtime.settings().language());
    }

    @Test
    void failedReloadKeepsPreviousSettingsAndMarksRuntimeDegraded() {
        var fail = new AtomicBoolean();
        var health = new HealthService();
        var runtime = new FoundationRuntime(
                () -> {
                    if (fail.get()) {
                        throw new IllegalArgumentException("invalid config");
                    }
                    return PluginSettings.defaults();
                },
                health,
                Logger.getLogger("RaskolZonesTest")
        );

        runtime.start();
        fail.set(true);

        assertThrows(IllegalArgumentException.class, runtime::reload);
        assertEquals("ru", runtime.settings().language());
        assertEquals(HealthStatus.DEGRADED, health.snapshot().status());

        fail.set(false);
        assertEquals("ru", runtime.reload().language());
        assertEquals(HealthStatus.READY, health.snapshot().status());
    }
}
