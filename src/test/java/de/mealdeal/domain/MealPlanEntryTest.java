package de.mealdeal.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;
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
}
