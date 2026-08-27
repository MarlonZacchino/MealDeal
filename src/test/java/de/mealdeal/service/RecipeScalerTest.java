package de.mealdeal.service;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.RecipeStep;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecipeScalerTest {

    private final RecipeScaler scaler = new RecipeScaler();
    private Ingredient pasta;

    @BeforeEach
    void setUp() {
        pasta = new Ingredient("Pasta");
    }

    @Test
    void keepsAmountForStandardServingCount() {
        assertScaledAmount(recipe(2, "500"), 2, "500");
    }

    @Test
    void halvesAmountForHalfTheServings() {
        assertScaledAmount(recipe(2, "500"), 1, "250");
    }

    @Test
    void doublesAmountForTwiceTheServings() {
        assertScaledAmount(recipe(2, "500"), 4, "1000");
    }

    @Test
    void scalesToOddServingCount() {
        assertScaledAmount(recipe(2, "500"), 5, "1250");
    }

    @Test
    void usesDecimal128ForRepeatingResults() {
        BigDecimal expected = new BigDecimal("100")
                .divide(new BigDecimal("3"), MathContext.DECIMAL128);

        BigDecimal actual = scaler.scale(recipe(3, "100"), 1).getFirst().getQuantity();

        assertEquals(expected, actual);
        assertEquals(34, actual.precision());
    }

    @Test
    void rejectsNonPositiveServingCounts() {
        Recipe recipe = recipe(2, "500");

        assertThrows(IllegalArgumentException.class, () -> scaler.scale(recipe, 0));
        assertThrows(IllegalArgumentException.class, () -> scaler.scale(recipe, -1));
    }

    @Test
    void leavesOriginalRecipeUnchanged() {
        Recipe recipe = recipe(2, "500");
        RecipeIngredient originalIngredient = recipe.getIngredients().getFirst();

        List<RecipeIngredient> scaledIngredients = scaler.scale(recipe, 4);

        assertEquals(new BigDecimal("500"), originalIngredient.getQuantity());
        assertEquals(2, recipe.getStandardServingCount());
        assertNotSame(originalIngredient, scaledIngredients.getFirst());
        assertEquals(pasta, scaledIngredients.getFirst().getIngredient());
        assertThrows(UnsupportedOperationException.class, scaledIngredients::clear);
    }

    @Test
    void rejectsNullRecipe() {
        assertThrows(NullPointerException.class, () -> scaler.scale(null, 2));
    }

    private Recipe recipe(int servings, String amount) {
        return new Recipe("Pasta", servings,
                List.of(new RecipeIngredient(pasta, new BigDecimal(amount), Unit.GRAM)),
                List.of(new RecipeStep(1, "Cook.")), List.of(new Taste("Savory")));
    }

    private void assertScaledAmount(
            Recipe recipe, int requestedServings, String expectedAmount) {
        BigDecimal actual = scaler.scale(recipe, requestedServings).getFirst().getQuantity();
        assertEquals(new BigDecimal(expectedAmount), actual);
    }
}
