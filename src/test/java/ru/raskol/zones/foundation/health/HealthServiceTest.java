package ru.raskol.zones.foundation.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class HealthServiceTest {

    @Test
    void startsInStartingState() {
        var health = new HealthService();

        assertEquals(HealthStatus.STARTING, health.snapshot().status());
    }

    @Test
    void recordsStateTransitions() {
        var health = new HealthService();
        health.markReady();

        var ready = health.snapshot();
        assertEquals(HealthStatus.READY, ready.status());
        assertFalse(ready.detail().isBlank());

        health.markDegraded("Проверка недоступна");
        assertEquals(HealthStatus.DEGRADED, health.snapshot().status());

        health.markStopped();
        assertEquals(HealthStatus.STOPPED, health.snapshot().status());
    }

    @Test
    void recordsFailureDetails() {
        var health = new HealthService();
        health.markFailed("Ошибка конфигурации");

        assertEquals(HealthStatus.FAILED, health.snapshot().status());
        assertEquals("Ошибка конфигурации", health.snapshot().detail());
    }
}
