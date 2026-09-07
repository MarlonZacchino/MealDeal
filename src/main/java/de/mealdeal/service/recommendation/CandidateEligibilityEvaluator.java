package de.mealdeal.service.recommendation;

import de.mealdeal.domain.Recipe;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Applies request and household hard constraints before scoring. */
final class CandidateEligibilityEvaluator {

    CandidateEligibility evaluate(Recipe recipe, RecommendationContext context) {
        Objects.requireNonNull(recipe, "Recipe must not be null.");
        Objects.requireNonNull(context, "Recommendation context must not be null.");
        Set<RecommendationReasonCode> reasons = new LinkedHashSet<>();
        if (recipe.getIngredientGroups().isEmpty()) {
            reasons.add(RecommendationReasonCode.MISSING_REQUIRED_INGREDIENT_STRUCTURE);
        }
        context.request().desiredDishType()
                .filter(dishType -> dishType != recipe.getDishType())
                .ifPresent(ignored -> reasons.add(RecommendationReasonCode.DISH_TYPE_MISMATCH));
        if (allConstraints(context).stream()
                .anyMatch(constraints -> constraints.excludedRecipeIds().contains(recipe.getId()))) {
            reasons.add(RecommendationReasonCode.RECIPE_HARD_EXCLUDED);
        }
        Set<UUID> excludedIngredients = excludedIngredientIds(context);
        if (recipe.getIngredientGroups().stream().anyMatch(group -> group.getOptions().stream()
                .allMatch(option -> excludedIngredients.contains(option.getIngredient().getId())))) {
            reasons.add(RecommendationReasonCode.INGREDIENT_GROUP_HARD_EXCLUDED);
        }
        return reasons.isEmpty()
                ? CandidateEligibility.allowed()
                : CandidateEligibility.excluded(List.copyOf(reasons));
    }

    Set<UUID> excludedIngredientIds(RecommendationContext context) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        allConstraints(context).stream()
                .map(RecommendationConstraints::excludedIngredientIds)
                .forEach(ids::addAll);
        return Set.copyOf(ids);
    }

    boolean hasIgnoredExcludedAlternative(Recipe recipe, Set<UUID> excludedIngredientIds) {
        return recipe.getIngredientGroups().stream().anyMatch(group ->
                group.getOptions().stream().anyMatch(option -> excludedIngredientIds.contains(
                        option.getIngredient().getId()))
                        && group.getOptions().stream().anyMatch(option -> !excludedIngredientIds
                        .contains(option.getIngredient().getId())));
    }

    private static List<RecommendationConstraints> allConstraints(
            RecommendationContext context) {
        List<RecommendationConstraints> constraints = new ArrayList<>();
        constraints.add(context.hardConstraints());
        context.householdPreferences().stream()
                .map(HouseholdMemberPreference::hardConstraints)
                .forEach(constraints::add);
        return List.copyOf(constraints);
    }
}
