package ru.raskol.zones.foundation.health;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class HealthService {

    private static final Map<HealthStatus, EnumSet<HealthStatus>> ALLOWED_TRANSITIONS = Map.of(
            HealthStatus.STARTING, EnumSet.of(HealthStatus.READY, HealthStatus.STOPPING, HealthStatus.FAILED),
            HealthStatus.READY, EnumSet.of(HealthStatus.DEGRADED, HealthStatus.STOPPING, HealthStatus.FAILED),
            HealthStatus.DEGRADED, EnumSet.of(HealthStatus.READY, HealthStatus.STOPPING, HealthStatus.FAILED),
            HealthStatus.STOPPING, EnumSet.of(HealthStatus.STOPPED, HealthStatus.FAILED),
            HealthStatus.STOPPED, EnumSet.noneOf(HealthStatus.class),
            HealthStatus.FAILED, EnumSet.noneOf(HealthStatus.class)
    );

    private final AtomicReference<HealthSnapshot> current = new AtomicReference<>(
            new HealthSnapshot(HealthStatus.STARTING, Instant.now(), "Инициализация")
    );

    public HealthSnapshot snapshot() {
        return current.get();
    }

    public void markReady() {
        transition(HealthStatus.READY, "Компоненты фундамента готовы");
    }

    public void markDegraded(String detail) {
        transition(HealthStatus.DEGRADED, detail);
    }

    public void markStopping() {
        transition(HealthStatus.STOPPING, "Остановка");
    }

    public void markStopped() {
        transition(HealthStatus.STOPPED, "Остановлено");
    }

    public void markFailed(String detail) {
        transition(HealthStatus.FAILED, detail);
    }

    private void transition(HealthStatus target, String detail) {
        while (true) {
            HealthSnapshot previous = current.get();
            if (!ALLOWED_TRANSITIONS.get(previous.status()).contains(target)) {
                throw new IllegalStateException(
                        "Недопустимый переход состояния: " + previous.status() + " -> " + target
                );
            }

            HealthSnapshot next = new HealthSnapshot(target, Instant.now(), detail);
            if (current.compareAndSet(previous, next)) {
                return;
            }
        }
    }
}
