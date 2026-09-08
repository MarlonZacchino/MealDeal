package de.mealdeal.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MealHistoryEntryTest {

    private static final Instant OCCURRED = Instant.parse("2026-09-01T18:30:00Z");
    private static final Instant CREATED = Instant.parse("2026-09-01T19:00:00Z");

    @Test
    void validEntryKeepsStableIdentityAndSnapshotValues() {
        UUID id = UUID.randomUUID();
        UUID recipeId = UUID.randomUUID();
        MealHistoryEntry entry = new MealHistoryEntry(id, recipeId, "  Carbonara  ",
                OCCURRED, 3, MealHistorySource.MANUAL, null, CREATED);

        assertEquals(id, entry.getId());
        assertEquals(recipeId, entry.getRecipeId());
        assertEquals("Carbonara", entry.getRecipeName());
        assertEquals(3, entry.getServings());
        assertEquals(OCCURRED, entry.getOccurredAt());
        assertEquals(CREATED, entry.getCreatedAt());
        assertTrue(entry.getSourceMealPlanEntryId().isEmpty());
        assertEquals(entry, new MealHistoryEntry(id, recipeId, "Other historical name",
                OCCURRED, 1, MealHistorySource.MANUAL, null, CREATED));
        assertNotEquals(entry, new MealHistoryEntry(recipeId, "Carbonara", OCCURRED,
                3, MealHistorySource.MANUAL, null, CREATED));
    }

    @Test
    void rejectsMissingIdentityTimeSourceNameAndNonPositiveServings() {
        UUID recipeId = UUID.randomUUID();
        assertThrows(NullPointerException.class, () -> new MealHistoryEntry(
                null, "Meal", OCCURRED, 1, MealHistorySource.MANUAL, null, CREATED));
        assertThrows(IllegalArgumentException.class, () -> new MealHistoryEntry(
                recipeId, " ", OCCURRED, 1, MealHistorySource.MANUAL, null, CREATED));
        assertThrows(NullPointerException.class, () -> new MealHistoryEntry(
                recipeId, "Meal", null, 1, MealHistorySource.MANUAL, null, CREATED));
        assertThrows(IllegalArgumentException.class, () -> new MealHistoryEntry(
                recipeId, "Meal", OCCURRED, 0, MealHistorySource.MANUAL, null, CREATED));
        assertThrows(NullPointerException.class, () -> new MealHistoryEntry(
                recipeId, "Meal", OCCURRED, 1, null, null, CREATED));
        assertThrows(NullPointerException.class, () -> new MealHistoryEntry(
                recipeId, "Meal", OCCURRED, 1, MealHistorySource.MANUAL, null, null));
    }

    @Test
    void mealPlanSourceRequiresItsPlanEntryIdentity() {
        UUID planEntryId = UUID.randomUUID();
        MealHistoryEntry entry = new MealHistoryEntry(UUID.randomUUID(), "Meal", OCCURRED,
                2, MealHistorySource.MEAL_PLAN, planEntryId, CREATED);

        assertEquals(planEntryId, entry.getSourceMealPlanEntryId().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> new MealHistoryEntry(
                UUID.randomUUID(), "Meal", OCCURRED, 2,
                MealHistorySource.MEAL_PLAN, null, CREATED));
    }

    @Test
    void nonMealPlanSourcesCannotClaimPlanIdentity() {
        for (MealHistorySource source : new MealHistorySource[]{
                MealHistorySource.MANUAL, MealHistorySource.RECOMMENDATION}) {
            assertThrows(IllegalArgumentException.class, () -> new MealHistoryEntry(
                    UUID.randomUUID(), "Meal", OCCURRED, 2,
                    source, UUID.randomUUID(), CREATED));
        }
    }
}
