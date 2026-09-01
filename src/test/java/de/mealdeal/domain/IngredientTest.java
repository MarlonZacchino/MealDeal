package de.mealdeal.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IngredientTest {

    @Test
    void createsIngredientWithValidName() {
        Ingredient ingredient = new Ingredient("  Tomato  ");

        assertEquals("Tomato", ingredient.getName());
    }

    @Test
    void rejectsEmptyName() {
        assertThrows(IllegalArgumentException.class, () -> new Ingredient(""));
    }

    @Test
    void rejectsWhitespaceOnlyName() {
        assertThrows(IllegalArgumentException.class, () -> new Ingredient("   "));
    }

    @Test
    void identityDoesNotDependOnName() {
        UUID id = UUID.randomUUID();

        assertEquals(new Ingredient(id, "Tomato"), new Ingredient(id, "Tomato renamed"));
        assertNotEquals(new Ingredient("Tomato"), new Ingredient("Tomato"));
    }

    @Test
    void requiresExactlyOneCategoryAndKeepsFallbackCompatibility() {
        Ingredient categorized = new Ingredient("Tomato", IngredientCategories.VEGETABLES);

        assertEquals(IngredientCategories.VEGETABLES, categorized.getCategory());
        assertEquals(IngredientCategories.OTHER, new Ingredient("Salt").getCategory());
        assertThrows(NullPointerException.class, () -> new Ingredient("Tomato", null));
    }
}
