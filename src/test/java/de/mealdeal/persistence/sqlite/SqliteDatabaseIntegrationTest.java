package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.DishType;
import de.mealdeal.domain.IngredientCategories;
import de.mealdeal.domain.MealRole;
import de.mealdeal.domain.Recipe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteDatabaseIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsSchemaVersionThirteenWithExpectedTables() throws Exception {
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

        assertEquals(13, database.getSchemaVersion());
        assertEquals(Set.of("ingredients", "tastes", "recipes", "recipe_ingredient_groups",
                "recipe_ingredient_options", "recipe_steps", "recipe_tastes",
                "meal_plan_entries", "meal_plan_ingredient_selections",
                "ingredient_categories", "inventory_items", "inventory_consumptions",
                "inventory_consumption_items"), tables);
        assertFalse(tables.contains("recipe_ingredients"));

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
                "protein_grams", "carbohydrate_grams", "fat_grams", "dish_type",
                "baking_time_minutes", "resting_time_minutes"), recipeColumns);

        Set<String> ingredientColumns = new HashSet<>();
        try (var connection = database.openConnection();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("PRAGMA table_info(ingredients)")) {
            while (resultSet.next()) {
                ingredientColumns.add(resultSet.getString("name"));
            }
        }
        assertEquals(Set.of("id", "name", "category_id"), ingredientColumns);
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
    void databaseRequiresAnExistingCategoryForEveryIngredient() throws Exception {
        SqliteDatabase database = new SqliteDatabase(
                temporaryDirectory.resolve("ingredient-category-constraint.db"));

        try (var connection = database.openConnection()) {
            assertThrows(java.sql.SQLException.class, () -> insert(connection,
                    "INSERT INTO ingredients (id, name, category_id) VALUES (?, ?, ?)",
                    java.util.UUID.randomUUID().toString(), "Ohne Kategorie", null));
            assertThrows(java.sql.SQLException.class, () -> insert(connection,
                    "INSERT INTO ingredients (id, name, category_id) VALUES (?, ?, ?)",
                    java.util.UUID.randomUUID().toString(), "Unbekannte Kategorie",
                    java.util.UUID.randomUUID().toString()));
        }
    }

    @Test
    void migratesVersionSixRecipeIngredientToSingleOptionGroupWithoutLoss() throws Exception {
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
            SqliteSchema.createVersion5(connection);
            SqliteSchema.createVersion6(connection);
            insertVersionOneData(connection, ingredientId, tasteId, recipeId);
            insert(connection, "INSERT INTO meal_plan_entries VALUES (?, ?, ?, ?, ?, ?)",
                    entryId.toString(), "2026-09-01", recipeId.toString(), 4, "MAIN", 0);
            insert(connection, "UPDATE recipes SET preparation_time_minutes = ?, "
                            + "cooking_time_minutes = ? WHERE id = ?",
                    12, 34, recipeId.toString());
            connection.commit();
        }

        SqliteDatabase migratedDatabase = new SqliteDatabase(databasePath);
        var loadedRecipe = new SqliteRecipeRepository(migratedDatabase)
                .findById(recipeId).orElseThrow();

        assertEquals(13, migratedDatabase.getSchemaVersion());
        assertEquals("Pasta recipe", loadedRecipe.getName());
        assertEquals(new java.math.BigDecimal("500.00"),
                loadedRecipe.getIngredients().getFirst().getQuantity());
        assertEquals("Savory", loadedRecipe.getTastes().getFirst().getName());
        assertEquals(12, loadedRecipe.getPreparationTimeMinutes().orElseThrow());
        assertEquals(34, loadedRecipe.getCookingTimeMinutes().orElseThrow());
        assertTrue(loadedRecipe.getBakingTimeMinutes().isEmpty());
        assertTrue(loadedRecipe.getRestingTimeMinutes().isEmpty());
        assertEquals(46, loadedRecipe.getTotalTimeMinutes().orElseThrow());
        assertTrue(loadedRecipe.getNutritionInfo().isEmpty());
        assertEquals(DishType.MAIN, loadedRecipe.getDishType());
        assertEquals(1, loadedRecipe.getIngredientGroups().size());
        var migratedGroup = loadedRecipe.getIngredientGroups().getFirst();
        assertEquals(1, migratedGroup.getOptions().size());
        assertEquals(migratedGroup.getOptions().getFirst(), migratedGroup.getStandardOption());
        assertEquals(ingredientId, migratedGroup.getStandardOption().getIngredient().getId());
        assertEquals(IngredientCategories.OTHER,
                migratedGroup.getStandardOption().getIngredient().getCategory());
        assertEquals(new java.math.BigDecimal("500.00"),
                migratedGroup.getStandardOption().getQuantity());
        assertEquals(de.mealdeal.domain.Unit.GRAM, migratedGroup.getStandardOption().getUnit());

        var loadedEntry = new SqliteMealPlanRepository(migratedDatabase)
                .findById(entryId).orElseThrow();
        assertEquals(MealRole.MAIN, loadedEntry.getMealRole());
        assertEquals(0, loadedEntry.getPosition());
        assertEquals(4, loadedEntry.getServingCount());
        assertTrue(loadedEntry.getIngredientOptionSelections().isEmpty());
    }

    @Test
    void migratesMultipleVersionSixIngredientsInTheirPreviousRepositoryOrder() throws Exception {
        Path databasePath = temporaryDirectory.resolve("multiple-ingredient-migration.db");
        java.util.UUID pastaId = java.util.UUID.randomUUID();
        java.util.UUID appleId = java.util.UUID.randomUUID();
        java.util.UUID tasteId = java.util.UUID.randomUUID();
        java.util.UUID recipeId = java.util.UUID.randomUUID();
        try (var connection = java.sql.DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath())) {
            connection.setAutoCommit(false);
            SqliteSchema.createVersion1(connection);
            SqliteSchema.createVersion2(connection);
            SqliteSchema.createVersion3(connection);
            SqliteSchema.createVersion4(connection);
            SqliteSchema.createVersion5(connection);
            SqliteSchema.createVersion6(connection);
            insertVersionOneData(connection, pastaId, tasteId, recipeId);
            insert(connection, "INSERT INTO ingredients VALUES (?, ?)",
                    appleId.toString(), "Apple");
            insert(connection, "INSERT INTO recipe_ingredients VALUES (?, ?, ?, ?)",
                    recipeId.toString(), appleId.toString(), "2.50", "PIECE");
            connection.commit();
        }

        Recipe loaded = new SqliteRecipeRepository(new SqliteDatabase(databasePath))
                .findById(recipeId).orElseThrow();

        assertEquals(List.of("Apple", "Pasta"), loaded.getIngredientGroups().stream()
                .map(group -> group.getStandardOption().getIngredient().getName()).toList());
        assertEquals(List.of(appleId, pastaId), loaded.getIngredientGroups().stream()
                .map(group -> group.getStandardOption().getIngredient().getId()).toList());
        assertEquals(List.of(new java.math.BigDecimal("2.50"),
                        new java.math.BigDecimal("500.00")),
                loaded.getIngredientGroups().stream()
                        .map(group -> group.getStandardOption().getQuantity()).toList());
        assertEquals(List.of(de.mealdeal.domain.Unit.PIECE, de.mealdeal.domain.Unit.GRAM),
                loaded.getIngredientGroups().stream()
                        .map(group -> group.getStandardOption().getUnit()).toList());
        assertTrue(loaded.getIngredientGroups().stream()
                .allMatch(group -> group.getOptions().size() == 1
                        && group.getStandardOption().equals(group.getOptions().getFirst())));
    }

    @Test
    void versionSevenMealPlanWithoutSelectionUsesRecipeDefaultAfterMigration() throws Exception {
        Path databasePath = temporaryDirectory.resolve("version-seven-selection-migration.db");
        java.util.UUID firstIngredientId = java.util.UUID.randomUUID();
        java.util.UUID secondIngredientId = java.util.UUID.randomUUID();
        java.util.UUID tasteId = java.util.UUID.randomUUID();
        java.util.UUID recipeId = java.util.UUID.randomUUID();
        java.util.UUID entryId = java.util.UUID.randomUUID();
        java.util.UUID groupId;
        java.util.UUID defaultOptionId;

        try (var connection = java.sql.DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath())) {
            connection.setAutoCommit(false);
            SqliteSchema.createVersion1(connection);
            SqliteSchema.createVersion2(connection);
            SqliteSchema.createVersion3(connection);
            SqliteSchema.createVersion4(connection);
            SqliteSchema.createVersion5(connection);
            SqliteSchema.createVersion6(connection);
            insertVersionOneData(connection, firstIngredientId, tasteId, recipeId);
            SqliteSchema.createVersion7(connection);

            try (var statement = connection.prepareStatement(
                    "SELECT id, default_option_id FROM recipe_ingredient_groups "
                            + "WHERE recipe_id = ?")) {
                statement.setString(1, recipeId.toString());
                try (var resultSet = statement.executeQuery()) {
                    assertTrue(resultSet.next());
                    groupId = java.util.UUID.fromString(resultSet.getString("id"));
                    defaultOptionId = java.util.UUID.fromString(
                            resultSet.getString("default_option_id"));
                }
            }
            insert(connection, "INSERT INTO ingredients VALUES (?, ?)",
                    secondIngredientId.toString(), "Rice");
            insert(connection, "INSERT INTO recipe_ingredient_options VALUES (?, ?, ?, ?, ?, ?)",
                    java.util.UUID.randomUUID().toString(), groupId.toString(),
                    secondIngredientId.toString(), "350", "GRAM", 1);
            insert(connection, "INSERT INTO meal_plan_entries VALUES (?, ?, ?, ?, ?, ?)",
                    entryId.toString(), "2026-09-01", recipeId.toString(), 2, "MAIN", 0);
            connection.commit();
        }

        SqliteDatabase migratedDatabase = new SqliteDatabase(databasePath);
        var loaded = new SqliteMealPlanRepository(migratedDatabase)
                .findById(entryId).orElseThrow();
        var loadedGroup = loaded.getRecipe().getIngredientGroups().getFirst();

        assertEquals(13, migratedDatabase.getSchemaVersion());
        assertTrue(loaded.getIngredientOptionSelections().isEmpty());
        assertEquals(defaultOptionId, loaded.getSelectedOption(loadedGroup).getId());
    }

    @Test
    void versionEightAlternativeSelectionSurvivesIngredientCategoryMigration()
            throws Exception {
        Path databasePath = temporaryDirectory.resolve("version-eight-category-migration.db");
        java.util.UUID firstIngredientId = java.util.UUID.randomUUID();
        java.util.UUID secondIngredientId = java.util.UUID.randomUUID();
        java.util.UUID tasteId = java.util.UUID.randomUUID();
        java.util.UUID recipeId = java.util.UUID.randomUUID();
        java.util.UUID entryId = java.util.UUID.randomUUID();
        java.util.UUID groupId;
        java.util.UUID alternativeOptionId = java.util.UUID.randomUUID();

        try (var connection = java.sql.DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath())) {
            connection.setAutoCommit(false);
            SqliteSchema.createVersion1(connection);
            SqliteSchema.createVersion2(connection);
            SqliteSchema.createVersion3(connection);
            SqliteSchema.createVersion4(connection);
            SqliteSchema.createVersion5(connection);
            SqliteSchema.createVersion6(connection);
            insertVersionOneData(connection, firstIngredientId, tasteId, recipeId);
            SqliteSchema.createVersion7(connection);
            try (var statement = connection.prepareStatement(
                    "SELECT id FROM recipe_ingredient_groups WHERE recipe_id = ?")) {
                statement.setString(1, recipeId.toString());
                try (var resultSet = statement.executeQuery()) {
                    assertTrue(resultSet.next());
                    groupId = java.util.UUID.fromString(resultSet.getString("id"));
                }
            }
            insert(connection, "INSERT INTO ingredients VALUES (?, ?)",
                    secondIngredientId.toString(), "Rice");
            insert(connection, "INSERT INTO recipe_ingredient_options VALUES (?, ?, ?, ?, ?, ?)",
                    alternativeOptionId.toString(), groupId.toString(),
                    secondIngredientId.toString(), "350", "GRAM", 1);
            insert(connection, "INSERT INTO meal_plan_entries VALUES (?, ?, ?, ?, ?, ?)",
                    entryId.toString(), "2026-09-01", recipeId.toString(), 2, "MAIN", 0);
            SqliteSchema.createVersion8(connection);
            insert(connection, "INSERT INTO meal_plan_ingredient_selections VALUES (?, ?, ?)",
                    entryId.toString(), groupId.toString(), alternativeOptionId.toString());
            connection.commit();
        }

        SqliteDatabase migratedDatabase = new SqliteDatabase(databasePath);
        var loaded = new SqliteMealPlanRepository(migratedDatabase)
                .findById(entryId).orElseThrow();
        var selected = loaded.getSelectedOption(
                loaded.getRecipe().getIngredientGroups().getFirst());

        assertEquals(13, migratedDatabase.getSchemaVersion());
        assertEquals(alternativeOptionId, selected.getId());
        assertEquals(IngredientCategories.OTHER, selected.getIngredient().getCategory());
    }

    @Test
    void versionNineIngredientAndRecipeDataSurviveInventoryMigration() throws Exception {
        Path databasePath = temporaryDirectory.resolve("version-nine-inventory-migration.db");
        java.util.UUID ingredientId = java.util.UUID.randomUUID();
        java.util.UUID tasteId = java.util.UUID.randomUUID();
        java.util.UUID recipeId = java.util.UUID.randomUUID();

        try (var connection = java.sql.DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath())) {
            connection.setAutoCommit(false);
            SqliteSchema.createVersion1(connection);
            SqliteSchema.createVersion2(connection);
            SqliteSchema.createVersion3(connection);
            SqliteSchema.createVersion4(connection);
            SqliteSchema.createVersion5(connection);
            SqliteSchema.createVersion6(connection);
            insertVersionOneData(connection, ingredientId, tasteId, recipeId);
            SqliteSchema.createVersion7(connection);
            SqliteSchema.createVersion8(connection);
            SqliteSchema.createVersion9(connection);
            insert(connection, "UPDATE ingredients SET category_id = ? WHERE id = ?",
                    IngredientCategories.GRAINS_RICE_AND_PASTA.getId().toString(),
                    ingredientId.toString());
            connection.commit();
        }

        SqliteDatabase migratedDatabase = new SqliteDatabase(databasePath);
        Recipe recipe = new SqliteRecipeRepository(migratedDatabase)
                .findById(recipeId).orElseThrow();

        assertEquals(13, migratedDatabase.getSchemaVersion());
        assertEquals("Pasta recipe", recipe.getName());
        assertEquals(IngredientCategories.GRAINS_RICE_AND_PASTA,
                recipe.getIngredients().getFirst().getIngredient().getCategory());
        assertTrue(new SqliteInventoryRepository(migratedDatabase).findAll().isEmpty());
    }

    @Test
    void versionTenRecipesMealPlansAndInventorySurviveDessertMigration() throws Exception {
        Path databasePath = temporaryDirectory.resolve("version-ten-dessert-migration.db");
        java.util.UUID ingredientId = java.util.UUID.randomUUID();
        java.util.UUID tasteId = java.util.UUID.randomUUID();
        java.util.UUID mainRecipeId = java.util.UUID.randomUUID();
        java.util.UUID sideRecipeId = java.util.UUID.randomUUID();
        java.util.UUID mainEntryId = java.util.UUID.randomUUID();
        java.util.UUID sideEntryId = java.util.UUID.randomUUID();
        java.util.UUID inventoryId = java.util.UUID.randomUUID();

        try (var connection = java.sql.DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath())) {
            connection.setAutoCommit(false);
            SqliteSchema.createVersion1(connection);
            SqliteSchema.createVersion2(connection);
            SqliteSchema.createVersion3(connection);
            SqliteSchema.createVersion4(connection);
            SqliteSchema.createVersion5(connection);
            SqliteSchema.createVersion6(connection);
            SqliteSchema.createVersion7(connection);
            SqliteSchema.createVersion8(connection);
            SqliteSchema.createVersion9(connection);
            SqliteSchema.createVersion10(connection);
            insert(connection, "INSERT INTO ingredients VALUES (?, ?, ?)",
                    ingredientId.toString(), "Pasta",
                    IngredientCategories.GRAINS_RICE_AND_PASTA.getId().toString());
            insert(connection, "INSERT INTO tastes VALUES (?, ?)",
                    tasteId.toString(), "Savory");
            insert(connection, "INSERT INTO recipes "
                            + "(id, name, standard_serving_count, dish_type) VALUES (?, ?, ?, ?)",
                    mainRecipeId.toString(), "Main", 2, "MAIN");
            insert(connection, "INSERT INTO recipes "
                            + "(id, name, standard_serving_count, dish_type) VALUES (?, ?, ?, ?)",
                    sideRecipeId.toString(), "Side", 4, "SIDE");
            insert(connection, "INSERT INTO recipe_tastes VALUES (?, ?)",
                    mainRecipeId.toString(), tasteId.toString());
            insert(connection, "INSERT INTO recipe_tastes VALUES (?, ?)",
                    sideRecipeId.toString(), tasteId.toString());
            insert(connection, "INSERT INTO meal_plan_entries VALUES (?, ?, ?, ?, ?, ?)",
                    mainEntryId.toString(), "2026-09-01", mainRecipeId.toString(), 3, "MAIN", 0);
            insert(connection, "INSERT INTO meal_plan_entries VALUES (?, ?, ?, ?, ?, ?)",
                    sideEntryId.toString(), "2026-09-01", sideRecipeId.toString(), 5, "SIDE", 0);
            insert(connection, "INSERT INTO inventory_items VALUES (?, ?, ?, ?)",
                    inventoryId.toString(), ingredientId.toString(), "750", "GRAM");
            connection.commit();
        }

        SqliteDatabase migratedDatabase = new SqliteDatabase(databasePath);
        List<de.mealdeal.domain.MealPlanEntry> entries =
                new SqliteMealPlanRepository(migratedDatabase).findBetween(
                        java.time.LocalDate.of(2026, 9, 1),
                        java.time.LocalDate.of(2026, 9, 1));

        assertEquals(13, migratedDatabase.getSchemaVersion());
        assertEquals(List.of(mainEntryId, sideEntryId), entries.stream()
                .map(de.mealdeal.domain.MealPlanEntry::getId).toList());
        assertEquals(List.of(MealRole.MAIN, MealRole.SIDE), entries.stream()
                .map(de.mealdeal.domain.MealPlanEntry::getMealRole).toList());
        assertEquals(List.of(3, 5), entries.stream()
                .map(de.mealdeal.domain.MealPlanEntry::getServingCount).toList());
        assertEquals(ingredientId, new SqliteInventoryRepository(migratedDatabase)
                .findById(inventoryId).orElseThrow().getIngredient().getId());
    }

    @Test
    void databaseRejectsDefaultOptionFromAnotherGroup() throws Exception {
        SqliteDatabase database = new SqliteDatabase(
                temporaryDirectory.resolve("default-option-constraint.db"));
        try (var connection = database.openConnection()) {
            connection.setAutoCommit(false);
            java.util.UUID recipeId = java.util.UUID.randomUUID();
            java.util.UUID ingredientId = java.util.UUID.randomUUID();
            java.util.UUID firstGroupId = java.util.UUID.randomUUID();
            java.util.UUID secondGroupId = java.util.UUID.randomUUID();
            java.util.UUID firstOptionId = java.util.UUID.randomUUID();
            java.util.UUID secondOptionId = java.util.UUID.randomUUID();
            insert(connection, "INSERT INTO recipes "
                            + "(id, name, standard_serving_count, dish_type) VALUES (?, ?, ?, ?)",
                    recipeId.toString(), "Invalid", 2, "MAIN");
            insert(connection, "INSERT INTO ingredients VALUES (?, ?, ?)",
                    ingredientId.toString(), "Ingredient",
                    IngredientCategories.OTHER.getId().toString());
            insert(connection, "INSERT INTO recipe_ingredient_groups VALUES (?, ?, ?, ?)",
                    firstGroupId.toString(), recipeId.toString(), 0, secondOptionId.toString());
            insert(connection, "INSERT INTO recipe_ingredient_groups VALUES (?, ?, ?, ?)",
                    secondGroupId.toString(), recipeId.toString(), 1, firstOptionId.toString());
            insert(connection, "INSERT INTO recipe_ingredient_options VALUES (?, ?, ?, ?, ?, ?)",
                    firstOptionId.toString(), firstGroupId.toString(), ingredientId.toString(),
                    "1", "PIECE", 0);
            insert(connection, "INSERT INTO recipe_ingredient_options VALUES (?, ?, ?, ?, ?, ?)",
                    secondOptionId.toString(), secondGroupId.toString(), ingredientId.toString(),
                    "1", "PIECE", 0);

            assertThrows(java.sql.SQLException.class, connection::commit);
            connection.rollback();
        }
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
