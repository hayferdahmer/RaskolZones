package ru.raskol.zones.foundation.config;

import java.util.Map;
import java.util.Objects;

public record PluginSettings(String language, boolean diagnosticsEnabled) {

    public PluginSettings {
        language = Objects.requireNonNull(language, "language").trim().toLowerCase();
        if (language.isBlank()) {
            throw new IllegalArgumentException("Язык локализации не может быть пустым");
        }
    }

    public static PluginSettings defaults() {
        return new PluginSettings("ru", false);
    }

    public static PluginSettings from(Map<String, Object> values) {
        Objects.requireNonNull(values, "values");
        var defaults = defaults();
        var language = values.get("language");
        var diagnostics = values.get("diagnostics.enabled");

        return new PluginSettings(
                language instanceof String text ? text : defaults.language(),
                diagnostics instanceof Boolean enabled ? enabled : defaults.diagnosticsEnabled()
        );
    }
}
