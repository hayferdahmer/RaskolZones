package ru.raskol.zones.foundation;

import java.util.Objects;
import java.util.logging.Logger;
import ru.raskol.zones.foundation.config.PluginSettings;
import ru.raskol.zones.foundation.config.SettingsProvider;
import ru.raskol.zones.foundation.health.HealthService;

public final class FoundationRuntime {

    private final SettingsProvider configuration;
    private final HealthService health;
    private final Logger logger;
    private PluginSettings settings;
    private boolean started;

    public FoundationRuntime(SettingsProvider configuration, HealthService health, Logger logger) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.health = Objects.requireNonNull(health, "health");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public synchronized void start() {
        if (started) {
            throw new IllegalStateException("Foundation runtime уже запущен");
        }

        try {
            PluginSettings loadedSettings = Objects.requireNonNull(
                    configuration.load(),
                    "Configuration provider вернул null"
            );
            settings = loadedSettings;
            started = true;
            health.markReady();
            logger.info("Конфигурация RaskolZones загружена. Язык: " + settings.language());
        } catch (RuntimeException exception) {
            health.markFailed(exception.getMessage());
            throw exception;
        }
    }

    public synchronized void stop() {
        if (!started) {
            return;
        }

        health.markStopping();
        started = false;
        settings = null;
        health.markStopped();
    }

    public synchronized PluginSettings settings() {
        if (!started || settings == null) {
            throw new IllegalStateException("Foundation runtime ещё не готов");
        }
        return settings;
    }
}
