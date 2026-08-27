package de.mealdeal.service;

import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredient;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Objects;

/**
 * Calculates recipe ingredient amounts for a requested serving count.
 *
 * <p>The calculation creates new immutable ingredient entries. It never
 * changes the stored recipe or its standard serving count.</p>
 */
public final class RecipeScaler {

    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;

    /**
     * Scales all ingredients using
     * {@code base amount × requested servings ÷ standard servings}.
     *
     * @param recipe source recipe that remains unchanged
     * @param requestedServingCount positive requested serving count
     * @return immutable list of newly created scaled ingredient entries
     */
    public List<RecipeIngredient> scale(Recipe recipe, int requestedServingCount) {
        Objects.requireNonNull(recipe, "Recipe must not be null.");
        if (requestedServingCount <= 0) {
            throw new IllegalArgumentException("Requested serving count must be greater than zero.");
        }

        BigDecimal requested = BigDecimal.valueOf(requestedServingCount);
        BigDecimal standard = BigDecimal.valueOf(recipe.getStandardServingCount());

        return recipe.getIngredients().stream()
                .map(ingredient -> scaleIngredient(ingredient, requested, standard))
                .toList();
    }

    private static RecipeIngredient scaleIngredient(
            RecipeIngredient ingredient, BigDecimal requested, BigDecimal standard) {
        BigDecimal scaledAmount = ingredient.getQuantity()
                .multiply(requested)
                .divide(standard, CALCULATION_CONTEXT);
        return new RecipeIngredient(ingredient.getIngredient(), scaledAmount, ingredient.getUnit());
    }
}
