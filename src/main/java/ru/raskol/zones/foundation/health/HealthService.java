package ru.raskol.zones.foundation.health;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

public final class HealthService {

    private final AtomicReference<HealthSnapshot> current = new AtomicReference<>(
            new HealthSnapshot(HealthStatus.STARTING, Instant.now(), "Инициализация")
    );

    public HealthSnapshot snapshot() {
        return current.get();
    }

    public void markStarting() {
        update(HealthStatus.STARTING, "Запуск");
    }

    public void markReady() {
        update(HealthStatus.READY, "Компоненты фундамента готовы");
    }

    public void markDegraded(String detail) {
        update(HealthStatus.DEGRADED, detail);
    }

    public void markStopping() {
        update(HealthStatus.STOPPING, "Остановка");
    }

    public void markStopped() {
        update(HealthStatus.STOPPED, "Остановлено");
    }

    public void markFailed(String detail) {
        update(HealthStatus.FAILED, detail);
    }

    private void update(HealthStatus status, String detail) {
        current.set(new HealthSnapshot(status, Instant.now(), detail));
    }
}
