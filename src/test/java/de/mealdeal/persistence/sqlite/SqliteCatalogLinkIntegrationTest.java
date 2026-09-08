package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.IngredientCategories;
import de.mealdeal.domain.Taste;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteCatalogLinkIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void migratesVersionFourteenWithoutChangingExistingUserData() throws Exception {
        Path path = temporaryDirectory.resolve("catalog-link-migration.db");
        UUID ingredientId = UUID.randomUUID();
        UUID tasteId = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(
                "jdbc:sqlite:" + path.toAbsolutePath())) {
            connection.setAutoCommit(false);
            createVersionFourteen(connection);
            insert(connection, "INSERT INTO ingredients (id, name, category_id) VALUES (?, ?, ?)",
                    ingredientId.toString(), "Eigene Zutat",
                    IngredientCategories.OTHER.getId().toString());
            insert(connection, "INSERT INTO tastes (id, name) VALUES (?, ?)",
                    tasteId.toString(), "Eigener Geschmack");
            connection.commit();
        }

        SqliteDatabase database = new SqliteDatabase(path);
        Ingredient ingredient = new SqliteIngredientRepository(database)
                .findById(ingredientId).orElseThrow();
        Taste taste = new SqliteTasteRepository(database).findById(tasteId).orElseThrow();

        assertEquals(16, database.getSchemaVersion());
        assertEquals(ingredientId, ingredient.getId());
        assertEquals("Eigene Zutat", ingredient.getName());
        assertTrue(ingredient.getCatalogId().isEmpty());
        assertEquals(tasteId, taste.getId());
        assertEquals("Eigener Geschmack", taste.getName());
        assertTrue(taste.getCatalogId().isEmpty());
    }

    @Test
    void persistsNullableAndAssignedCatalogLinks() {
        SqliteDatabase database = new SqliteDatabase(
                temporaryDirectory.resolve("catalog-link-roundtrip.db"));
        SqliteIngredientRepository ingredients = new SqliteIngredientRepository(database);
        SqliteTasteRepository tastes = new SqliteTasteRepository(database);
        Ingredient linkedIngredient = new Ingredient("Tomate", IngredientCategories.VEGETABLES,
                "ingredient.tomato");
        Ingredient localIngredient = new Ingredient("Familiengewürz");
        Taste linkedTaste = new Taste("Süß", "taste.sweet");
        Taste localTaste = new Taste("Familiengeschmack");

        ingredients.save(linkedIngredient);
        ingredients.save(localIngredient);
        tastes.save(linkedTaste);
        tastes.save(localTaste);

        assertEquals("ingredient.tomato", ingredients.findById(linkedIngredient.getId())
                .orElseThrow().getCatalogId().orElseThrow());
        assertTrue(ingredients.findById(localIngredient.getId())
                .orElseThrow().getCatalogId().isEmpty());
        assertEquals("taste.sweet", tastes.findById(linkedTaste.getId())
                .orElseThrow().getCatalogId().orElseThrow());
        assertTrue(tastes.findById(localTaste.getId())
                .orElseThrow().getCatalogId().isEmpty());
    }

    private static void createVersionFourteen(java.sql.Connection connection) throws Exception {
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
        SqliteSchema.createVersion11(connection);
        SqliteSchema.createVersion12(connection);
        SqliteSchema.createVersion13(connection);
        SqliteSchema.createVersion14(connection);
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
