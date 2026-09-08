package de.mealdeal.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable local event describing an explicit interaction with a ranked recommendation.
 * Non-interaction is intentionally never inferred as a negative event.
 */
public final class RecommendationInteraction {

    private final UUID id;
    private final UUID recommendationSessionId;
    private final UUID recipeId;
    private final RecommendationAction action;
    private final int displayedRank;
    private final BigDecimal displayedScore;
    private final Instant occurredAt;

    public RecommendationInteraction(UUID recommendationSessionId, UUID recipeId,
                                     RecommendationAction action, int displayedRank,
                                     BigDecimal displayedScore, Instant occurredAt) {
        this(UUID.randomUUID(), recommendationSessionId, recipeId, action, displayedRank,
                displayedScore, occurredAt);
    }

    /** Recreates an interaction with its stable event identity. */
    public RecommendationInteraction(UUID id, UUID recommendationSessionId, UUID recipeId,
                                     RecommendationAction action, int displayedRank,
                                     BigDecimal displayedScore, Instant occurredAt) {
        this.id = Objects.requireNonNull(id, "Recommendation interaction ID must not be null.");
        this.recommendationSessionId = Objects.requireNonNull(
                recommendationSessionId, "Recommendation session ID must not be null.");
        this.recipeId = Objects.requireNonNull(recipeId, "Recipe ID must not be null.");
        this.action = Objects.requireNonNull(action, "Recommendation action must not be null.");
        if (displayedRank <= 0) {
            throw new IllegalArgumentException("Displayed rank must be greater than zero.");
        }
        this.displayedRank = displayedRank;
        this.displayedScore = Objects.requireNonNull(
                displayedScore, "Displayed score must not be null.");
        if (displayedScore.compareTo(BigDecimal.ZERO) < 0
                || displayedScore.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Displayed score must be between 0 and 1.");
        }
        this.occurredAt = Objects.requireNonNull(
                occurredAt, "Recommendation interaction timestamp must not be null.");
    }

    public UUID getId() { return id; }
    public UUID getRecommendationSessionId() { return recommendationSessionId; }
    public UUID getRecipeId() { return recipeId; }
    public RecommendationAction getAction() { return action; }
    public int getDisplayedRank() { return displayedRank; }
    public BigDecimal getDisplayedScore() { return displayedScore; }
    public Instant getOccurredAt() { return occurredAt; }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof RecommendationInteraction interaction
                && id.equals(interaction.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
