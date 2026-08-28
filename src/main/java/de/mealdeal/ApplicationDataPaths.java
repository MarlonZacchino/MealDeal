package de.mealdeal;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves and prepares MealDeal's local, non-roaming application data paths. */
public final class ApplicationDataPaths {

    private static final String APPLICATION_DIRECTORY_NAME = "MealDeal";
    private static final String DATABASE_FILE_NAME = "mealdeal.db";

    private ApplicationDataPaths() {
    }

    /**
     * Creates the MealDeal directory below {@code LOCALAPPDATA} when needed and
     * returns the path of the production SQLite file.
     */
    public static Path prepareDatabasePath() {
        return prepareDatabasePath(System.getenv("LOCALAPPDATA"));
    }

    static Path prepareDatabasePath(String localApplicationData) {
        if (localApplicationData == null || localApplicationData.isBlank()) {
            throw new ApplicationStartupException(
                    "Windows environment variable LOCALAPPDATA is not available.");
        }

        try {
            Path applicationDirectory = Path.of(localApplicationData)
                    .resolve(APPLICATION_DIRECTORY_NAME);
            Files.createDirectories(applicationDirectory);
            return applicationDirectory.resolve(DATABASE_FILE_NAME);
        } catch (InvalidPathException | IOException exception) {
            throw new ApplicationStartupException(
                    "Could not prepare the local MealDeal data directory.", exception);
        }
    }
}
