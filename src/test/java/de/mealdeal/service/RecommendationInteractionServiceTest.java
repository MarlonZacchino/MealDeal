package de.mealdeal.service;

import de.mealdeal.domain.RecommendationAction;
import de.mealdeal.domain.RecommendationInteraction;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.Taste;
import de.mealdeal.persistence.repository.RecommendationInteractionRepository;
import de.mealdeal.service.recommendation.RecipeRecommendation;
import de.mealdeal.service.recommendation.RecommendationBand;
import de.mealdeal.service.recommendation.RecommendationSignals;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecommendationInteractionServiceTest {

    @Test
    void snapshotsActualR0RecipeScoreAndDisplayedRank() {
        RecordingRepository repository = new RecordingRepository();
        Instant now = Instant.parse("2026-09-04T12:00:00Z");
        RecommendationInteractionService service = new RecommendationInteractionService(
                repository, Clock.fixed(now, ZoneOffset.UTC));
        Recipe recipe = new Recipe("Suppe", 2, List.of(), List.of(),
                List.of(new Taste("Herzhaft")));
        RecipeRecommendation recommendation = new RecipeRecommendation(
                recipe, new BigDecimal("0.734500"), RecommendationBand.GUT_PASSEND,
                new RecommendationSignals(Map.of()), 0, Optional.empty(), Map.of(), List.of());
        UUID sessionId = UUID.randomUUID();

        RecommendationInteraction interaction = service.record(
                sessionId, recommendation, RecommendationAction.SELECTED, 3);

        assertEquals(interaction, repository.saved.getFirst());
        assertEquals(sessionId, interaction.getRecommendationSessionId());
        assertEquals(recipe.getId(), interaction.getRecipeId());
        assertEquals(new BigDecimal("0.734500"), interaction.getDisplayedScore());
        assertEquals(3, interaction.getDisplayedRank());
        assertEquals(now, interaction.getOccurredAt());
    }

    private static final class RecordingRepository
            implements RecommendationInteractionRepository {
        private final List<RecommendationInteraction> saved = new ArrayList<>();

        @Override public void save(RecommendationInteraction interaction) {
            saved.add(interaction);
        }
        @Override public List<RecommendationInteraction> findBySessionId(UUID sessionId) {
            return List.of();
        }
        @Override public List<RecommendationInteraction> findByRecipeId(UUID recipeId) {
            return List.of();
        }
        @Override public List<RecommendationInteraction> findRecent(int limit) {
            return List.of();
        }
        @Override public boolean deleteById(UUID id) {
            return false;
        }
    }
}
