package de.mealdeal.service.recommendation;

import de.mealdeal.domain.Recipe;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Ranked recipe plus normalized signals and machine-readable explanations. */
public record RecipeRecommendation(
        Recipe recipe,
        BigDecimal score,
        RecommendationBand band,
        RecommendationSignals signals,
        int missingIngredientGroupCount,
        Optional<Duration> relevantDuration,
        Map<UUID, UUID> suggestedIngredientOptions,
        List<RecommendationReasonCode> reasonCodes) {

    public RecipeRecommendation {
        recipe = Objects.requireNonNull(recipe, "Recipe must not be null.");
        score = Objects.requireNonNull(score, "Recommendation score must not be null.");
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Recommendation score must be between 0 and 1.");
        }
        band = Objects.requireNonNull(band, "Recommendation band must not be null.");
        signals = Objects.requireNonNull(signals, "Recommendation signals must not be null.");
        if (missingIngredientGroupCount < 0) {
            throw new IllegalArgumentException("Missing ingredient count must not be negative.");
        }
        relevantDuration = Objects.requireNonNull(
                relevantDuration, "Relevant duration must not be null.");
        Objects.requireNonNull(
                suggestedIngredientOptions, "Suggested ingredient options must not be null.");
        if (suggestedIngredientOptions.entrySet().stream()
                .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
            throw new IllegalArgumentException(
                    "Suggested ingredient options must not contain null values.");
        }
        suggestedIngredientOptions = Collections.unmodifiableMap(
                new LinkedHashMap<>(suggestedIngredientOptions));
        reasonCodes = List.copyOf(Objects.requireNonNull(
                reasonCodes, "Recommendation reason codes must not be null."));
        if (reasonCodes.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Recommendation reason codes must not contain null values.");
        }
    }
}
