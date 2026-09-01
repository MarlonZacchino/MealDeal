package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.DishType;
import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.MealRole;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.RecipeIngredientGroup;
import de.mealdeal.domain.RecipeIngredientOption;
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
import java.util.Map;
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
    private SqliteDatabase database;
    private Recipe pastaRecipe;
    private Recipe soupRecipe;
    private Recipe breadRecipe;
    private Recipe saladRecipe;
    private Recipe puddingRecipe;
    private Recipe iceCreamRecipe;
    private Ingredient pastaIngredient;
    private Ingredient vegetablesIngredient;
    private Taste savoryTaste;

    @BeforeEach
    void setUp() {
        database = new SqliteDatabase(temporaryDirectory.resolve("meal-plan.db"));
        var ingredientRepository = new SqliteIngredientRepository(database);
        var tasteRepository = new SqliteTasteRepository(database);
        recipeRepository = new SqliteRecipeRepository(database);
        mealPlanRepository = new SqliteMealPlanRepository(database);

        pastaIngredient = new Ingredient("Pasta");
        vegetablesIngredient = new Ingredient("Vegetables");
        savoryTaste = new Taste("Savory");
        ingredientRepository.save(pastaIngredient);
        ingredientRepository.save(vegetablesIngredient);
        tasteRepository.save(savoryTaste);

        pastaRecipe = recipe("Pasta recipe", pastaIngredient, savoryTaste);
        soupRecipe = recipe("Soup recipe", vegetablesIngredient, savoryTaste);
        breadRecipe = recipe("Bread recipe", pastaIngredient, savoryTaste, DishType.SIDE);
        saladRecipe = recipe("Salad recipe", vegetablesIngredient, savoryTaste, DishType.SIDE);
        puddingRecipe = recipe("Pudding recipe", pastaIngredient, savoryTaste, DishType.DESSERT);
        iceCreamRecipe = recipe(
                "Ice cream recipe", vegetablesIngredient, savoryTaste, DishType.DESSERT);
        recipeRepository.save(pastaRecipe);
        recipeRepository.save(soupRecipe);
        recipeRepository.save(breadRecipe);
        recipeRepository.save(saladRecipe);
        recipeRepository.save(puddingRecipe);
        recipeRepository.save(iceCreamRecipe);
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
    void savesDifferentAlternativeSelectionsForSameRecipeOnDifferentDays() {
        RecipeIngredientOption pasta = new RecipeIngredientOption(
                pastaIngredient, new BigDecimal("500"), Unit.GRAM, 0);
        RecipeIngredientOption vegetables = new RecipeIngredientOption(
                vegetablesIngredient, new BigDecimal("750"), Unit.GRAM, 1);
        RecipeIngredientGroup group = new RecipeIngredientGroup(
                List.of(pasta, vegetables), pasta);
        Recipe flexible = Recipe.withIngredientGroups("Flexible", 2, List.of(group),
                List.of(), List.of(savoryTaste), DishType.MAIN);
        recipeRepository.save(flexible);
        MealPlanEntry monday = new MealPlanEntry(
                LocalDate.of(2026, 9, 1), flexible, 2);
        MealPlanEntry tuesday = new MealPlanEntry(UUID.randomUUID(),
                LocalDate.of(2026, 9, 2), flexible, 4, MealRole.MAIN, 0,
                Map.of(group.getId(), vegetables.getId()));

        mealPlanRepository.applyChanges(List.of(monday, tuesday), List.of());

        List<MealPlanEntry> loaded = mealPlanRepository.findBetween(
                monday.getDate(), tuesday.getDate());
        assertEquals(pasta, loaded.get(0).getSelectedOption(
                loaded.get(0).getRecipe().getIngredientGroups().getFirst()));
        assertEquals(vegetables.getId(), loaded.get(1).getSelectedOption(
                loaded.get(1).getRecipe().getIngredientGroups().getFirst()).getId());
        assertTrue(loaded.get(0).getIngredientOptionSelections().isEmpty());
        assertEquals(Map.of(group.getId(), vegetables.getId()),
                loaded.get(1).getIngredientOptionSelections());
    }

    @Test
    void recipeUpdateKeepsStillValidMealPlanSelection() {
        RecipeIngredientOption pasta = new RecipeIngredientOption(
                pastaIngredient, new BigDecimal("500"), Unit.GRAM, 0);
        RecipeIngredientOption vegetables = new RecipeIngredientOption(
                vegetablesIngredient, new BigDecimal("750"), Unit.GRAM, 1);
        RecipeIngredientGroup group = new RecipeIngredientGroup(
                List.of(pasta, vegetables), pasta);
        Recipe flexible = Recipe.withIngredientGroups("Flexible", 2, List.of(group),
                List.of(), List.of(savoryTaste), DishType.MAIN);
        recipeRepository.save(flexible);
        MealPlanEntry planned = new MealPlanEntry(UUID.randomUUID(),
                LocalDate.of(2026, 9, 1), flexible, 2, MealRole.MAIN, 0,
                Map.of(group.getId(), vegetables.getId()));
        mealPlanRepository.save(planned);

        RecipeIngredientOption updatedPasta = new RecipeIngredientOption(pasta.getId(),
                pastaIngredient, new BigDecimal("600"), Unit.GRAM, 0);
        RecipeIngredientOption updatedVegetables = new RecipeIngredientOption(vegetables.getId(),
                vegetablesIngredient, new BigDecimal("800"), Unit.GRAM, 1);
        RecipeIngredientGroup updatedGroup = new RecipeIngredientGroup(group.getId(),
                List.of(updatedPasta, updatedVegetables), updatedPasta);
        Recipe updatedRecipe = Recipe.withIngredientGroups(flexible.getId(), "Flexible updated", 2,
                List.of(updatedGroup), List.of(), List.of(savoryTaste),
                null, null, null, DishType.MAIN);

        recipeRepository.save(updatedRecipe);

        MealPlanEntry loaded = mealPlanRepository.findById(planned.getId()).orElseThrow();
        RecipeIngredientGroup loadedGroup = loaded.getRecipe().getIngredientGroups().getFirst();
        assertEquals(Map.of(group.getId(), vegetables.getId()),
                loaded.getIngredientOptionSelections());
        assertEquals(vegetables.getId(), loaded.getSelectedOption(loadedGroup).getId());
        assertEquals(new BigDecimal("800"), loaded.getSelectedOption(loadedGroup).getQuantity());
        assertEquals(1, selectionCount(planned.getId()));
    }

    @Test
    void removedSelectedOptionFallsBackToCurrentDefaultWithoutStaleSelection() {
        RecipeIngredientOption pasta = new RecipeIngredientOption(
                pastaIngredient, new BigDecimal("500"), Unit.GRAM, 0);
        RecipeIngredientOption vegetables = new RecipeIngredientOption(
                vegetablesIngredient, new BigDecimal("750"), Unit.GRAM, 1);
        RecipeIngredientGroup group = new RecipeIngredientGroup(
                List.of(pasta, vegetables), pasta);
        Recipe flexible = Recipe.withIngredientGroups("Flexible", 2, List.of(group),
                List.of(), List.of(savoryTaste), DishType.MAIN);
        recipeRepository.save(flexible);
        MealPlanEntry planned = new MealPlanEntry(UUID.randomUUID(),
                LocalDate.of(2026, 9, 1), flexible, 2, MealRole.MAIN, 0,
                Map.of(group.getId(), vegetables.getId()));
        mealPlanRepository.save(planned);

        RecipeIngredientOption retainedPasta = new RecipeIngredientOption(pasta.getId(),
                pastaIngredient, new BigDecimal("550"), Unit.GRAM, 0);
        RecipeIngredientGroup updatedGroup = new RecipeIngredientGroup(group.getId(),
                List.of(retainedPasta), retainedPasta);
        Recipe updatedRecipe = Recipe.withIngredientGroups(flexible.getId(), "Flexible updated", 2,
                List.of(updatedGroup), List.of(), List.of(savoryTaste),
                null, null, null, DishType.MAIN);

        recipeRepository.save(updatedRecipe);

        MealPlanEntry loaded = mealPlanRepository.findById(planned.getId()).orElseThrow();
        RecipeIngredientGroup loadedGroup = loaded.getRecipe().getIngredientGroups().getFirst();
        assertTrue(loaded.getIngredientOptionSelections().isEmpty());
        assertEquals(retainedPasta.getId(), loaded.getSelectedOption(loadedGroup).getId());
        assertEquals(0, selectionCount(planned.getId()));
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
    void savesAndReloadsDessertOnlyAndMixedDaysWithStableIdsOrderAndServings() {
        LocalDate dessertOnlyDate = LocalDate.of(2026, 9, 1);
        LocalDate mixedDate = LocalDate.of(2026, 9, 2);
        MealPlanEntry dessertOnly = new MealPlanEntry(dessertOnlyDate, puddingRecipe, 6,
                MealRole.DESSERT, 0);
        MealPlanEntry main = new MealPlanEntry(mixedDate, pastaRecipe, 2);
        MealPlanEntry side = new MealPlanEntry(mixedDate, breadRecipe, 4, MealRole.SIDE, 0);
        MealPlanEntry secondDessert = new MealPlanEntry(
                mixedDate, puddingRecipe, 5, MealRole.DESSERT, 1);
        MealPlanEntry firstDessert = new MealPlanEntry(
                mixedDate, iceCreamRecipe, 3, MealRole.DESSERT, 0);

        mealPlanRepository.applyChanges(
                List.of(dessertOnly, secondDessert, main, firstDessert, side), List.of());

        List<MealPlanEntry> dessertOnlyLoaded = mealPlanRepository.findBetween(
                dessertOnlyDate, dessertOnlyDate);
        assertEquals(List.of(dessertOnly.getId()), dessertOnlyLoaded.stream()
                .map(MealPlanEntry::getId).toList());
        assertTrue(mealPlanRepository.findByDate(dessertOnlyDate).isEmpty());

        List<MealPlanEntry> mixed = mealPlanRepository.findBetween(mixedDate, mixedDate);
        assertEquals(List.of(MealRole.MAIN, MealRole.SIDE, MealRole.DESSERT, MealRole.DESSERT),
                mixed.stream().map(MealPlanEntry::getMealRole).toList());
        assertEquals(List.of(main.getId(), side.getId(), firstDessert.getId(),
                        secondDessert.getId()),
                mixed.stream().map(MealPlanEntry::getId).toList());
        assertEquals(List.of(2, 4, 3, 5), mixed.stream()
                .map(MealPlanEntry::getServingCount).toList());
    }

    @Test
    void updatesAndReordersPersistedSidesWithoutChangingTheirUuids() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        MealPlanEntry first = new MealPlanEntry(date, breadRecipe, 2, MealRole.SIDE, 0);
        MealPlanEntry second = new MealPlanEntry(date, saladRecipe, 3, MealRole.SIDE, 1);
        mealPlanRepository.applyChanges(List.of(first, second), List.of());

        MealPlanEntry movedFirst = new MealPlanEntry(first.getId(), date, saladRecipe,
                5, MealRole.SIDE, 1);
        MealPlanEntry movedSecond = new MealPlanEntry(second.getId(), date, breadRecipe,
                3, MealRole.SIDE, 0);
        mealPlanRepository.applyChanges(List.of(movedFirst, movedSecond), List.of());

        List<MealPlanEntry> loaded = mealPlanRepository.findBetween(date, date);
        assertEquals(List.of(second.getId(), first.getId()), loaded.stream()
                .map(MealPlanEntry::getId).toList());
        assertEquals(List.of(0, 1), loaded.stream().map(MealPlanEntry::getPosition).toList());
        assertEquals(List.of(3, 5), loaded.stream().map(MealPlanEntry::getServingCount).toList());
        assertEquals(List.of(breadRecipe.getId(), saladRecipe.getId()), loaded.stream()
                .map(entry -> entry.getRecipe().getId()).toList());
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
    void rollbackRestoresPreviousAlternativeSelection() {
        RecipeIngredientOption pasta = new RecipeIngredientOption(
                pastaIngredient, new BigDecimal("500"), Unit.GRAM, 0);
        RecipeIngredientOption vegetables = new RecipeIngredientOption(
                vegetablesIngredient, new BigDecimal("750"), Unit.GRAM, 1);
        RecipeIngredientGroup group = new RecipeIngredientGroup(
                List.of(pasta, vegetables), pasta);
        Recipe flexible = Recipe.withIngredientGroups("Flexible", 2, List.of(group),
                List.of(), List.of(savoryTaste), DishType.MAIN);
        recipeRepository.save(flexible);
        LocalDate date = LocalDate.of(2026, 9, 1);
        MealPlanEntry original = new MealPlanEntry(UUID.randomUUID(), date, flexible, 2,
                MealRole.MAIN, 0, Map.of(group.getId(), vegetables.getId()));
        mealPlanRepository.save(original);

        MealPlanEntry changedToDefault = new MealPlanEntry(original.getId(), date, flexible, 2,
                MealRole.MAIN, 0, Map.of());
        Recipe missingRecipe = new Recipe("Missing", List.of(), List.of(),
                List.of(new Taste("Missing taste")));
        MealPlanEntry invalidAddition = new MealPlanEntry(
                LocalDate.of(2026, 9, 2), missingRecipe, 2);

        assertThrows(PersistenceException.class, () -> mealPlanRepository.applyChanges(
                List.of(changedToDefault, invalidAddition), List.of()));

        MealPlanEntry loaded = mealPlanRepository.findById(original.getId()).orElseThrow();
        RecipeIngredientGroup loadedGroup = loaded.getRecipe().getIngredientGroups().getFirst();
        assertEquals(Map.of(group.getId(), vegetables.getId()),
                loaded.getIngredientOptionSelections());
        assertEquals(vegetables.getId(), loaded.getSelectedOption(loadedGroup).getId());
        assertEquals(1, selectionCount(original.getId()));
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

    private int selectionCount(UUID entryId) {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(
                     "SELECT count(*) FROM meal_plan_ingredient_selections "
                             + "WHERE meal_plan_entry_id = ?")) {
            statement.setString(1, entryId.toString());
            try (var resultSet = statement.executeQuery()) {
                return resultSet.getInt(1);
            }
        } catch (java.sql.SQLException exception) {
            throw new AssertionError("Could not inspect meal-plan selections.", exception);
        }
    }
}
