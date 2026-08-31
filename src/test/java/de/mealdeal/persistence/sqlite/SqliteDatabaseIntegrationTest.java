package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.DishType;
import de.mealdeal.domain.MealRole;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteDatabaseIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsSchemaVersionFiveWithExpectedTables() throws Exception {
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

        assertEquals(5, database.getSchemaVersion());
        assertEquals(Set.of("ingredients", "tastes", "recipes", "recipe_ingredients",
                "recipe_steps", "recipe_tastes", "meal_plan_entries"), tables);

        Set<String> recipeColumns = new HashSet<>();
        try (var connection = database.openConnection();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("PRAGMA table_info(recipes)")) {
            while (resultSet.next()) {
                recipeColumns.add(resultSet.getString("name"));
            }
        }
        assertEquals(Set.of("id", "name", "standard_serving_count",
                "preparation_time_minutes", "cooking_time_minutes", "calories_kcal",
                "protein_grams", "carbohydrate_grams", "fat_grams", "dish_type"), recipeColumns);
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
    void migratesVersionFourDataToVersionFiveWithoutLoss() throws Exception {
        Path databasePath = temporaryDirectory.resolve("migration.db");
        java.util.UUID ingredientId = java.util.UUID.randomUUID();
        java.util.UUID tasteId = java.util.UUID.randomUUID();
        java.util.UUID recipeId = java.util.UUID.randomUUID();
        java.util.UUID entryId = java.util.UUID.randomUUID();

        try (var connection = java.sql.DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath())) {
            connection.setAutoCommit(false);
            SqliteSchema.createVersion1(connection);
            SqliteSchema.createVersion2(connection);
            SqliteSchema.createVersion3(connection);
            SqliteSchema.createVersion4(connection);
            insertVersionOneData(connection, ingredientId, tasteId, recipeId);
            insert(connection, "INSERT INTO meal_plan_entries VALUES (?, ?, ?, ?)",
                    entryId.toString(), "2026-09-01", recipeId.toString(), 4);
            connection.commit();
        }

        SqliteDatabase migratedDatabase = new SqliteDatabase(databasePath);
        var loadedRecipe = new SqliteRecipeRepository(migratedDatabase)
                .findById(recipeId).orElseThrow();

        assertEquals(5, migratedDatabase.getSchemaVersion());
        assertEquals("Pasta recipe", loadedRecipe.getName());
        assertEquals(new java.math.BigDecimal("500.00"),
                loadedRecipe.getIngredients().getFirst().getQuantity());
        assertEquals("Savory", loadedRecipe.getTastes().getFirst().getName());
        assertTrue(loadedRecipe.getPreparationTimeMinutes().isEmpty());
        assertTrue(loadedRecipe.getCookingTimeMinutes().isEmpty());
        assertTrue(loadedRecipe.getTotalTimeMinutes().isEmpty());
        assertTrue(loadedRecipe.getNutritionInfo().isEmpty());
        assertEquals(DishType.MAIN, loadedRecipe.getDishType());

        var loadedEntry = new SqliteMealPlanRepository(migratedDatabase)
                .findById(entryId).orElseThrow();
        assertEquals(MealRole.MAIN, loadedEntry.getMealRole());
        assertEquals(0, loadedEntry.getPosition());
        assertEquals(4, loadedEntry.getServingCount());
    }

    private static void insertVersionOneData(java.sql.Connection connection,
                                             java.util.UUID ingredientId,
                                             java.util.UUID tasteId,
                                             java.util.UUID recipeId) throws Exception {
        insert(connection, "INSERT INTO ingredients VALUES (?, ?)",
                ingredientId.toString(), "Pasta");
        insert(connection, "INSERT INTO tastes VALUES (?, ?)",
                tasteId.toString(), "Savory");
        insert(connection, "INSERT INTO recipes (id, name, standard_serving_count) VALUES (?, ?, ?)",
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
