package de.mealdeal.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private Recipe createRecipe(String name, int servingCount) {
        return new Recipe(name, servingCount, List.of(recipeIngredient),
                List.of(recipeStep), List.of(savory));
    }
}
