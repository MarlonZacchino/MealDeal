package de.mealdeal.ui.theme;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Objects;

/** Maintains the current UI theme and optionally persists it in one local file. */
public final class ThemeService {

    public static final String SETTINGS_FILE_NAME = "theme.properties";

    private final Path settingsFile;
    private ThemeMode mode;

    /** Creates a non-persistent light theme, primarily for isolated UI tests. */
    public ThemeService() {
        settingsFile = null;
        mode = ThemeMode.LIGHT;
    }

    /** Creates a theme service backed by the supplied local settings file. */
    public ThemeService(Path settingsFile) {
        this.settingsFile = Objects.requireNonNull(
                settingsFile, "Theme settings path must not be null.");
        mode = load(settingsFile);
    }

    public ThemeMode getMode() {
        return mode;
    }

    /** Switches between light and dark, persisting the new mode before returning it. */
    public ThemeMode toggle() {
        ThemeMode selected = mode.toggled();
        if (settingsFile != null) {
            save(settingsFile, selected);
        }
        mode = selected;
        return mode;
    }

    private static ThemeMode load(Path settingsFile) {
        if (Files.notExists(settingsFile)) {
            return ThemeMode.LIGHT;
        }
        try {
            String value = Files.readString(settingsFile, StandardCharsets.UTF_8)
                    .strip().toUpperCase(Locale.ROOT);
            try {
                return ThemeMode.valueOf(value);
            } catch (IllegalArgumentException exception) {
                return ThemeMode.LIGHT;
            }
        } catch (IOException exception) {
            throw new ThemePersistenceException(
                    "Could not read the local theme preference.", exception);
        }
    }

    private static void save(Path settingsFile, ThemeMode mode) {
        try {
            Path parent = settingsFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(settingsFile, mode.name(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new ThemePersistenceException(
                    "Could not save the local theme preference.", exception);
        }
    }
}
