package de.mealdeal.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void optionalCatalogLinkDoesNotReplaceLocalIdentity() {
        UUID id = UUID.randomUUID();
        Ingredient linked = new Ingredient(id, "Eigene Tomate",
                IngredientCategories.VEGETABLES, "ingredient.tomato");

        assertEquals("ingredient.tomato", linked.getCatalogId().orElseThrow());
        assertEquals(id, linked.getId());
        assertTrue(new Ingredient("Freie Zutat").getCatalogId().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new Ingredient(
                "Tomate", IngredientCategories.VEGETABLES, "  "));
    }
}
