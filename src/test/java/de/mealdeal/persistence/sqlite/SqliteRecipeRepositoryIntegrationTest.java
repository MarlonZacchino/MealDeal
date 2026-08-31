package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.RecipeStep;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteRecipeRepositoryIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    private SqliteIngredientRepository ingredientRepository;
    private SqliteTasteRepository tasteRepository;
    private SqliteRecipeRepository recipeRepository;
    private Ingredient pasta;
    private Ingredient salt;
    private Taste savory;
    private Taste mild;

    @BeforeEach
    void setUp() {
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("recipes.db"));
        ingredientRepository = new SqliteIngredientRepository(database);
        tasteRepository = new SqliteTasteRepository(database);
        recipeRepository = new SqliteRecipeRepository(database);

        pasta = new Ingredient("Pasta");
        salt = new Ingredient("Salt");
        savory = new Taste("Savory");
        mild = new Taste("Mild");
        ingredientRepository.save(pasta);
        ingredientRepository.save(salt);
        tasteRepository.save(savory);
        tasteRepository.save(mild);
    }

    @Test
    void savesAndLoadsCompleteRecipeExactly() {
        BigDecimal exactQuantity = new BigDecimal("500.1250");
        Recipe recipe = new Recipe("Pasta", 3,
                List.of(new RecipeIngredient(pasta, exactQuantity, Unit.GRAM)),
                List.of(new RecipeStep(2, "Serve."), new RecipeStep(1, "Cook.")),
                List.of(savory, mild));

        recipeRepository.save(recipe);
        Recipe loaded = recipeRepository.findById(recipe.getId()).orElseThrow();

        assertEquals(recipe.getId(), loaded.getId());
        assertEquals("Pasta", loaded.getName());
        assertEquals(3, loaded.getStandardServingCount());
        assertEquals(exactQuantity, loaded.getIngredients().getFirst().getQuantity());
        assertEquals(Unit.GRAM, loaded.getIngredients().getFirst().getUnit());
        assertEquals(pasta, loaded.getIngredients().getFirst().getIngredient());
        assertEquals(List.of(1, 2), loaded.getSteps().stream().map(RecipeStep::getPosition).toList());
        assertEquals(2, loaded.getTastes().size());
    }

    @Test
    void savesAndLoadsSliceUnitByItsEnumName() {
        Recipe recipe = new Recipe("Toast",
                List.of(new RecipeIngredient(pasta, new BigDecimal("2"), Unit.SLICE)),
                List.of(new RecipeStep(1, "Toast.")), List.of(savory));

        recipeRepository.save(recipe);

        assertEquals(Unit.SLICE, recipeRepository.findById(recipe.getId()).orElseThrow()
                .getIngredients().getFirst().getUnit());
    }

    @Test
    void updatesRecipeAndReplacesAllDependentRows() {
        UUID recipeId = UUID.randomUUID();
        Recipe original = new Recipe(recipeId, "Pasta", 2,
                List.of(new RecipeIngredient(pasta, new BigDecimal("500"), Unit.GRAM)),
                List.of(new RecipeStep(1, "Cook.")), List.of(savory));
        recipeRepository.save(original);

        Recipe updated = new Recipe(recipeId, "Salt pasta", 4,
                List.of(new RecipeIngredient(salt, new BigDecimal("1.5"), Unit.TEASPOON)),
                List.of(new RecipeStep(1, "Boil."), new RecipeStep(2, "Season.")),
                List.of(mild));
        recipeRepository.save(updated);

        Recipe loaded = recipeRepository.findById(recipeId).orElseThrow();
        assertEquals("Salt pasta", loaded.getName());
        assertEquals(4, loaded.getStandardServingCount());
        assertEquals(List.of(salt), loaded.getIngredients().stream()
                .map(RecipeIngredient::getIngredient).toList());
        assertEquals(new BigDecimal("1.5"), loaded.getIngredients().getFirst().getQuantity());
        assertEquals(List.of("Boil.", "Season."), loaded.getSteps().stream()
                .map(RecipeStep::getDescription).toList());
        assertEquals(List.of(mild), loaded.getTastes());
    }

    @Test
    void loadsAllRecipesAndReportsUnknownId() {
        recipeRepository.save(recipeNamed("Ziti"));
        recipeRepository.save(recipeNamed("Alfredo"));

        assertEquals(List.of("Alfredo", "Ziti"), recipeRepository.findAll().stream()
                .map(Recipe::getName).toList());
        assertTrue(recipeRepository.findById(UUID.randomUUID()).isEmpty());
    }

    @Test
    void deletesRecipeRelationshipsButKeepsCentralData() {
        Recipe recipe = recipeNamed("Pasta");
        recipeRepository.save(recipe);

        assertTrue(recipeRepository.deleteById(recipe.getId()));
        assertFalse(recipeRepository.findById(recipe.getId()).isPresent());
        assertTrue(ingredientRepository.findById(pasta.getId()).isPresent());
        assertTrue(tasteRepository.findById(savory.getId()).isPresent());
        assertFalse(recipeRepository.deleteById(UUID.randomUUID()));
    }

    @Test
    void rollsBackEntireSaveWhenIngredientDoesNotExist() {
        Ingredient missingIngredient = new Ingredient("Missing");
        Recipe recipe = new Recipe("Invalid reference",
                List.of(new RecipeIngredient(missingIngredient, BigDecimal.ONE, Unit.PIECE)),
                List.of(new RecipeStep(1, "Fail.")), List.of(savory));

        assertThrows(PersistenceException.class, () -> recipeRepository.save(recipe));

        assertTrue(recipeRepository.findById(recipe.getId()).isEmpty());
    }

    private Recipe recipeNamed(String name) {
        return new Recipe(name,
                List.of(new RecipeIngredient(pasta, new BigDecimal("500"), Unit.GRAM)),
                List.of(new RecipeStep(1, "Cook.")), List.of(savory));
    }
}
