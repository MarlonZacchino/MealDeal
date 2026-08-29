package de.mealdeal.ui.theme;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThemeServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void togglesBetweenLightAndDarkMode() {
        ThemeService service = new ThemeService();

        assertEquals(ThemeMode.LIGHT, service.getMode());
        assertEquals(ThemeMode.DARK, service.toggle());
        assertEquals(ThemeMode.LIGHT, service.toggle());
    }

    @Test
    void restoresPersistedThemeAfterRestart() {
        Path settingsFile = temporaryDirectory.resolve(ThemeService.SETTINGS_FILE_NAME);
        ThemeService firstRun = new ThemeService(settingsFile);

        firstRun.toggle();

        ThemeService nextRun = new ThemeService(settingsFile);
        assertEquals(ThemeMode.DARK, nextRun.getMode());
    }
}
