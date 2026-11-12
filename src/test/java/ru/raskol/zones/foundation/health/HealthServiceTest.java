package ru.raskol.zones.foundation.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class HealthServiceTest {

    @Test
    void startsInStartingState() {
        var health = new HealthService();
        assertEquals(HealthStatus.STARTING, health.snapshot().status());
    }

    @Test
    void acceptsOnlyLegalLifecycleTransitions() {
        var health = new HealthService();

        health.markReady();
        assertEquals(HealthStatus.READY, health.snapshot().status());

        health.markDegraded("Проверка недоступна");
        assertEquals(HealthStatus.DEGRADED, health.snapshot().status());

        health.markReady();
        health.markStopping();
        health.markStopped();

        assertEquals(HealthStatus.STOPPED, health.snapshot().status());
        assertThrows(IllegalStateException.class, health::markReady);
        assertThrows(IllegalStateException.class, () -> health.markFailed("Повторный отказ"));
    }

    @Test
    void allowsShutdownWhileStillStarting() {
        var health = new HealthService();

        health.markStopping();
        health.markStopped();

        assertEquals(HealthStatus.STOPPED, health.snapshot().status());
    }

    @Test
    void rejectsSkippingRequiredLifecycleStates() {
        var health = new HealthService();

        assertThrows(IllegalStateException.class, health::markStopped);
        assertThrows(IllegalStateException.class, () -> health.markDegraded("Повторная деградация"));
        assertEquals(HealthStatus.STARTING, health.snapshot().status());
    }

    @Test
    void failureIsTerminal() {
        var health = new HealthService();
        health.markFailed("Ошибка конфигурации");

        assertEquals(HealthStatus.FAILED, health.snapshot().status());
        assertEquals("Ошибка конфигурации", health.snapshot().detail());
        assertThrows(IllegalStateException.class, health::markReady);
    }
}
