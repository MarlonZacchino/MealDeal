package de.mealdeal.service;

import de.mealdeal.domain.RecommendationAction;
import de.mealdeal.domain.RecommendationInteraction;
import de.mealdeal.persistence.repository.RecommendationInteractionRepository;
import de.mealdeal.service.recommendation.RecipeRecommendation;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Records only explicit, locally observed interactions with R0 recommendation results. */
public final class RecommendationInteractionService {

    private final RecommendationInteractionRepository repository;
    private final Clock clock;

    public RecommendationInteractionService(RecommendationInteractionRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    public RecommendationInteractionService(
            RecommendationInteractionRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "Repository must not be null.");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null.");
    }

    /** Captures the score and rank exactly as they were displayed in one calculation session. */
    public RecommendationInteraction record(UUID sessionId,
                                            RecipeRecommendation recommendation,
                                            RecommendationAction action,
                                            int displayedRank) {
        Objects.requireNonNull(recommendation, "Recommendation must not be null.");
        RecommendationInteraction interaction = new RecommendationInteraction(
                sessionId, recommendation.recipe().getId(), action, displayedRank,
                recommendation.score(), clock.instant());
        repository.save(interaction);
        return interaction;
    }

    public List<RecommendationInteraction> findBySessionId(UUID sessionId) {
        return repository.findBySessionId(Objects.requireNonNull(
                sessionId, "Recommendation session ID must not be null."));
    }

    public List<RecommendationInteraction> findByRecipeId(UUID recipeId) {
        return repository.findByRecipeId(Objects.requireNonNull(
                recipeId, "Recipe ID must not be null."));
    }

    public List<RecommendationInteraction> findRecent(int limit) {
        return repository.findRecent(limit);
    }

    public boolean delete(UUID interactionId) {
        return repository.deleteById(Objects.requireNonNull(
                interactionId, "Recommendation interaction ID must not be null."));
    }
}
