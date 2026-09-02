package de.mealdeal.service;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.IngredientCategories;
import de.mealdeal.domain.InventoryItem;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.sqlite.SqliteDatabase;
import de.mealdeal.persistence.sqlite.SqliteIngredientCategoryRepository;
import de.mealdeal.persistence.sqlite.SqliteIngredientRepository;
import de.mealdeal.persistence.sqlite.SqliteInventoryRepository;
import de.mealdeal.persistence.sqlite.SqliteRecipeRepository;
import de.mealdeal.persistence.sqlite.SqliteTasteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IngredientManagementServiceIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void renameAndCategoryChangeKeepRecipeAndInventoryReferencesValid() {
        SqliteDatabase database = new SqliteDatabase(
                temporaryDirectory.resolve("ingredient-management.db"));
        var ingredientRepository = new SqliteIngredientRepository(database);
        var categoryRepository = new SqliteIngredientCategoryRepository(database);
        var inventoryRepository = new SqliteInventoryRepository(database);
        var tasteRepository = new SqliteTasteRepository(database);
        var recipeRepository = new SqliteRecipeRepository(database);
        Ingredient ingredient = new Ingredient("Tomate", IngredientCategories.VEGETABLES);
        ingredientRepository.save(ingredient);
        inventoryRepository.save(new InventoryItem(ingredient, BigDecimal.TEN, Unit.PIECE));
        Taste taste = new Taste("Herzhaft");
        tasteRepository.save(taste);
        Recipe recipe = new Recipe("Tomatensalat", 2,
                List.of(new RecipeIngredient(ingredient, BigDecimal.ONE, Unit.PIECE)),
                List.of(), List.of(taste));
        recipeRepository.save(recipe);
        IngredientManagementService service = new IngredientManagementService(
                ingredientRepository, new IngredientCategoryService(categoryRepository));

        service.update(ingredient.getId(), "Cherrytomate", IngredientCategories.FRUIT.getId());

        Ingredient loaded = ingredientRepository.findById(ingredient.getId()).orElseThrow();
        Ingredient recipeIngredient = recipeRepository.findById(recipe.getId()).orElseThrow()
                .getIngredients().getFirst().getIngredient();
        Ingredient inventoryIngredient = inventoryRepository.findAll().getFirst().getIngredient();
        assertEquals(ingredient.getId(), loaded.getId());
        assertEquals("Cherrytomate", loaded.getName());
        assertEquals(IngredientCategories.FRUIT.getId(), loaded.getCategory().getId());
        assertEquals(loaded.getId(), recipeIngredient.getId());
        assertEquals("Cherrytomate", recipeIngredient.getName());
        assertEquals(IngredientCategories.FRUIT.getId(), recipeIngredient.getCategory().getId());
        assertEquals(loaded.getId(), inventoryIngredient.getId());
        assertEquals("Cherrytomate", inventoryIngredient.getName());
        assertEquals(IngredientCategories.FRUIT.getId(), inventoryIngredient.getCategory().getId());
    }
}
