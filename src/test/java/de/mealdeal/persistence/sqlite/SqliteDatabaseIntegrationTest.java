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
    void createsSchemaVersionTwoWithExpectedTables() throws Exception {
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

        assertEquals(2, database.getSchemaVersion());
        assertEquals(Set.of("ingredients", "tastes", "recipes", "recipe_ingredients",
                "recipe_steps", "recipe_tastes", "meal_plan_entries"), tables);
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

    @Test
    void migratesVersionOneDataToVersionTwoWithoutLoss() throws Exception {
        Path databasePath = temporaryDirectory.resolve("migration.db");
        java.util.UUID ingredientId = java.util.UUID.randomUUID();
        java.util.UUID tasteId = java.util.UUID.randomUUID();
        java.util.UUID recipeId = java.util.UUID.randomUUID();

        try (var connection = java.sql.DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath())) {
            connection.setAutoCommit(false);
            SqliteSchema.createVersion1(connection);
            insertVersionOneData(connection, ingredientId, tasteId, recipeId);
            connection.commit();
        }

        SqliteDatabase migratedDatabase = new SqliteDatabase(databasePath);
        var loadedRecipe = new SqliteRecipeRepository(migratedDatabase)
                .findById(recipeId).orElseThrow();

        assertEquals(2, migratedDatabase.getSchemaVersion());
        assertEquals("Pasta recipe", loadedRecipe.getName());
        assertEquals(new java.math.BigDecimal("500.00"),
                loadedRecipe.getIngredients().getFirst().getQuantity());
        assertEquals("Savory", loadedRecipe.getTastes().getFirst().getName());
    }

    private static void insertVersionOneData(java.sql.Connection connection,
                                             java.util.UUID ingredientId,
                                             java.util.UUID tasteId,
                                             java.util.UUID recipeId) throws Exception {
        insert(connection, "INSERT INTO ingredients VALUES (?, ?)",
                ingredientId.toString(), "Pasta");
        insert(connection, "INSERT INTO tastes VALUES (?, ?)",
                tasteId.toString(), "Savory");
        insert(connection, "INSERT INTO recipes VALUES (?, ?, ?)",
                recipeId.toString(), "Pasta recipe", 2);
        insert(connection, "INSERT INTO recipe_ingredients VALUES (?, ?, ?, ?)",
                recipeId.toString(), ingredientId.toString(), "500.00", "GRAM");
        insert(connection, "INSERT INTO recipe_steps VALUES (?, ?, ?)",
                recipeId.toString(), 1, "Cook.");
        insert(connection, "INSERT INTO recipe_tastes VALUES (?, ?)",
                recipeId.toString(), tasteId.toString());
    }

    private static void insert(java.sql.Connection connection, String sql, Object... values)
            throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
        }
    }
}
