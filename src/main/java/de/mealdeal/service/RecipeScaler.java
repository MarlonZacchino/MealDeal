package de.mealdeal.service;

import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.RecipeIngredientGroup;
import de.mealdeal.domain.RecipeIngredientOption;

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
        return scaleIngredientGroups(recipe, requestedServingCount).stream()
                .map(group -> group.getStandardOption())
                .map(option -> new RecipeIngredient(option.getIngredient(), option.getQuantity(),
                        option.getUnit()))
                .toList();
    }

    /** Scales the concrete per-group options resolved by one meal-plan entry. */
    public List<RecipeIngredient> scale(MealPlanEntry entry) {
        Objects.requireNonNull(entry, "Meal plan entry must not be null.");
        BigDecimal requested = BigDecimal.valueOf(entry.getServingCount());
        BigDecimal standard = BigDecimal.valueOf(
                entry.getRecipe().getStandardServingCount());
        return entry.getSelectedIngredientOptions().stream()
                .map(option -> scaleOption(option, requested, standard))
                .map(option -> new RecipeIngredient(option.getIngredient(), option.getQuantity(),
                        option.getUnit()))
                .toList();
    }

    /**
     * Scales every option in every ingredient group while retaining group, option,
     * default and ordering identities.
     */
    public List<RecipeIngredientGroup> scaleIngredientGroups(
            Recipe recipe, int requestedServingCount) {
        Objects.requireNonNull(recipe, "Recipe must not be null.");
        if (requestedServingCount <= 0) {
            throw new IllegalArgumentException("Requested serving count must be greater than zero.");
        }

        BigDecimal requested = BigDecimal.valueOf(requestedServingCount);
        BigDecimal standard = BigDecimal.valueOf(recipe.getStandardServingCount());

        return recipe.getIngredientGroups().stream()
                .map(group -> scaleGroup(group, requested, standard))
                .toList();
    }

    private static RecipeIngredientGroup scaleGroup(RecipeIngredientGroup group,
                                                     BigDecimal requested,
                                                     BigDecimal standard) {
        List<RecipeIngredientOption> options = group.getOptions().stream()
                .map(option -> scaleOption(option, requested, standard))
                .toList();
        return new RecipeIngredientGroup(group.getId(), options, group.getStandardOptionId());
    }

    private static RecipeIngredientOption scaleOption(
            RecipeIngredientOption option, BigDecimal requested, BigDecimal standard) {
        BigDecimal scaledAmount = option.getQuantity()
                .multiply(requested)
                .divide(standard, CALCULATION_CONTEXT);
        return new RecipeIngredientOption(option.getId(), option.getIngredient(), scaledAmount,
                option.getUnit(), option.getPosition());
    }
}
