package de.mealdeal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationDataPathsTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void preparesDatabaseBelowLocalApplicationData() {
        Path databasePath = ApplicationDataPaths.prepareDatabasePath(
                temporaryDirectory.toString());

        assertEquals(temporaryDirectory.resolve("MealDeal").resolve("mealdeal.db"),
                databasePath);
        assertTrue(Files.isDirectory(databasePath.getParent()));
    }

    @Test
    void rejectsMissingLocalApplicationDataDirectory() {
        assertThrows(ApplicationStartupException.class,
                () -> ApplicationDataPaths.prepareDatabasePath("  "));
    }
}
