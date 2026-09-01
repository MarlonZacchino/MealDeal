package de.mealdeal.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MealPlanEntryTest {

    private final Recipe recipe = new Recipe("Pasta", List.of(), List.of(),
            List.of(new Taste("Savory")));

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 10})
    void createsValidEntryWithPositiveServingCount(int servingCount) {
        MealPlanEntry entry = new MealPlanEntry(
                LocalDate.of(2026, 8, 31), recipe, servingCount);

        assertEquals(servingCount, entry.getServingCount());
        assertEquals(LocalDate.of(2026, 8, 31), entry.getDate());
        assertEquals(recipe, entry.getRecipe());
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(NullPointerException.class,
                () -> new MealPlanEntry(null, LocalDate.now(), recipe, 2));
        assertThrows(NullPointerException.class,
                () -> new MealPlanEntry(UUID.randomUUID(), null, recipe, 2));
        assertThrows(NullPointerException.class,
                () -> new MealPlanEntry(UUID.randomUUID(), LocalDate.now(), null, 2));
    }

    @Test
    void rejectsNonPositiveServingCount() {
        assertThrows(IllegalArgumentException.class,
                () -> new MealPlanEntry(LocalDate.now(), recipe, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new MealPlanEntry(LocalDate.now(), recipe, -1));
    }

    @Test
    void equalityUsesStableUuid() {
        UUID id = UUID.randomUUID();
        MealPlanEntry first = new MealPlanEntry(
                id, LocalDate.of(2026, 8, 31), recipe, 2);
        MealPlanEntry changed = new MealPlanEntry(
                id, LocalDate.of(2026, 9, 1), recipe, 4);

        assertEquals(first, changed);
        assertNotEquals(first, new MealPlanEntry(LocalDate.of(2026, 8, 31), recipe, 2));
    }

    @Test
    void acceptsOrderedSideDishWithMatchingRecipeType() {
        Recipe sideRecipe = new Recipe("Bread", 2, List.of(), List.of(),
                List.of(new Taste("Savory")), DishType.SIDE);

        MealPlanEntry entry = new MealPlanEntry(LocalDate.of(2026, 8, 31), sideRecipe,
                3, MealRole.SIDE, 2);

        assertEquals(MealRole.SIDE, entry.getMealRole());
        assertEquals(2, entry.getPosition());
        assertEquals(3, entry.getServingCount());
    }

    @Test
    void rejectsRoleThatDoesNotMatchRecipeType() {
        Recipe sideRecipe = new Recipe("Bread", 2, List.of(), List.of(),
                List.of(new Taste("Savory")), DishType.SIDE);

        assertThrows(IllegalArgumentException.class, () -> new MealPlanEntry(
                LocalDate.now(), recipe, 2, MealRole.SIDE, 0));
        assertThrows(IllegalArgumentException.class, () -> new MealPlanEntry(
                LocalDate.now(), sideRecipe, 2, MealRole.MAIN, 0));
    }

    @Test
    void resolvesDefaultWithoutSelectionAndExplicitAlternativePerEntry() {
        RecipeIngredientOption pasta = new RecipeIngredientOption(
                new Ingredient("Pasta"), new BigDecimal("500"), Unit.GRAM, 0);
        RecipeIngredientOption rice = new RecipeIngredientOption(
                new Ingredient("Rice"), new BigDecimal("350"), Unit.GRAM, 1);
        RecipeIngredientGroup group = new RecipeIngredientGroup(
                List.of(pasta, rice), pasta);
        Recipe flexible = Recipe.withIngredientGroups("Flexible", 2, List.of(group),
                List.of(), List.of(new Taste("Savory")), DishType.MAIN);

        MealPlanEntry defaultEntry = new MealPlanEntry(
                LocalDate.of(2026, 9, 1), flexible, 2);
        MealPlanEntry alternativeEntry = new MealPlanEntry(UUID.randomUUID(),
                LocalDate.of(2026, 9, 2), flexible, 2, MealRole.MAIN, 0,
                Map.of(group.getId(), rice.getId()));

        assertEquals(pasta, defaultEntry.getSelectedOption(group));
        assertEquals(rice, alternativeEntry.getSelectedOption(group));
        assertEquals(Map.of(), defaultEntry.getIngredientOptionSelections());
        assertEquals(Map.of(group.getId(), rice.getId()),
                alternativeEntry.getIngredientOptionSelections());
    }

    @Test
    void rejectsSelectionFromAnotherGroupAndSelectionForSingleOptionGroup() {
        RecipeIngredientOption only = new RecipeIngredientOption(
                new Ingredient("Pasta"), BigDecimal.ONE, Unit.PIECE, 0);
        RecipeIngredientGroup group = new RecipeIngredientGroup(List.of(only), only);
        Recipe single = Recipe.withIngredientGroups("Single", 2, List.of(group),
                List.of(), List.of(new Taste("Savory")), DishType.MAIN);

        assertThrows(IllegalArgumentException.class, () -> new MealPlanEntry(
                UUID.randomUUID(), LocalDate.now(), single, 2, MealRole.MAIN, 0,
                Map.of(group.getId(), only.getId())));
        assertThrows(IllegalArgumentException.class, () -> new MealPlanEntry(
                UUID.randomUUID(), LocalDate.now(), single, 2, MealRole.MAIN, 0,
                Map.of(UUID.randomUUID(), only.getId())));
    }
}
