package de.mealdeal.service.recommendation;

import java.util.List;
import java.util.Objects;

/** Complete deterministic output including ranked and ineligible candidates. */
public record RecommendationOutcome(
        List<RecipeRecommendation> recommendations,
        List<RecommendationExclusion> exclusions) {

    public RecommendationOutcome {
        recommendations = immutable(recommendations, "Recommendations");
        exclusions = immutable(exclusions, "Recommendation exclusions");
    }

    private static <T> List<T> immutable(List<T> values, String label) {
        Objects.requireNonNull(values, label + " must not be null.");
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(label + " must not contain null values.");
        }
        return List.copyOf(values);
    }
}
