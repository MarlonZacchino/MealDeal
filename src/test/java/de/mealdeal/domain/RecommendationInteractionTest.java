package de.mealdeal.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecommendationInteractionTest {

    private static final Instant NOW = Instant.parse("2026-09-02T10:00:00Z");

    @Test
    void keepsSessionRecipeActionRankScoreAndStableIdentity() {
        UUID id = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID recipeId = UUID.randomUUID();

        for (RecommendationAction action : RecommendationAction.values()) {
            RecommendationInteraction interaction = new RecommendationInteraction(
                    id, sessionId, recipeId, action, 2, new BigDecimal("0.7500"), NOW);
            assertEquals(id, interaction.getId());
            assertEquals(sessionId, interaction.getRecommendationSessionId());
            assertEquals(recipeId, interaction.getRecipeId());
            assertEquals(action, interaction.getAction());
            assertEquals(2, interaction.getDisplayedRank());
            assertEquals(new BigDecimal("0.7500"), interaction.getDisplayedScore());
            assertEquals(NOW, interaction.getOccurredAt());
        }
    }

    @Test
    void rejectsInvalidRankAndScoreOutsideR0Range() {
        UUID sessionId = UUID.randomUUID();
        UUID recipeId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new RecommendationInteraction(
                sessionId, recipeId, RecommendationAction.SHOWN, 0, BigDecimal.ONE, NOW));
        assertThrows(IllegalArgumentException.class, () -> new RecommendationInteraction(
                sessionId, recipeId, RecommendationAction.SHOWN, 1,
                new BigDecimal("-0.01"), NOW));
        assertThrows(IllegalArgumentException.class, () -> new RecommendationInteraction(
                sessionId, recipeId, RecommendationAction.SHOWN, 1,
                new BigDecimal("1.01"), NOW));
    }

    @Test
    void requiresEveryCoreField() {
        UUID id = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID recipeId = UUID.randomUUID();
        assertThrows(NullPointerException.class, () -> new RecommendationInteraction(
                null, sessionId, recipeId, RecommendationAction.SHOWN, 1,
                BigDecimal.ONE, NOW));
        assertThrows(NullPointerException.class, () -> new RecommendationInteraction(
                id, null, recipeId, RecommendationAction.SHOWN, 1,
                BigDecimal.ONE, NOW));
        assertThrows(NullPointerException.class, () -> new RecommendationInteraction(
                id, sessionId, null, RecommendationAction.SHOWN, 1,
                BigDecimal.ONE, NOW));
        assertThrows(NullPointerException.class, () -> new RecommendationInteraction(
                id, sessionId, recipeId, null, 1, BigDecimal.ONE, NOW));
        assertThrows(NullPointerException.class, () -> new RecommendationInteraction(
                id, sessionId, recipeId, RecommendationAction.SHOWN, 1, null, NOW));
        assertThrows(NullPointerException.class, () -> new RecommendationInteraction(
                id, sessionId, recipeId, RecommendationAction.SHOWN, 1,
                BigDecimal.ONE, null));
    }
}
