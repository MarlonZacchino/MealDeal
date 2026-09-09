package de.mealdeal.service.recommendation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable, persistence-free personalization snapshot for one local Recipe.
 *
 * <p>The signed preference values use {@code -1..1}; the normalized score input is
 * derived centrally so persisted feedback never leaks into the pure scorer.</p>
 */
public record RecipePersonalizationSignals(
        UUID recipeId,
        Optional<Instant> lastCookedAt,
        RecipeRecency recency,
        BigDecimal freshness,
        Optional<BigDecimal> explicitPreference,
        BigDecimal implicitInteractionPreference,
        BigDecimal effectivePreference,
        int selectedCount,
        int dismissedCount) {

    public RecipePersonalizationSignals {
        recipeId = Objects.requireNonNull(recipeId, "Recipe ID must not be null.");
        lastCookedAt = Objects.requireNonNull(
                lastCookedAt, "Last-cooked timestamp must not be null.");
        recency = Objects.requireNonNull(recency, "Recipe recency must not be null.");
        if (lastCookedAt.isEmpty() != (recency == RecipeRecency.NEVER_COOKED)) {
            throw new IllegalArgumentException(
                    "Never-cooked recency must match absence of meal history.");
        }
        freshness = normalized(freshness, "Freshness");
        explicitPreference = Objects.requireNonNull(
                explicitPreference, "Explicit preference must not be null.")
                .map(value -> signed(value, "Explicit preference"));
        implicitInteractionPreference = signed(
                implicitInteractionPreference, "Interaction preference");
        if (implicitInteractionPreference.compareTo(new BigDecimal("-0.5")) < 0
                || implicitInteractionPreference.compareTo(new BigDecimal("0.5")) > 0) {
            throw new IllegalArgumentException(
                    "Interaction preference must be between -0.5 and 0.5.");
        }
        effectivePreference = signed(effectivePreference, "Effective preference");
        BigDecimal expected = explicitPreference.orElse(implicitInteractionPreference);
        if (effectivePreference.compareTo(expected) != 0) {
            throw new IllegalArgumentException(
                    "Effective preference must use explicit feedback or interaction fallback.");
        }
        if (selectedCount < 0 || dismissedCount < 0) {
            throw new IllegalArgumentException(
                    "Interaction counts must not be negative.");
        }
    }

    /** Maps the signed effective preference to the scorer's normalized {@code 0..1} range. */
    public BigDecimal normalizedEffectivePreference() {
        return effectivePreference.add(BigDecimal.ONE)
                .divide(BigDecimal.valueOf(2));
    }

    public boolean hasExplicitFeedback() {
        return explicitPreference.isPresent();
    }

    public boolean wasNeverCooked() {
        return lastCookedAt.isEmpty();
    }

    /** Returns whether Meal History places the latest confirmed cooking on today's local date. */
    public boolean wasCookedToday() {
        return recency == RecipeRecency.COOKED_TODAY;
    }

    private static BigDecimal normalized(BigDecimal value, String label) {
        Objects.requireNonNull(value, label + " must not be null.");
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(label + " must be between 0 and 1.");
        }
        return value;
    }

    private static BigDecimal signed(BigDecimal value, String label) {
        Objects.requireNonNull(value, label + " must not be null.");
        if (value.compareTo(BigDecimal.ONE.negate()) < 0
                || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(label + " must be between -1 and 1.");
        }
        return value;
    }
}
