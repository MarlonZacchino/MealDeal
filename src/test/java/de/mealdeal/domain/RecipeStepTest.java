package de.mealdeal.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecipeStepTest {

    @Test
    void createsValidStep() {
        RecipeStep step = new RecipeStep(1, "  Boil water.  ");

        assertEquals(1, step.getPosition());
        assertEquals("Boil water.", step.getDescription());
    }

    @Test
    void rejectsZeroPosition() {
        assertThrows(IllegalArgumentException.class, () -> new RecipeStep(0, "Boil water."));
    }

    @Test
    void rejectsNegativePosition() {
        assertThrows(IllegalArgumentException.class, () -> new RecipeStep(-1, "Boil water."));
    }

    @Test
    void rejectsEmptyDescription() {
        assertThrows(IllegalArgumentException.class, () -> new RecipeStep(1, ""));
    }

    @Test
    void rejectsWhitespaceOnlyDescription() {
        assertThrows(IllegalArgumentException.class, () -> new RecipeStep(1, "   "));
    }
}
