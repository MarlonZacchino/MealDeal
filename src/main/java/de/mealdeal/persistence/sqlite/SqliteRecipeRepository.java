package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.DishType;
import de.mealdeal.domain.NutritionInfo;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.RecipeStep;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.RecipeDeletionRestrictedException;
import de.mealdeal.persistence.repository.RecipeRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite/JDBC repository for complete recipes.
 *
 * <p>Saving replaces relationship rows inside one transaction. This simple
 * strategy keeps updates understandable and guarantees that a failed write
 * never leaves a partially updated recipe.</p>
 */
public final class SqliteRecipeRepository implements RecipeRepository {

    private static final int SQLITE_CONSTRAINT_ERROR_CODE = 19;
    private final SqliteDatabase database;

    public SqliteRecipeRepository(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "Database must not be null.");
    }

    @Override
    public void save(Recipe recipe) {
        Objects.requireNonNull(recipe, "Recipe must not be null.");
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                saveRecipeRow(connection, recipe);
                deleteRelationships(connection, recipe.getId());
                saveIngredients(connection, recipe);
                saveSteps(connection, recipe);
                saveTastes(connection, recipe);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new PersistenceException(
                    "Could not save recipe. Ingredients and tastes must already exist.", exception);
        }
    }

    @Override
    public Optional<Recipe> findById(UUID id) {
        Objects.requireNonNull(id, "Recipe ID must not be null.");
        try (Connection connection = database.openConnection()) {
            return loadRecipe(connection, id);
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load recipe.", exception);
        }
    }

    @Override
    public List<Recipe> findAll() {
        List<Recipe> recipes = new ArrayList<>();
        String sql = "SELECT id FROM recipes ORDER BY name, id";
        try (Connection connection = database.openConnection();
             var statement = connection.prepareStatement(sql);
             var resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                UUID id = UUID.fromString(resultSet.getString("id"));
                recipes.add(loadRecipe(connection, id).orElseThrow());
            }
            return List.copyOf(recipes);
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load recipes.", exception);
        }
    }

    @Override
    public boolean deleteById(UUID id) {
        Objects.requireNonNull(id, "Recipe ID must not be null.");
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("DELETE FROM recipes WHERE id = ?")) {
            statement.setString(1, id.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            if (isForeignKeyConstraint(exception)) {
                throw new RecipeDeletionRestrictedException(
                        "Recipe is still referenced and cannot be deleted.", exception);
            }
            throw new PersistenceException("Could not delete recipe.", exception);
        }
    }

    private static boolean isForeignKeyConstraint(SQLException exception) {
        String message = exception.getMessage();
        return exception.getErrorCode() == SQLITE_CONSTRAINT_ERROR_CODE
                && message != null
                && message.toLowerCase(java.util.Locale.ROOT).contains("foreign key");
    }

    private static void saveRecipeRow(Connection connection, Recipe recipe) throws SQLException {
        String sql = """
                INSERT INTO recipes (
                    id, name, standard_serving_count, preparation_time_minutes,
                    cooking_time_minutes, calories_kcal, protein_grams,
                    carbohydrate_grams, fat_grams, dish_type)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    standard_serving_count = excluded.standard_serving_count,
                    preparation_time_minutes = excluded.preparation_time_minutes,
                    cooking_time_minutes = excluded.cooking_time_minutes,
                    calories_kcal = excluded.calories_kcal,
                    protein_grams = excluded.protein_grams,
                    carbohydrate_grams = excluded.carbohydrate_grams,
                    fat_grams = excluded.fat_grams,
                    dish_type = excluded.dish_type
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, recipe.getId().toString());
            statement.setString(2, recipe.getName());
            statement.setInt(3, recipe.getStandardServingCount());
            setOptionalTime(statement, 4, recipe.getPreparationTimeMinutes());
            setOptionalTime(statement, 5, recipe.getCookingTimeMinutes());
            setNutrition(statement, recipe.getNutritionInfo().orElse(null));
            statement.setString(10, recipe.getDishType().name());
            statement.executeUpdate();
        }
    }

    private static void deleteRelationships(Connection connection, UUID recipeId)
            throws SQLException {
        for (String table : List.of("recipe_ingredients", "recipe_steps", "recipe_tastes")) {
            // Table names are fixed application constants; all data still uses parameters.
            try (var statement = connection.prepareStatement(
                    "DELETE FROM " + table + " WHERE recipe_id = ?")) {
                statement.setString(1, recipeId.toString());
                statement.executeUpdate();
            }
        }
    }

    private static void saveIngredients(Connection connection, Recipe recipe) throws SQLException {
        String sql = """
                INSERT INTO recipe_ingredients
                    (recipe_id, ingredient_id, quantity, unit)
                VALUES (?, ?, ?, ?)
                """;
        try (var statement = connection.prepareStatement(sql)) {
            for (RecipeIngredient recipeIngredient : recipe.getIngredients()) {
                statement.setString(1, recipe.getId().toString());
                statement.setString(2, recipeIngredient.getIngredient().getId().toString());
                // Plain decimal text preserves BigDecimal exactly without binary conversion.
                statement.setString(3, recipeIngredient.getQuantity().toPlainString());
                statement.setString(4, recipeIngredient.getUnit().name());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void saveSteps(Connection connection, Recipe recipe) throws SQLException {
        String sql = """
                INSERT INTO recipe_steps (recipe_id, position, description)
                VALUES (?, ?, ?)
                """;
        try (var statement = connection.prepareStatement(sql)) {
            for (RecipeStep step : recipe.getSteps()) {
                statement.setString(1, recipe.getId().toString());
                statement.setInt(2, step.getPosition());
                statement.setString(3, step.getDescription());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void saveTastes(Connection connection, Recipe recipe) throws SQLException {
        String sql = "INSERT INTO recipe_tastes (recipe_id, taste_id) VALUES (?, ?)";
        try (var statement = connection.prepareStatement(sql)) {
            for (Taste taste : recipe.getTastes()) {
                statement.setString(1, recipe.getId().toString());
                statement.setString(2, taste.getId().toString());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static Optional<Recipe> loadRecipe(Connection connection, UUID id) throws SQLException {
        String sql = """
                SELECT name, standard_serving_count, preparation_time_minutes,
                       cooking_time_minutes, calories_kcal, protein_grams,
                       carbohydrate_grams, fat_grams, dish_type
                FROM recipes WHERE id = ?
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                String name = resultSet.getString("name");
                int servingCount = resultSet.getInt("standard_serving_count");
                return Optional.of(new Recipe(id, name, servingCount,
                        loadIngredients(connection, id), loadSteps(connection, id),
                        loadTastes(connection, id),
                        nullableInteger(resultSet, "preparation_time_minutes"),
                        nullableInteger(resultSet, "cooking_time_minutes"),
                        nutritionInfo(resultSet),
                        DishType.valueOf(resultSet.getString("dish_type"))));
            }
        }
    }

    private static void setOptionalTime(java.sql.PreparedStatement statement, int index,
                                        java.util.OptionalInt minutes) throws SQLException {
        if (minutes.isPresent()) {
            statement.setInt(index, minutes.getAsInt());
        } else {
            statement.setNull(index, java.sql.Types.INTEGER);
        }
    }

    private static Integer nullableInteger(java.sql.ResultSet resultSet, String column)
            throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private static void setNutrition(java.sql.PreparedStatement statement, NutritionInfo nutrition)
            throws SQLException {
        if (nutrition == null) {
            statement.setNull(6, java.sql.Types.INTEGER);
            for (int index = 7; index <= 9; index++) {
                statement.setNull(index, java.sql.Types.VARCHAR);
            }
            return;
        }
        setOptionalTime(statement, 6, nutrition.getCaloriesKcal());
        setOptionalDecimal(statement, 7, nutrition.getProteinGrams());
        setOptionalDecimal(statement, 8, nutrition.getCarbohydrateGrams());
        setOptionalDecimal(statement, 9, nutrition.getFatGrams());
    }

    private static void setOptionalDecimal(java.sql.PreparedStatement statement, int index,
                                           Optional<BigDecimal> value) throws SQLException {
        if (value.isPresent()) {
            statement.setString(index, value.get().toPlainString());
        } else {
            statement.setNull(index, java.sql.Types.VARCHAR);
        }
    }

    private static NutritionInfo nutritionInfo(java.sql.ResultSet resultSet) throws SQLException {
        return new NutritionInfo(nullableInteger(resultSet, "calories_kcal"),
                nullableDecimal(resultSet, "protein_grams"),
                nullableDecimal(resultSet, "carbohydrate_grams"),
                nullableDecimal(resultSet, "fat_grams"));
    }

    private static BigDecimal nullableDecimal(java.sql.ResultSet resultSet, String column)
            throws SQLException {
        String value = resultSet.getString(column);
        return value == null ? null : new BigDecimal(value);
    }

    private static List<RecipeIngredient> loadIngredients(Connection connection, UUID recipeId)
            throws SQLException {
        String sql = """
                SELECT i.id, i.name, ri.quantity, ri.unit
                FROM recipe_ingredients ri
                JOIN ingredients i ON i.id = ri.ingredient_id
                WHERE ri.recipe_id = ?
                ORDER BY i.name, i.id
                """;
        List<RecipeIngredient> ingredients = new ArrayList<>();
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, recipeId.toString());
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Ingredient ingredient = new Ingredient(
                            UUID.fromString(resultSet.getString("id")),
                            resultSet.getString("name"));
                    ingredients.add(new RecipeIngredient(ingredient,
                            new BigDecimal(resultSet.getString("quantity")),
                            Unit.valueOf(resultSet.getString("unit"))));
                }
            }
        }
        return ingredients;
    }

    private static List<RecipeStep> loadSteps(Connection connection, UUID recipeId)
            throws SQLException {
        String sql = """
                SELECT position, description FROM recipe_steps
                WHERE recipe_id = ? ORDER BY position
                """;
        List<RecipeStep> steps = new ArrayList<>();
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, recipeId.toString());
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    steps.add(new RecipeStep(resultSet.getInt("position"),
                            resultSet.getString("description")));
                }
            }
        }
        return steps;
    }

    private static List<Taste> loadTastes(Connection connection, UUID recipeId)
            throws SQLException {
        String sql = """
                SELECT t.id, t.name FROM recipe_tastes rt
                JOIN tastes t ON t.id = rt.taste_id
                WHERE rt.recipe_id = ? ORDER BY t.name, t.id
                """;
        List<Taste> tastes = new ArrayList<>();
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, recipeId.toString());
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    tastes.add(new Taste(UUID.fromString(resultSet.getString("id")),
                            resultSet.getString("name")));
                }
            }
        }
        return tastes;
    }

    private static void rollback(Connection connection, Exception originalException) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            originalException.addSuppressed(rollbackException);
        }
    }
}
