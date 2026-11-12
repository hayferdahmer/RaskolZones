package ru.raskol.zones.foundation;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;
import ru.raskol.zones.foundation.config.PluginSettings;
import ru.raskol.zones.foundation.config.SettingsProvider;
import ru.raskol.zones.foundation.health.HealthService;
import ru.raskol.zones.foundation.health.HealthStatus;
import ru.raskol.zones.foundation.storage.StorageService;

public final class FoundationRuntime {

    private final SettingsProvider configuration;
    private final HealthService health;
    private final Logger logger;
    private final StorageService storage;
    private PluginSettings settings;
    private boolean started;
    private boolean storageCloseRequested;

    public FoundationRuntime(SettingsProvider configuration, HealthService health, Logger logger) {
        this(configuration, health, logger, null);
    }

    public FoundationRuntime(
            SettingsProvider configuration,
            HealthService health,
            Logger logger,
            StorageService storage
    ) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.health = Objects.requireNonNull(health, "health");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.storage = storage;
    }

    /**
     * Синхронный запуск оставлен для изолированных тестов и runtime без внешнего хранилища.
     */
    public synchronized void start() {
        if (storage != null) {
            throw new IllegalStateException("Для runtime с хранилищем используйте startAsync()");
        }
        if (started) {
            throw new IllegalStateException("Foundation runtime уже запущен");
        }

        loadSettings();
        started = true;
        health.markReady();
        logger.info("Конфигурация RaskolZones загружена. Язык: " + settings.language());
    }

    /**
     * Запускает хранилище вне основного потока Paper и публикует READY только после его инициализации.
     */
    public CompletionStage<Void> startAsync() {
        synchronized (this) {
            if (started) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Foundation runtime уже запущен")
                );
            }
            if (storageCloseRequested) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Foundation runtime уже остановлен")
                );
            }

            try {
                loadSettings();
                started = true;
            } catch (RuntimeException exception) {
                health.markFailed(exception.getMessage());
                return CompletableFuture.failedFuture(exception);
            }
        }

        if (storage == null) {
            synchronized (this) {
                if (started) {
                    health.markReady();
                    logger.info("Конфигурация RaskolZones загружена. Язык: " + settings.language());
                }
            }
            return CompletableFuture.completedFuture(null);
        }

        return storage.initialize().handle((ignored, failure) -> {
            synchronized (this) {
                if (failure != null) {
                    Throwable cause = unwrap(failure);
                    if (started && health.snapshot().status() == HealthStatus.STARTING) {
                        health.markFailed(messageOf(cause));
                    }
                    throw new CompletionException(cause);
                }
                if (started && health.snapshot().status() == HealthStatus.STARTING) {
                    health.markReady();
                    logger.info("Конфигурация и SQLite-хранилище RaskolZones готовы. Язык: "
                            + settings.language());
                }
            }
            return null;
        });
    }

    public synchronized PluginSettings reload() {
        if (!started || settings == null) {
            throw new IllegalStateException("Нельзя перезагрузить конфигурацию до запуска runtime");
        }

        try {
            PluginSettings reloadedSettings = Objects.requireNonNull(
                    configuration.reload(),
                    "Configuration provider вернул null при перезагрузке"
            );
            settings = reloadedSettings;
            if (health.snapshot().status() == HealthStatus.DEGRADED) {
                health.markReady();
            }
            logger.info("Конфигурация RaskolZones перезагружена. Язык: " + settings.language());
            return settings;
        } catch (RuntimeException exception) {
            if (health.snapshot().status() == HealthStatus.READY) {
                health.markDegraded("Ошибка перезагрузки конфигурации: " + exception.getMessage());
            }
            throw exception;
        }
    }

    /**
     * Запрашивает остановку без ожидания завершения I/O на основном потоке Paper.
     */
    public CompletionStage<Void> stopAsync() {
        synchronized (this) {
            if (started) {
                started = false;
                settings = null;
            }
            if (!storageCloseRequested) {
                storageCloseRequested = true;
                HealthStatus status = health.snapshot().status();
                if (status == HealthStatus.STARTING
                        || status == HealthStatus.READY
                        || status == HealthStatus.DEGRADED) {
                    health.markStopping();
                }
            } else if (storage == null) {
                return CompletableFuture.completedFuture(null);
            }
        }

        CompletionStage<Void> closing = storage == null
                ? CompletableFuture.completedFuture(null)
                : storage.closeAsync();

        return closing.whenComplete((ignored, failure) -> {
            synchronized (this) {
                HealthStatus status = health.snapshot().status();
                if (failure != null) {
                    Throwable cause = unwrap(failure);
                    if (status == HealthStatus.STOPPING) {
                        health.markFailed("Ошибка закрытия хранилища: " + messageOf(cause));
                    }
                    logger.severe("Ошибка закрытия SQLite-хранилища: " + messageOf(cause));
                } else if (status == HealthStatus.STOPPING) {
                    health.markStopped();
                    logger.info("Foundation runtime остановлен.");
                }
            }
        });
    }

    public void stop() {
        stopAsync();
    }

    public synchronized PluginSettings settings() {
        if (!started || settings == null) {
            throw new IllegalStateException("Foundation runtime ещё не готов");
        }
        return settings;
    }

    private void loadSettings() {
        settings = Objects.requireNonNull(
                configuration.load(),
                "Configuration provider вернул null"
        );
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static String messageOf(Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
