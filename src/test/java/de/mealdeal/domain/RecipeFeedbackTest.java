package de.mealdeal.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeFeedbackTest {

    private static final Instant NOW = Instant.parse("2026-09-02T10:00:00Z");

    @Test
    void supportsBinaryRatingAndRatingOnlyFeedback() {
        RecipeFeedback liked = new RecipeFeedback(
                UUID.randomUUID(), RecipeFeedbackValue.LIKE, 5, NOW);
        RecipeFeedback disliked = new RecipeFeedback(
                UUID.randomUUID(), RecipeFeedbackValue.DISLIKE, 1, NOW);
        RecipeFeedback neutralRating = new RecipeFeedback(UUID.randomUUID(), null, 3, NOW);

        assertEquals(RecipeFeedbackValue.LIKE, liked.getValue().orElseThrow());
        assertEquals(5, liked.getRating().orElseThrow());
        assertEquals(RecipeFeedbackValue.DISLIKE, disliked.getValue().orElseThrow());
        assertTrue(neutralRating.getValue().isEmpty());
        assertEquals(3, neutralRating.getRating().orElseThrow());
    }

    @Test
    void preferenceWithoutRatingIsValid() {
        RecipeFeedback feedback = new RecipeFeedback(
                UUID.randomUUID(), RecipeFeedbackValue.LIKE, null, NOW);

        assertTrue(feedback.getRating().isEmpty());
    }

    @Test
    void rejectsEmptyInvalidAndContradictoryFeedback() {
        UUID recipeId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeFeedback(recipeId, null, null, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeFeedback(recipeId, null, 0, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeFeedback(recipeId, null, 6, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeFeedback(recipeId, RecipeFeedbackValue.LIKE, 3, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeFeedback(recipeId, RecipeFeedbackValue.DISLIKE, 3, NOW));
    }

    @Test
    void equalityUsesStableFeedbackIdentity() {
        UUID id = UUID.randomUUID();
        UUID recipeId = UUID.randomUUID();
        RecipeFeedback first = new RecipeFeedback(
                id, recipeId, RecipeFeedbackValue.LIKE, 4, NOW);
        RecipeFeedback updated = new RecipeFeedback(
                id, recipeId, RecipeFeedbackValue.LIKE, 5, NOW.plusSeconds(1));

        assertEquals(first, updated);
        assertEquals(first.hashCode(), updated.hashCode());
    }
}
