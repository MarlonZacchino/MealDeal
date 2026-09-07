package de.mealdeal.service.recommendation;

import de.mealdeal.domain.Recipe;

import java.util.List;
import java.util.Objects;

/** Recipe removed from the candidate set before scoring. */
public record RecommendationExclusion(
        Recipe recipe,
        List<RecommendationReasonCode> reasonCodes) {

    public RecommendationExclusion {
        recipe = Objects.requireNonNull(recipe, "Recipe must not be null.");
        reasonCodes = List.copyOf(Objects.requireNonNull(
                reasonCodes, "Exclusion reason codes must not be null."));
        if (reasonCodes.isEmpty()) {
            throw new IllegalArgumentException("Recommendation exclusion needs a reason.");
        }
        if (reasonCodes.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Exclusion reason codes must not contain null values.");
        }
    }
}
