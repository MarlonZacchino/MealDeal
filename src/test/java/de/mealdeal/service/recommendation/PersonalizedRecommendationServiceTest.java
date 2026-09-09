package de.mealdeal.service.recommendation;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.InventoryItem;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalizedRecommendationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");
    private final RecipeRecommendationService scorer = new RecipeRecommendationService();

    @Test
    void yesterdayCookedRecipeRemainsCandidateAndRecencyStillRanksItLower() {
        Recipe recent = candidate("recent", "Alpha Recent");
        Recipe fresh = candidate("fresh", "Zulu Fresh");
        RecommendationContext context = context(List.of(recent, fresh), Map.of(
                recent.getId(), signals(recent, RecipeRecency.COOKED_RECENTLY,
                        "0.0714285714285714", null, "0", 0, 0),
                fresh.getId(), signals(fresh, RecipeRecency.NEVER_COOKED, "1", null, "0", 0, 0)));

        RecommendationOutcome result = scorer.recommend(List.of(recent, fresh), context);

        assertEquals(fresh, result.recommendations().getFirst().recipe());
        assertTrue(result.recommendations().getFirst().reasonCodes().contains(
                RecommendationReasonCode.RECIPE_NEVER_COOKED));
        assertTrue(result.recommendations().get(1).reasonCodes().contains(
                RecommendationReasonCode.RECENTLY_COOKED));
        assertTrue(result.exclusions().isEmpty());
    }

    @Test
    void todayCookedRecipeIsExcludedBeforeScoringWhileOtherRecipesRemainUnchanged() {
        Recipe cookedToday = candidate("today", "Alpha Today");
        Recipe other = candidate("other", "Zulu Other");
        RecommendationContext context = context(List.of(cookedToday, other), Map.of(
                cookedToday.getId(), signals(cookedToday, RecipeRecency.COOKED_TODAY,
                        "0", null, "0", 0, 0),
                other.getId(), signals(other, RecipeRecency.NEVER_COOKED,
                        "1", null, "0", 0, 0)));

        RecommendationOutcome result = scorer.recommend(List.of(cookedToday, other), context);

        assertEquals(List.of(other), result.recommendations().stream()
                .map(RecipeRecommendation::recipe).toList());
        assertEquals(cookedToday, result.exclusions().getFirst().recipe());
        assertTrue(result.exclusions().getFirst().reasonCodes()
                .contains(RecommendationReasonCode.RECIPE_COOKED_TODAY));
    }

    @Test
    void explicitLikeAndDislikeMoveOtherwiseEqualRankingPredictably() {
        Recipe liked = candidate("liked", "Zulu Liked");
        Recipe neutral = candidate("neutral", "Middle Neutral");
        Recipe disliked = candidate("disliked", "Alpha Disliked");
        RecommendationContext context = context(List.of(liked, neutral, disliked), Map.of(
                liked.getId(), signals(liked, RecipeRecency.NEVER_COOKED, "1", "0.75", "0", 0, 0),
                neutral.getId(), signals(neutral, RecipeRecency.NEVER_COOKED, "1", null, "0", 0, 0),
                disliked.getId(), signals(disliked, RecipeRecency.NEVER_COOKED, "1", "-0.75", "0", 0, 0)));

        List<RecipeRecommendation> results = scorer.recommend(
                List.of(disliked, neutral, liked), context).recommendations();

        assertEquals(List.of(liked, neutral, disliked),
                results.stream().map(RecipeRecommendation::recipe).toList());
        assertTrue(results.getFirst().reasonCodes().contains(
                RecommendationReasonCode.RECIPE_EXPLICITLY_LIKED));
        assertTrue(results.getLast().reasonCodes().contains(
                RecommendationReasonCode.RECIPE_EXPLICITLY_DISLIKED));
    }

    @Test
    void personalizationCannotOvercomeLargePantryQualityDifference() {
        Recipe strong = candidate("strong", "Strong");
        Recipe weakLiked = candidate("weak", "Weak Liked");
        InventoryItem onlyStrongStock = stock("strong-stock",
                ingredientOf(strong), "100", Unit.GRAM);
        RecommendationContext base = RecommendationContext.pantryOnly(2,
                List.of(onlyStrongStock));
        RecommendationContext context = base.withPersonalization(Map.of(
                strong.getId(), signals(strong, RecipeRecency.NEVER_COOKED, "1", null, "0", 0, 0),
                weakLiked.getId(), signals(weakLiked, RecipeRecency.NEVER_COOKED, "1", "1", "0", 0, 0)));

        List<RecipeRecommendation> results = scorer.recommend(
                List.of(weakLiked, strong), context).recommendations();

        assertEquals(strong, results.getFirst().recipe());
        assertTrue(results.getFirst().score().compareTo(results.getLast().score()) > 0);
    }

    @Test
    void shownIsNeutralAndImplicitPreferenceIsWeakerThanExplicitFeedback() {
        Recipe explicit = candidate("explicit", "Explicit");
        Recipe implicit = candidate("implicit", "Implicit");
        Recipe shownOnly = candidate("shown", "Shown");
        RecommendationContext context = context(List.of(explicit, implicit, shownOnly), Map.of(
                explicit.getId(), signals(explicit, RecipeRecency.NEVER_COOKED, "1", "0.75", "0", 0, 0),
                implicit.getId(), signals(implicit, RecipeRecency.NEVER_COOKED, "1", null, "0.25", 2, 0),
                shownOnly.getId(), signals(shownOnly, RecipeRecency.NEVER_COOKED, "1", null, "0", 0, 0)));

        List<RecipeRecommendation> results = scorer.recommend(
                List.of(shownOnly, implicit, explicit), context).recommendations();

        assertEquals(List.of(explicit, implicit, shownOnly),
                results.stream().map(RecipeRecommendation::recipe).toList());
        assertTrue(results.get(1).reasonCodes().contains(
                RecommendationReasonCode.RECIPE_REPEATEDLY_SELECTED));
        assertFalse(results.getLast().reasonCodes().stream().anyMatch(code ->
                code == RecommendationReasonCode.RECIPE_REPEATEDLY_SELECTED
                        || code == RecommendationReasonCode.RECIPE_REPEATEDLY_DISMISSED));
    }

    @Test
    void selectedIsNotCookedAndCookedIsNotLiked() {
        Recipe selected = candidate("selected-only", "Selected only");
        Recipe cooked = candidate("cooked-only", "Cooked only");
        RecommendationContext context = context(List.of(selected, cooked), Map.of(
                selected.getId(), signals(selected, RecipeRecency.NEVER_COOKED, "1", null,
                        "0.1666666666666666666666666666666667", 1, 0),
                cooked.getId(), signals(cooked, RecipeRecency.COOKED_RECENTLY, "0.25", null,
                        "0", 0, 0)));

        Map<UUID, RecipeRecommendation> results = scorer.recommend(
                        List.of(selected, cooked), context).recommendations().stream()
                .collect(java.util.stream.Collectors.toMap(
                        result -> result.recipe().getId(), result -> result));

        assertTrue(results.get(selected.getId()).reasonCodes().contains(
                RecommendationReasonCode.RECIPE_NEVER_COOKED));
        assertFalse(results.get(selected.getId()).reasonCodes().contains(
                RecommendationReasonCode.RECENTLY_COOKED));
        assertTrue(results.get(cooked.getId()).reasonCodes().contains(
                RecommendationReasonCode.RECENTLY_COOKED));
        assertFalse(results.get(cooked.getId()).reasonCodes().stream().anyMatch(code ->
                code == RecommendationReasonCode.RECIPE_EXPLICITLY_LIKED
                        || code == RecommendationReasonCode.RECIPE_REPEATEDLY_SELECTED));
    }

    @Test
    void samePersonalizedInputsRemainExactlyDeterministic() {
        Recipe beta = candidate("det-beta", "Beta");
        Recipe alpha = candidate("det-alpha", "Alpha");
        Map<UUID, RecipePersonalizationSignals> personalization = Map.of(
                beta.getId(), signals(beta, RecipeRecency.MID_WINDOW, "0.5", null, "0", 0, 0),
                alpha.getId(), signals(alpha, RecipeRecency.MID_WINDOW, "0.5", null, "0", 0, 0));
        RecommendationContext context = context(List.of(alpha, beta), personalization);

        RecommendationOutcome first = scorer.recommend(List.of(beta, alpha), context);
        RecommendationOutcome second = scorer.recommend(List.of(alpha, beta), context);

        assertEquals(first.recommendations(), second.recommendations());
        assertEquals(List.of(alpha, beta),
                first.recommendations().stream().map(RecipeRecommendation::recipe).toList());
    }

    @Test
    void explicitDislikeSuppressesImplicitSelectedExplanation() {
        Recipe candidate = candidate("explicit-over-implicit", "Explicit over implicit");
        RecipePersonalizationSignals personalization = signals(
                candidate, RecipeRecency.NEVER_COOKED, "1", "-0.75", "0.4", 12, 0);

        RecipeRecommendation result = scorer.recommend(List.of(candidate),
                        context(List.of(candidate), Map.of(candidate.getId(), personalization)))
                .recommendations().getFirst();

        assertTrue(result.reasonCodes().contains(
                RecommendationReasonCode.RECIPE_EXPLICITLY_DISLIKED));
        assertFalse(result.reasonCodes().contains(
                RecommendationReasonCode.RECIPE_REPEATEDLY_SELECTED));
    }

    @Test
    void householdStrongRejectionCapStillAppliesAfterPersonalization() {
        Recipe candidate = candidate("household-cap", "Household cap");
        Taste taste = candidate.getTastes().getFirst();
        HouseholdMemberPreference rejectingMember = new HouseholdMemberPreference(
                "member", new TastePreferenceProfile(Map.of(
                        taste.getId(), BigDecimal.ONE.negate())),
                RecommendationConstraints.none());
        RecommendationContext base = new RecommendationContext(
                RecommendationRequest.forServings(2),
                List.of(stock("household-cap", ingredientOf(candidate), "100", Unit.GRAM)),
                TastePreferenceProfile.empty(), List.of(rejectingMember),
                RecommendationConstraints.none());
        RecommendationContext context = base.withPersonalization(Map.of(
                candidate.getId(), signals(candidate, RecipeRecency.NEVER_COOKED,
                        "1", "1", "0", 0, 0)));

        RecipeRecommendation result = scorer.recommend(List.of(candidate), context)
                .recommendations().getFirst();

        assertTrue(result.score().compareTo(new BigDecimal("0.39")) <= 0);
        assertTrue(result.reasonCodes().contains(RecommendationReasonCode.HOUSEHOLD_CONFLICT));
        assertTrue(result.reasonCodes().contains(
                RecommendationReasonCode.HOUSEHOLD_MEMBER_DISLIKES));
    }

    private static RecommendationContext context(
            List<Recipe> recipes, Map<UUID, RecipePersonalizationSignals> signals) {
        List<InventoryItem> inventory = recipes.stream()
                .map(recipe -> stock("stock:" + recipe.getId(),
                        ingredientOf(recipe), "100", Unit.GRAM))
                .toList();
        return RecommendationContext.pantryOnly(2, inventory).withPersonalization(signals);
    }

    private static RecipePersonalizationSignals signals(
            Recipe recipe, RecipeRecency recency, String freshness,
            String explicit, String implicit, int selected, int dismissed) {
        BigDecimal implicitValue = new BigDecimal(implicit);
        Optional<BigDecimal> explicitValue = explicit == null
                ? Optional.empty() : Optional.of(new BigDecimal(explicit));
        Optional<Instant> lastCooked = recency == RecipeRecency.NEVER_COOKED
                ? Optional.empty() : Optional.of(NOW.minusSeconds(86_400));
        return new RecipePersonalizationSignals(
                recipe.getId(), lastCooked, recency, new BigDecimal(freshness),
                explicitValue, implicitValue, explicitValue.orElse(implicitValue),
                selected, dismissed);
    }

    private static Ingredient ingredientOf(Recipe recipe) {
        return recipe.getIngredientGroups().getFirst().getStandardOption().getIngredient();
    }

    private static Recipe candidate(String key, String name) {
        Ingredient ingredient = ingredient("R3 ingredient " + key);
        Taste taste = taste("R3 taste");
        return recipe(key, name, List.of(group(key, List.of(
                option(key, ingredient, "100", Unit.GRAM, 0)), 0)), taste);
    }
}
