package de.mealdeal.service.recommendation;

import de.mealdeal.domain.DishType;
import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.InventoryItem;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredientGroup;
import de.mealdeal.domain.RecipeIngredientOption;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static de.mealdeal.service.recommendation.RecommendationTestFixtures.group;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.ingredient;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.option;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.recipe;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.stock;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.taste;

/** Small, readable fixtures shared only by the R4 offline evaluation tests. */
final class R4EvaluationFixtures {

    static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");
    static final Taste SAVORY = taste("R4 Herzhaft");
    static final Taste SWEET = taste("R4 Süß");

    private R4EvaluationFixtures() {
    }

    static Recipe candidate(String key, String name) {
        return candidate(key, name, SAVORY, null, 1);
    }

    static Recipe candidate(String key, String name, Taste recipeTaste, Integer minutes) {
        return candidate(key, name, recipeTaste, minutes, 1);
    }

    static Recipe candidate(
            String key, String name, Taste recipeTaste, Integer minutes, int groupCount) {
        List<RecipeIngredientGroup> groups = new ArrayList<>();
        for (int index = 0; index < groupCount; index++) {
            Ingredient ingredient = ingredient("R4 " + key + " " + index);
            RecipeIngredientOption ingredientOption = option(
                    key + "-" + index, ingredient, "100", Unit.GRAM, 0);
            groups.add(group(key + "-" + index, List.of(ingredientOption), 0));
        }
        return recipe(key, name, groups, List.of(recipeTaste), minutes, null, null, null,
                DishType.MAIN);
    }

    static Recipe alternativeCandidate(
            String key, String name, Ingredient standard, Ingredient alternative) {
        RecipeIngredientOption standardOption = option(
                key + "-standard", standard, "100", Unit.GRAM, 0);
        RecipeIngredientOption alternativeOption = option(
                key + "-alternative", alternative, "100", Unit.GRAM, 1);
        return recipe(key, name, List.of(group(
                key, List.of(standardOption, alternativeOption), 0)), SAVORY);
    }

    static List<InventoryItem> stockFor(Recipe recipe, String quantityPerGroup) {
        return recipe.getIngredientGroups().stream()
                .map(group -> stock(
                        "r4-stock-" + recipe.getId() + "-" + group.getId(),
                        group.getStandardOption().getIngredient(), quantityPerGroup, Unit.GRAM))
                .toList();
    }

    static List<InventoryItem> stockForAll(String quantityPerGroup, Recipe... recipes) {
        return java.util.Arrays.stream(recipes)
                .flatMap(recipe -> stockFor(recipe, quantityPerGroup).stream())
                .toList();
    }

    static RecommendationContext pantryContext(List<InventoryItem> inventory) {
        return RecommendationContext.pantryOnly(2, inventory);
    }

    static RecommendationContext personalizedContext(
            List<InventoryItem> inventory,
            TastePreferenceProfile tastePreferences,
            List<HouseholdMemberPreference> household,
            RecommendationRequest request,
            Map<UUID, RecipePersonalizationSignals> personalization) {
        return new RecommendationContext(
                request, inventory, tastePreferences, household,
                RecommendationConstraints.none(), personalization);
    }

    static RecipePersonalizationSignals signals(
            Recipe recipe, String freshness, String explicit, String implicit,
            int selected, int dismissed) {
        BigDecimal freshnessValue = new BigDecimal(freshness);
        Optional<BigDecimal> explicitValue = explicit == null
                ? Optional.empty() : Optional.of(new BigDecimal(explicit));
        BigDecimal implicitValue = new BigDecimal(implicit);
        RecipeRecency recency;
        Optional<Instant> lastCooked;
        if (freshnessValue.compareTo(BigDecimal.ONE) == 0) {
            recency = RecipeRecency.NEVER_COOKED;
            lastCooked = Optional.empty();
        } else if (freshnessValue.signum() == 0) {
            recency = RecipeRecency.COOKED_TODAY;
            lastCooked = Optional.of(NOW);
        } else if (freshnessValue.compareTo(new BigDecimal("0.5")) < 0) {
            recency = RecipeRecency.COOKED_RECENTLY;
            lastCooked = Optional.of(NOW.minusSeconds(86_400));
        } else {
            recency = RecipeRecency.MID_WINDOW;
            lastCooked = Optional.of(NOW.minusSeconds(7 * 86_400));
        }
        return new RecipePersonalizationSignals(
                recipe.getId(), lastCooked, recency, freshnessValue,
                explicitValue, implicitValue, explicitValue.orElse(implicitValue),
                selected, dismissed);
    }

    static HouseholdMemberPreference member(String id, Taste memberTaste, String affinity) {
        return new HouseholdMemberPreference(
                id,
                new TastePreferenceProfile(Map.of(
                        memberTaste.getId(), new BigDecimal(affinity))),
                RecommendationConstraints.none());
    }

    static BigDecimal signalContribution(
            RecipeRecommendation recommendation, RecommendationSignal signal) {
        BigDecimal value = recommendation.signals().valueOf(signal).orElseThrow();
        if (signal == RecommendationSignal.MISSING_INGREDIENT_PENALTY
                || signal == RecommendationSignal.RECENT_MEAL_PENALTY) {
            value = BigDecimal.ONE.subtract(value);
        }
        return value.multiply(RecommendationScoringProfile.v1().weightOf(signal));
    }
}
