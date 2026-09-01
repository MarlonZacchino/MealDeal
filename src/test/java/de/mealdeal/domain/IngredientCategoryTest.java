package de.mealdeal.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IngredientCategoryTest {

    @Test
    void validatesAndNormalizesCategoryData() {
        IngredientCategory category = new IngredientCategory("  Gemüse  ", 3);

        assertEquals("Gemüse", category.getName());
        assertEquals(3, category.getPosition());
        assertThrows(IllegalArgumentException.class,
                () -> new IngredientCategory(" ", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new IngredientCategory("Gemüse", -1));
    }

    @Test
    void equalityUsesStableUuid() {
        UUID id = UUID.randomUUID();

        assertEquals(new IngredientCategory(id, "Alt", 1),
                new IngredientCategory(id, "Neu", 2));
    }
}
