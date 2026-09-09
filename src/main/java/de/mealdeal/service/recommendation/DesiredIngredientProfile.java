package de.mealdeal.service.recommendation;

import de.mealdeal.domain.Ingredient;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Immutable local-identity snapshot of ingredients desired for one recommendation request.
 *
 * <p>These identities express a soft preference and are deliberately independent of the
 * inventory snapshot. An empty profile deactivates the signal entirely.</p>
 */
public record DesiredIngredientProfile(Set<UUID> ingredientIds) {

    public DesiredIngredientProfile {
        Objects.requireNonNull(ingredientIds, "Desired ingredient IDs must not be null.");
        if (ingredientIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Desired ingredient IDs must not contain null values.");
        }
        ingredientIds = Set.copyOf(new TreeSet<>(ingredientIds));
    }

    /** Creates a deterministic profile from the selected central Ingredients. */
    public static DesiredIngredientProfile fromIngredients(Collection<Ingredient> ingredients) {
        Objects.requireNonNull(ingredients, "Desired ingredients must not be null.");
        if (ingredients.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Desired ingredients must not contain null values.");
        }
        return new DesiredIngredientProfile(ingredients.stream()
                .map(Ingredient::getId).collect(java.util.stream.Collectors.toSet()));
    }

    public static DesiredIngredientProfile empty() {
        return new DesiredIngredientProfile(Set.of());
    }

    public boolean isEmpty() {
        return ingredientIds.isEmpty();
    }
}
