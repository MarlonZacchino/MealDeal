package de.mealdeal.persistence.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteDatabaseIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsSchemaVersionOneWithExpectedTables() throws Exception {
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("schema.db"));

        Set<String> tables = new HashSet<>();
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(
                     "SELECT name FROM sqlite_master WHERE type = 'table'");
             var resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                tables.add(resultSet.getString("name"));
            }
        }

        assertEquals(1, database.getSchemaVersion());
        assertEquals(Set.of("ingredients", "tastes", "recipes", "recipe_ingredients",
                "recipe_steps", "recipe_tastes"), tables);
    }

    @Test
    void enablesForeignKeysForEveryConnection() throws Exception {
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("foreign-keys.db"));

        try (var connection = database.openConnection();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("PRAGMA foreign_keys")) {
            assertEquals(1, resultSet.getInt(1));
        }
    }
}
