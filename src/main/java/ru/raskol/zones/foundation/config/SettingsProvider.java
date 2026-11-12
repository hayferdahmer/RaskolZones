package ru.raskol.zones.foundation.config;

@FunctionalInterface
public interface SettingsProvider {

    PluginSettings load();
}
