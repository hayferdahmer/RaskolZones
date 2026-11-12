package ru.raskol.zones.foundation.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PluginSettingsTest {

    @Test
    void defaultsUseRussianAndDisableDiagnostics() {
        var settings = PluginSettings.defaults();

        assertEquals("ru", settings.language());
        assertFalse(settings.diagnosticsEnabled());
    }

    @Test
    void readsConfiguredValues() {
        var settings = PluginSettings.from(Map.of(
                "language", "en",
                "diagnostics.enabled", true
        ));

        assertEquals("en", settings.language());
        assertTrue(settings.diagnosticsEnabled());
    }

    @Test
    void normalizesLanguageCode() {
        var settings = new PluginSettings(" RU ", false);
        assertEquals("ru", settings.language());
    }

    @Test
    void rejectsInvalidLanguageCode() {
        assertThrows(IllegalArgumentException.class, () -> new PluginSettings("russian language", false));
    }

    @Test
    void rejectsUnexpectedConfigurationTypes() {
        assertThrows(IllegalArgumentException.class, () -> PluginSettings.from(Map.of(
                "language", 42,
                "diagnostics.enabled", true
        )));
        assertThrows(IllegalArgumentException.class, () -> PluginSettings.from(Map.of(
                "language", "ru",
                "diagnostics.enabled", "yes"
        )));
    }
}
