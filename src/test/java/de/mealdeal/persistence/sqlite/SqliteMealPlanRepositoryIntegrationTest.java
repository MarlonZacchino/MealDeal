package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.MealPlanEntry;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteMealPlanRepositoryIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    private SqliteMealPlanRepository mealPlanRepository;
    private SqliteRecipeRepository recipeRepository;
    private Recipe pastaRecipe;
    private Recipe soupRecipe;

    @BeforeEach
    void setUp() {
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("meal-plan.db"));
        var ingredientRepository = new SqliteIngredientRepository(database);
        var tasteRepository = new SqliteTasteRepository(database);
        recipeRepository = new SqliteRecipeRepository(database);
        mealPlanRepository = new SqliteMealPlanRepository(database);

        Ingredient pasta = new Ingredient("Pasta");
        Ingredient vegetables = new Ingredient("Vegetables");
        Taste savory = new Taste("Savory");
        ingredientRepository.save(pasta);
        ingredientRepository.save(vegetables);
        tasteRepository.save(savory);

        pastaRecipe = recipe("Pasta recipe", pasta, savory);
        soupRecipe = recipe("Soup recipe", vegetables, savory);
        recipeRepository.save(pastaRecipe);
        recipeRepository.save(soupRecipe);
    }

    @Test
    void savesAndLoadsEntryByIdAndDate() {
        MealPlanEntry entry = new MealPlanEntry(
                LocalDate.of(2026, 9, 1), pastaRecipe, 10);

        mealPlanRepository.save(entry);
        MealPlanEntry byId = mealPlanRepository.findById(entry.getId()).orElseThrow();
        MealPlanEntry byDate = mealPlanRepository.findByDate(entry.getDate()).orElseThrow();

        assertEquals(entry.getId(), byId.getId());
        assertEquals(entry.getDate(), byId.getDate());
        assertEquals(10, byId.getServingCount());
        assertEquals(pastaRecipe.getId(), byId.getRecipe().getId());
        assertEquals(entry, byDate);
        assertTrue(mealPlanRepository.findByDate(LocalDate.of(2026, 9, 2)).isEmpty());
    }

    @Test
    void findsInclusiveDateRangeInChronologicalOrder() {
        mealPlanRepository.save(new MealPlanEntry(
                LocalDate.of(2026, 9, 3), pastaRecipe, 2));
        mealPlanRepository.save(new MealPlanEntry(
                LocalDate.of(2026, 9, 1), soupRecipe, 4));
        mealPlanRepository.save(new MealPlanEntry(
                LocalDate.of(2026, 9, 5), pastaRecipe, 2));

        List<MealPlanEntry> entries = mealPlanRepository.findBetween(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3));

        assertEquals(List.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3)),
                entries.stream().map(MealPlanEntry::getDate).toList());
        assertThrows(IllegalArgumentException.class,
                () -> mealPlanRepository.findBetween(
                        LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 1)));
    }

    @Test
    void replacesExistingDateIncludingItsIdentity() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        MealPlanEntry original = new MealPlanEntry(date, pastaRecipe, 2);
        MealPlanEntry replacement = new MealPlanEntry(date, soupRecipe, 4);
        mealPlanRepository.save(original);

        mealPlanRepository.save(replacement);

        MealPlanEntry loaded = mealPlanRepository.findByDate(date).orElseThrow();
        assertEquals(replacement.getId(), loaded.getId());
        assertEquals(soupRecipe.getId(), loaded.getRecipe().getId());
        assertEquals(4, loaded.getServingCount());
        assertTrue(mealPlanRepository.findById(original.getId()).isEmpty());
        assertEquals(1, mealPlanRepository.findBetween(date, date).size());
    }

    @Test
    void deletesEntryAndReportsUnknownId() {
        MealPlanEntry entry = new MealPlanEntry(LocalDate.of(2026, 9, 1), pastaRecipe, 2);
        mealPlanRepository.save(entry);

        assertTrue(mealPlanRepository.deleteById(entry.getId()));
        assertFalse(mealPlanRepository.findById(entry.getId()).isPresent());
        assertFalse(mealPlanRepository.deleteById(UUID.randomUUID()));
    }

    @Test
    void rejectsEntryWhoseRecipeDoesNotExist() {
        Recipe missingRecipe = new Recipe("Missing", List.of(), List.of(),
                List.of(new Taste("Missing taste")));

        assertThrows(PersistenceException.class,
                () -> mealPlanRepository.save(new MealPlanEntry(
                        LocalDate.of(2026, 9, 1), missingRecipe, 2)));
    }

    @Test
    void preventsDeletingRecipeReferencedByMealPlan() {
        MealPlanEntry entry = new MealPlanEntry(LocalDate.of(2026, 9, 1), pastaRecipe, 2);
        mealPlanRepository.save(entry);

        assertThrows(PersistenceException.class,
                () -> recipeRepository.deleteById(pastaRecipe.getId()));

        assertTrue(recipeRepository.findById(pastaRecipe.getId()).isPresent());
        assertTrue(mealPlanRepository.findById(entry.getId()).isPresent());
    }

    private static Recipe recipe(String name, Ingredient ingredient, Taste taste) {
        return new Recipe(name,
                List.of(new RecipeIngredient(ingredient, BigDecimal.ONE, Unit.PIECE)),
                List.of(new RecipeStep(1, "Cook.")), List.of(taste));
    }
}
