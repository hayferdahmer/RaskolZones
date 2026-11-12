package ru.raskol.zones.foundation.config;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public record PluginSettings(String language, boolean diagnosticsEnabled) {

    private static final Pattern LANGUAGE_TAG = Pattern.compile("[a-z]{2}(?:-[a-z0-9]{2,8})*");

    public PluginSettings {
        language = Objects.requireNonNull(language, "language").trim().toLowerCase(Locale.ROOT);
        if (!LANGUAGE_TAG.matcher(language).matches()) {
            throw new IllegalArgumentException("Некорректный код языка локализации: " + language);
        }
    }

    public static PluginSettings defaults() {
        return new PluginSettings("ru", false);
    }

    public static PluginSettings from(Map<String, Object> values) {
        Objects.requireNonNull(values, "values");
        var defaults = defaults();
        Object rawLanguage = values.getOrDefault("language", defaults.language());
        Object rawDiagnostics = values.getOrDefault("diagnostics.enabled", defaults.diagnosticsEnabled());

        if (!(rawLanguage instanceof String language)) {
            throw new IllegalArgumentException("Параметр language должен быть строкой");
        }
        if (!(rawDiagnostics instanceof Boolean diagnostics)) {
            throw new IllegalArgumentException("Параметр diagnostics.enabled должен быть boolean");
        }

        return new PluginSettings(language, diagnostics);
    }
}
