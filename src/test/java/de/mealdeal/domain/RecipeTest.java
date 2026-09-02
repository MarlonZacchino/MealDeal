package de.mealdeal.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeTest {

    private final Ingredient pasta = new Ingredient("Pasta");
    private final RecipeIngredient recipeIngredient =
            new RecipeIngredient(pasta, new BigDecimal("500"), Unit.GRAM);
    private final RecipeStep recipeStep = new RecipeStep(1, "Boil pasta.");
    private final Taste savory = new Taste("Savory");

    @Test
    void createsRecipeWithValidNameAndServingCount() {
        Recipe recipe = createRecipe("  Pasta  ", 4);

        assertEquals("Pasta", recipe.getName());
        assertEquals(4, recipe.getStandardServingCount());
    }

    @Test
    void usesDefaultServingCountOfTwo() {
        Recipe recipe = new Recipe("Pasta", List.of(recipeIngredient),
                List.of(recipeStep), List.of(savory));

        assertEquals(2, recipe.getStandardServingCount());
    }

    @Test
    void keepsAllTimeValuesEmptyWhenTheyAreNotProvided() {
        Recipe recipe = createRecipe("Pasta", 2);

        assertTrue(recipe.getPreparationTimeMinutes().isEmpty());
        assertTrue(recipe.getCookingTimeMinutes().isEmpty());
        assertTrue(recipe.getBakingTimeMinutes().isEmpty());
        assertTrue(recipe.getRestingTimeMinutes().isEmpty());
        assertTrue(recipe.getTotalTimeMinutes().isEmpty());
    }

    @Test
    void derivesTotalTimeFromPreparationAndCookingTime() {
        Recipe recipe = new Recipe("Pasta", 2, List.of(recipeIngredient),
                List.of(recipeStep), List.of(savory), 15, 25);

        assertEquals(15, recipe.getPreparationTimeMinutes().orElseThrow());
        assertEquals(25, recipe.getCookingTimeMinutes().orElseThrow());
        assertEquals(40, recipe.getTotalTimeMinutes().orElseThrow());
    }

    @Test
    void derivesTotalTimeFromTheOnlyAvailableTimeValue() {
        Recipe withPreparationTime = new Recipe("Pasta", 2, List.of(recipeIngredient),
                List.of(recipeStep), List.of(savory), 20, null);
        Recipe withCookingTime = new Recipe("Pasta", 2, List.of(recipeIngredient),
                List.of(recipeStep), List.of(savory), null, 45);

        assertEquals(20, withPreparationTime.getTotalTimeMinutes().orElseThrow());
        assertEquals(45, withCookingTime.getTotalTimeMinutes().orElseThrow());
    }

    @Test
    void derivesTotalTimeFromOnlyBakingTime() {
        Recipe recipe = recipeWithTimes(null, null, 30);

        assertTrue(recipe.getPreparationTimeMinutes().isEmpty());
        assertTrue(recipe.getCookingTimeMinutes().isEmpty());
        assertEquals(30, recipe.getBakingTimeMinutes().orElseThrow());
        assertEquals(30, recipe.getTotalTimeMinutes().orElseThrow());
    }

    @Test
    void derivesTotalTimeFromPreparationAndBakingTime() {
        assertEquals(50, recipeWithTimes(20, null, 30)
                .getTotalTimeMinutes().orElseThrow());
    }

    @Test
    void derivesTotalTimeFromCookingAndBakingTime() {
        assertEquals(55, recipeWithTimes(null, 25, 30)
                .getTotalTimeMinutes().orElseThrow());
    }

    @Test
    void derivesTotalTimeFromAllThreeTimeValues() {
        Recipe recipe = recipeWithTimes(10, 20, 30);

        assertEquals(10, recipe.getPreparationTimeMinutes().orElseThrow());
        assertEquals(20, recipe.getCookingTimeMinutes().orElseThrow());
        assertEquals(30, recipe.getBakingTimeMinutes().orElseThrow());
        assertEquals(60, recipe.getTotalTimeMinutes().orElseThrow());
    }

    @Test
    void derivesTotalTimeIncludingOptionalRestingTime() {
        Recipe recipe = new Recipe("Pasta", 2, List.of(recipeIngredient),
                List.of(recipeStep), List.of(savory), 10, 20, 30, 40,
                null, DishType.MAIN);

        assertEquals(40, recipe.getRestingTimeMinutes().orElseThrow());
        assertEquals(100, recipe.getTotalTimeMinutes().orElseThrow());
    }

    @Test
    void keepsNutritionInfoOptionalAndPerServing() {
        Recipe withoutNutrition = createRecipe("Pasta", 2);
        NutritionInfo nutrition = new NutritionInfo(650, new BigDecimal("42"),
                new BigDecimal("71.5"), new BigDecimal("18"));
        Recipe withNutrition = new Recipe("Pasta", 2, List.of(recipeIngredient),
                List.of(recipeStep), List.of(savory), null, null, nutrition);

        assertTrue(withoutNutrition.getNutritionInfo().isEmpty());
        assertEquals(650, withNutrition.getNutritionInfo().orElseThrow()
                .getCaloriesKcal().orElseThrow());
        assertEquals(new BigDecimal("71.5"), withNutrition.getNutritionInfo().orElseThrow()
                .getCarbohydrateGrams().orElseThrow());
    }

    @Test
    void rejectsZeroAndNegativeTimeValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new Recipe("Pasta", 2, List.of(recipeIngredient), List.of(recipeStep),
                        List.of(savory), 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Recipe("Pasta", 2, List.of(recipeIngredient), List.of(recipeStep),
                        List.of(savory), null, -1));
        assertThrows(IllegalArgumentException.class,
                () -> recipeWithTimes(null, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Recipe("Pasta", 2, List.of(recipeIngredient), List.of(recipeStep),
                        List.of(savory), null, null, null, -1, null, DishType.MAIN));
    }

    @Test
    void rejectsEmptyName() {
        assertThrows(IllegalArgumentException.class, () -> createRecipe("", 2));
    }

    @Test
    void rejectsWhitespaceOnlyName() {
        assertThrows(IllegalArgumentException.class, () -> createRecipe("   ", 2));
    }

    @Test
    void rejectsZeroServingCount() {
        assertThrows(IllegalArgumentException.class, () -> createRecipe("Pasta", 0));
    }

    @Test
    void rejectsNegativeServingCount() {
        assertThrows(IllegalArgumentException.class, () -> createRecipe("Pasta", -1));
    }

    @Test
    void protectsCollectionsFromExternalChanges() {
        List<RecipeIngredient> ingredients = new ArrayList<>(List.of(recipeIngredient));
        Recipe recipe = new Recipe("Pasta", ingredients, List.of(recipeStep), List.of(savory));

        ingredients.clear();

        assertEquals(1, recipe.getIngredients().size());
        assertThrows(UnsupportedOperationException.class, () -> recipe.getIngredients().clear());
    }

    @Test
    void rejectsRecipeWithoutTaste() {
        assertThrows(IllegalArgumentException.class,
                () -> new Recipe("Pasta", List.of(recipeIngredient), List.of(recipeStep), List.of()));
    }

    @Test
    void sortsRecipeStepsByPosition() {
        RecipeStep secondStep = new RecipeStep(2, "Serve pasta.");
        Recipe recipe = new Recipe("Pasta", List.of(recipeIngredient),
                List.of(secondStep, recipeStep), List.of(savory));

        assertEquals(List.of(recipeStep, secondStep), recipe.getSteps());
    }

    @Test
    void allowsRecipeWithoutPreparationSteps() {
        Recipe recipe = new Recipe("Pasta", List.of(recipeIngredient),
                List.of(), List.of(savory));

        assertEquals(List.of(), recipe.getSteps());
    }

    @Test
    void rejectsDuplicateStepPositions() {
        RecipeStep duplicatePosition = new RecipeStep(1, "Serve pasta.");

        assertThrows(IllegalArgumentException.class,
                () -> new Recipe("Pasta", List.of(recipeIngredient),
                        List.of(recipeStep, duplicatePosition), List.of(savory)));
    }

    @Test
    void rejectsDuplicateIngredientIdentity() {
        RecipeIngredient duplicateIngredient =
                new RecipeIngredient(pasta, BigDecimal.ONE, Unit.KILOGRAM);

        assertThrows(IllegalArgumentException.class,
                () -> new Recipe("Pasta", List.of(recipeIngredient, duplicateIngredient),
                        List.of(recipeStep), List.of(savory)));
    }

    @Test
    void rejectsDuplicateTasteIdentity() {
        assertThrows(IllegalArgumentException.class,
                () -> new Recipe("Pasta", List.of(recipeIngredient),
                        List.of(recipeStep), List.of(savory, savory)));
    }

    @Test
    void equalityUsesStableIdentity() {
        UUID id = UUID.randomUUID();
        Recipe first = new Recipe(id, "Pasta", 2, List.of(recipeIngredient),
                List.of(recipeStep), List.of(savory));
        Recipe renamed = new Recipe(id, "Renamed pasta", 4, List.of(recipeIngredient),
                List.of(recipeStep), List.of(savory));

        assertEquals(first, renamed);
        assertNotEquals(first, createRecipe("Pasta", 2));
    }

    private Recipe createRecipe(String name, int servingCount) {
        return new Recipe(name, servingCount, List.of(recipeIngredient),
                List.of(recipeStep), List.of(savory));
    }

    private Recipe recipeWithTimes(Integer preparation, Integer cooking, Integer baking) {
        return new Recipe("Pasta", 2, List.of(recipeIngredient), List.of(recipeStep),
                List.of(savory), preparation, cooking, baking, null, DishType.MAIN);
    }
}
