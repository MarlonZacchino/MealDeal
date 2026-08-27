package de.mealdeal.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecipeIngredientTest {

    private final Ingredient pasta = new Ingredient("Pasta");

    @Test
    void createsRecipeIngredientWithPositiveQuantity() {
        RecipeIngredient recipeIngredient =
                new RecipeIngredient(pasta, new BigDecimal("500.25"), Unit.GRAM);

        assertEquals(new BigDecimal("500.25"), recipeIngredient.getQuantity());
    }

    @Test
    void rejectsZeroQuantity() {
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeIngredient(pasta, BigDecimal.ZERO, Unit.GRAM));
    }

    @Test
    void rejectsNegativeQuantity() {
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeIngredient(pasta, new BigDecimal("-1"), Unit.GRAM));
    }

    @Test
    void rejectsMissingIngredient() {
        assertThrows(NullPointerException.class,
                () -> new RecipeIngredient(null, BigDecimal.ONE, Unit.GRAM));
    }

    @Test
    void rejectsMissingUnit() {
        assertThrows(NullPointerException.class,
                () -> new RecipeIngredient(pasta, BigDecimal.ONE, null));
    }
}
