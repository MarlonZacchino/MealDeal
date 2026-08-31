package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.DishType;
import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.MealRole;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.RecipeStep;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.RecipeDeletionRestrictedException;
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
    private Recipe breadRecipe;
    private Recipe saladRecipe;

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
        breadRecipe = recipe("Bread recipe", pasta, savory, DishType.SIDE);
        saladRecipe = recipe("Salad recipe", vegetables, savory, DishType.SIDE);
        recipeRepository.save(pastaRecipe);
        recipeRepository.save(soupRecipe);
        recipeRepository.save(breadRecipe);
        recipeRepository.save(saladRecipe);
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
    void rejectsSecondMainForTheSameDateAndKeepsExistingEntry() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        MealPlanEntry original = new MealPlanEntry(date, pastaRecipe, 2);
        MealPlanEntry replacement = new MealPlanEntry(date, soupRecipe, 4);
        mealPlanRepository.save(original);

        assertThrows(PersistenceException.class, () -> mealPlanRepository.save(replacement));

        MealPlanEntry loaded = mealPlanRepository.findByDate(date).orElseThrow();
        assertEquals(original.getId(), loaded.getId());
        assertEquals(pastaRecipe.getId(), loaded.getRecipe().getId());
        assertEquals(2, loaded.getServingCount());
        assertTrue(mealPlanRepository.findById(replacement.getId()).isEmpty());
        assertEquals(1, mealPlanRepository.findBetween(date, date).size());
    }

    @Test
    void savesMultipleOrderedSidesWithoutAMain() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        MealPlanEntry secondSide = new MealPlanEntry(date, breadRecipe, 3,
                MealRole.SIDE, 2);
        MealPlanEntry firstSide = new MealPlanEntry(date, saladRecipe, 7,
                MealRole.SIDE, 1);

        mealPlanRepository.save(secondSide);
        mealPlanRepository.save(firstSide);

        List<MealPlanEntry> entries = mealPlanRepository.findBetween(date, date);
        assertEquals(List.of(firstSide.getId(), secondSide.getId()),
                entries.stream().map(MealPlanEntry::getId).toList());
        assertEquals(List.of(1, 2), entries.stream().map(MealPlanEntry::getPosition).toList());
        assertEquals(List.of(7, 3), entries.stream().map(MealPlanEntry::getServingCount).toList());
        assertTrue(mealPlanRepository.findByDate(date).isEmpty());
    }

    @Test
    void savesMainAndSidesWithIndependentServingCounts() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        MealPlanEntry main = new MealPlanEntry(date, pastaRecipe, 2);
        MealPlanEntry side = new MealPlanEntry(date, breadRecipe, 6, MealRole.SIDE, 0);

        mealPlanRepository.applyChanges(List.of(main, side), List.of());

        List<MealPlanEntry> entries = mealPlanRepository.findBetween(date, date);
        assertEquals(List.of(MealRole.MAIN, MealRole.SIDE),
                entries.stream().map(MealPlanEntry::getMealRole).toList());
        assertEquals(List.of(2, 6), entries.stream().map(MealPlanEntry::getServingCount).toList());
        assertEquals(main.getId(), mealPlanRepository.findByDate(date).orElseThrow().getId());
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
    void rollsBackTheWholeWeeklyChangeSetWhenOneEntryCannotBeSaved() {
        LocalDate monday = LocalDate.of(2026, 9, 1);
        LocalDate tuesday = LocalDate.of(2026, 9, 2);
        MealPlanEntry existing = new MealPlanEntry(monday, pastaRecipe, 2);
        MealPlanEntry validAddition = new MealPlanEntry(tuesday, soupRecipe, 4);
        Recipe missingRecipe = new Recipe("Missing", List.of(), List.of(),
                List.of(new Taste("Missing taste")));
        MealPlanEntry invalidAddition = new MealPlanEntry(
                LocalDate.of(2026, 9, 3), missingRecipe, 3);
        mealPlanRepository.save(existing);

        assertThrows(PersistenceException.class, () -> mealPlanRepository.applyChanges(
                List.of(validAddition, invalidAddition), List.of(existing.getId())));

        assertEquals(existing.getId(), mealPlanRepository.findByDate(monday).orElseThrow().getId());
        assertTrue(mealPlanRepository.findByDate(tuesday).isEmpty());
        assertTrue(mealPlanRepository.findByDate(LocalDate.of(2026, 9, 3)).isEmpty());
    }

    @Test
    void preventsDeletingRecipeReferencedByMealPlan() {
        MealPlanEntry entry = new MealPlanEntry(LocalDate.of(2026, 9, 1), pastaRecipe, 2);
        mealPlanRepository.save(entry);

        assertThrows(RecipeDeletionRestrictedException.class,
                () -> recipeRepository.deleteById(pastaRecipe.getId()));

        assertTrue(recipeRepository.findById(pastaRecipe.getId()).isPresent());
        assertTrue(mealPlanRepository.findById(entry.getId()).isPresent());
    }

    private static Recipe recipe(String name, Ingredient ingredient, Taste taste) {
        return recipe(name, ingredient, taste, DishType.MAIN);
    }

    private static Recipe recipe(String name, Ingredient ingredient, Taste taste, DishType dishType) {
        return new Recipe(name,
                List.of(new RecipeIngredient(ingredient, BigDecimal.ONE, Unit.PIECE)),
                List.of(new RecipeStep(1, "Cook.")), List.of(taste), dishType);
    }
}
