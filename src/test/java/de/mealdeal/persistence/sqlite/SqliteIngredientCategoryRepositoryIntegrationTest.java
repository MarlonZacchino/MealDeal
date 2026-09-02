package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.IngredientCategories;
import de.mealdeal.domain.IngredientCategory;
import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.InventoryItem;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.DuplicateIngredientCategoryException;
import de.mealdeal.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteIngredientCategoryRepositoryIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsStartCategoriesInPositionOrder() {
        var repository = repository("order.db");

        assertEquals(List.of("Obst", "Gemüse", "Fleisch", "Fisch & Meeresfrüchte",
                        "Milchprodukte", "Eier", "Getreide, Reis & Nudeln",
                        "Hülsenfrüchte", "Kräuter & Gewürze", "Backzutaten",
                        "Öle, Essig & Saucen", "Nüsse & Samen", "Tiefkühlprodukte",
                        "Getränke", "Sonstiges"),
                repository.findAll().stream().map(IngredientCategory::getName).toList());
        assertEquals(java.util.stream.IntStream.range(0, 15).boxed().toList(),
                repository.findAll().stream().map(IngredientCategory::getPosition).toList());
    }

    @Test
    void savesAndLoadsCategoryRoundtrip() {
        var repository = repository("roundtrip.db");
        IngredientCategory category = new IngredientCategory("Fermentiertes", 20);

        repository.save(category);
        IngredientCategory loaded = repository.findById(category.getId()).orElseThrow();

        assertEquals(category, loaded);
        assertEquals("Fermentiertes", loaded.getName());
        assertEquals(20, loaded.getPosition());
        assertEquals(IngredientCategories.OTHER,
                repository.findById(IngredientCategories.OTHER.getId()).orElseThrow());
    }

    @Test
    void rejectsDuplicateNameIgnoringCase() {
        var repository = repository("duplicate.db");

        assertThrows(DuplicateIngredientCategoryException.class,
                () -> repository.save(new IngredientCategory(" oBsT ", 20)));
    }

    @Test
    void atomicallyRenamesAndReordersCompleteCatalog() {
        var repository = repository("rename-order.db");
        List<IngredientCategory> categories = new java.util.ArrayList<>(repository.findAll());
        IngredientCategory fruit = categories.removeFirst();
        IngredientCategory vegetables = categories.removeFirst();
        categories.addFirst(new IngredientCategory(fruit.getId(), "Früchte", 1));
        categories.addFirst(new IngredientCategory(
                vegetables.getId(), vegetables.getName(), 0));
        List<IngredientCategory> normalized = normalize(categories);

        repository.replaceAll(normalized);

        assertEquals(List.of("Gemüse", "Früchte"), repository.findAll().stream()
                .limit(2).map(IngredientCategory::getName).toList());
        assertEquals(java.util.stream.IntStream.range(0, 15).boxed().toList(),
                repository.findAll().stream().map(IngredientCategory::getPosition).toList());
        assertEquals(fruit.getId(), repository.findAll().get(1).getId());
    }

    @Test
    void renamedAndReorderedCategoryFlowsThroughIngredientInventoryAndRecipeReads() {
        SqliteDatabase database = database("category-consumers.db");
        var categories = new SqliteIngredientCategoryRepository(database);
        var ingredients = new SqliteIngredientRepository(database);
        var inventory = new SqliteInventoryRepository(database);
        var tastes = new SqliteTasteRepository(database);
        var recipes = new SqliteRecipeRepository(database);
        Ingredient ingredient = new Ingredient("Apfel", IngredientCategories.FRUIT);
        ingredients.save(ingredient);
        inventory.save(new InventoryItem(ingredient, BigDecimal.TEN, Unit.PIECE));
        Taste taste = new Taste("Fruchtig");
        tastes.save(taste);
        Recipe recipe = new Recipe("Apfelgericht", 1,
                List.of(new RecipeIngredient(ingredient, BigDecimal.ONE, Unit.PIECE)),
                List.of(), List.of(taste));
        recipes.save(recipe);
        List<IngredientCategory> reordered = new java.util.ArrayList<>(categories.findAll());
        IngredientCategory fruit = reordered.removeFirst();
        IngredientCategory vegetables = reordered.removeFirst();
        reordered.addFirst(new IngredientCategory(fruit.getId(), "Früchte", 1));
        reordered.addFirst(new IngredientCategory(
                vegetables.getId(), vegetables.getName(), 0));

        categories.replaceAll(normalize(reordered));

        IngredientCategory ingredientCategory = ingredients.findById(ingredient.getId())
                .orElseThrow().getCategory();
        IngredientCategory inventoryCategory = inventory.findAll().getFirst()
                .getIngredient().getCategory();
        IngredientCategory recipeCategory = recipes.findById(recipe.getId()).orElseThrow()
                .getIngredients().getFirst().getIngredient().getCategory();
        assertEquals(fruit.getId(), ingredientCategory.getId());
        assertEquals("Früchte", ingredientCategory.getName());
        assertEquals(1, ingredientCategory.getPosition());
        assertEquals(ingredientCategory.getId(), inventoryCategory.getId());
        assertEquals("Früchte", inventoryCategory.getName());
        assertEquals(ingredientCategory.getId(), recipeCategory.getId());
        assertEquals("Früchte", recipeCategory.getName());
    }

    @Test
    void deletesEmptyCategoryAndClosesPositionGap() {
        SqliteDatabase database = database("empty-delete.db");
        var repository = new SqliteIngredientCategoryRepository(database);
        IngredientCategory category = new IngredientCategory("Fermentiertes", 15);
        repository.save(category);

        repository.deleteAndReassign(category.getId(), IngredientCategories.OTHER.getId(),
                normalize(repository.findAll().stream()
                        .filter(value -> !value.getId().equals(category.getId())).toList()));

        assertTrue(repository.findById(category.getId()).isEmpty());
        assertEquals(java.util.stream.IntStream.range(0, 15).boxed().toList(),
                repository.findAll().stream().map(IngredientCategory::getPosition).toList());
    }

    @Test
    void occupiedDeletionReassignsIngredientWithoutChangingItsIdentity() {
        SqliteDatabase database = database("occupied-delete.db");
        var categories = new SqliteIngredientCategoryRepository(database);
        var ingredients = new SqliteIngredientRepository(database);
        IngredientCategory category = new IngredientCategory("Fermentiertes", 15);
        categories.save(category);
        Ingredient ingredient = new Ingredient("Kimchi", category);
        ingredients.save(ingredient);

        categories.deleteAndReassign(category.getId(), IngredientCategories.OTHER.getId(),
                normalize(categories.findAll().stream()
                        .filter(value -> !value.getId().equals(category.getId())).toList()));

        Ingredient loaded = ingredients.findById(ingredient.getId()).orElseThrow();
        assertEquals(ingredient.getId(), loaded.getId());
        assertEquals("Kimchi", loaded.getName());
        assertEquals(IngredientCategories.OTHER.getId(), loaded.getCategory().getId());
        assertFalse(categories.findById(category.getId()).isPresent());
    }

    @Test
    void deletionFailureRollsBackIngredientReassignmentAndCategoryRemoval() throws Exception {
        SqliteDatabase database = database("rollback.db");
        var categories = new SqliteIngredientCategoryRepository(database);
        var ingredients = new SqliteIngredientRepository(database);
        IngredientCategory category = new IngredientCategory("Fermentiertes", 15);
        categories.save(category);
        Ingredient ingredient = new Ingredient("Kimchi", category);
        ingredients.save(ingredient);
        try (var connection = database.openConnection();
             var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TRIGGER reject_category_delete
                    BEFORE DELETE ON ingredient_categories
                    BEGIN SELECT RAISE(ABORT, 'delete rejected'); END
                    """);
        }

        assertThrows(PersistenceException.class,
                () -> categories.deleteAndReassign(category.getId(),
                        IngredientCategories.OTHER.getId(),
                        normalize(categories.findAll().stream()
                                .filter(value -> !value.getId().equals(category.getId())).toList())));

        assertEquals(category.getId(), ingredients.findById(ingredient.getId())
                .orElseThrow().getCategory().getId());
        assertTrue(categories.findById(category.getId()).isPresent());
        assertEquals(16, categories.findAll().size());
    }

    @Test
    void refusesDeletingFallbackCategory() {
        var repository = repository("fallback.db");

        assertThrows(IllegalArgumentException.class,
                () -> repository.deleteAndReassign(IngredientCategories.OTHER.getId(),
                        IngredientCategories.OTHER.getId(), repository.findAll()));
    }

    private static List<IngredientCategory> normalize(List<IngredientCategory> categories) {
        return java.util.stream.IntStream.range(0, categories.size())
                .mapToObj(index -> new IngredientCategory(categories.get(index).getId(),
                        categories.get(index).getName(), index))
                .toList();
    }

    private SqliteIngredientCategoryRepository repository(String fileName) {
        return new SqliteIngredientCategoryRepository(database(fileName));
    }

    private SqliteDatabase database(String fileName) {
        return new SqliteDatabase(temporaryDirectory.resolve(fileName));
    }
}
