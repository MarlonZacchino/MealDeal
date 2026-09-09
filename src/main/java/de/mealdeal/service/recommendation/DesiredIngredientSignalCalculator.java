package de.mealdeal.service.recommendation;

import de.mealdeal.domain.Recipe;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Calculates the optional soft fit of locally identified desired Ingredients. */
final class DesiredIngredientSignalCalculator {

    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;

    Optional<BigDecimal> score(DesiredIngredientProfile profile, Recipe recipe) {
        Objects.requireNonNull(profile, "Desired ingredient profile must not be null.");
        Objects.requireNonNull(recipe, "Recipe must not be null.");
        if (profile.isEmpty()) {
            return Optional.empty();
        }
        long matched = profile.ingredientIds().stream()
                .filter(ingredientId -> containsIngredient(recipe, ingredientId))
                .count();
        return Optional.of(BigDecimal.valueOf(matched)
                .divide(BigDecimal.valueOf(profile.ingredientIds().size()),
                        CALCULATION_CONTEXT));
    }

    private static boolean containsIngredient(Recipe recipe, UUID ingredientId) {
        return recipe.getIngredientGroups().stream()
                .flatMap(group -> group.getOptions().stream())
                .anyMatch(option -> option.getIngredient().getId().equals(ingredientId));
    }
}
