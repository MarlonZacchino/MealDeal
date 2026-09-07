package de.mealdeal.service.recommendation;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Hard recipe and ingredient exclusions that are evaluated before ranking. */
public record RecommendationConstraints(
        Set<UUID> excludedRecipeIds,
        Set<UUID> excludedIngredientIds) {

    public RecommendationConstraints {
        excludedRecipeIds = immutableIds(excludedRecipeIds, "Excluded recipe IDs");
        excludedIngredientIds = immutableIds(excludedIngredientIds, "Excluded ingredient IDs");
    }

    public static RecommendationConstraints none() {
        return new RecommendationConstraints(Set.of(), Set.of());
    }

    private static Set<UUID> immutableIds(Set<UUID> ids, String label) {
        Objects.requireNonNull(ids, label + " must not be null.");
        if (ids.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(label + " must not contain null values.");
        }
        return Set.copyOf(ids);
    }
}
