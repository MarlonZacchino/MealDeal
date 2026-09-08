package de.mealdeal.service.recommendation;

import de.mealdeal.domain.InventoryItem;
import de.mealdeal.domain.Recipe;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static de.mealdeal.service.recommendation.R4EvaluationFixtures.candidate;
import static de.mealdeal.service.recommendation.R4EvaluationFixtures.personalizedContext;
import static de.mealdeal.service.recommendation.R4EvaluationFixtures.signals;
import static de.mealdeal.service.recommendation.R4EvaluationFixtures.stockForAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** R4 invariants for semantics, explainability, ranking reversals, and determinism. */
@Tag("r4-invariants")
@Tag("r4-explainability")
@Tag("r4-determinism")
class RecommendationR4InvariantTest {

    private final RecipeRecommendationService scorer = new RecipeRecommendationService();

    @Test
    void explicitDislikeOverridesOneHundredSelections() {
        Recipe candidate = candidate("r4-explicit-dislike", "Explicit Dislike");
        RecipePersonalizationSignals snapshot = signals(
                candidate, "1", "-0.75", "0.49", 100, 0);

        RecipeRecommendation result = recommend(candidate, snapshot);

        assertEquals(0, new BigDecimal("0.125").compareTo(result.signals()
                .valueOf(RecommendationSignal.RECIPE_PREFERENCE).orElseThrow()));
        assertTrue(result.reasonCodes().contains(
                RecommendationReasonCode.RECIPE_EXPLICITLY_DISLIKED));
        assertFalse(result.reasonCodes().contains(
                RecommendationReasonCode.RECIPE_REPEATEDLY_SELECTED));
    }

    @Test
    void explicitLikeOverridesOneHundredDismissals() {
        Recipe candidate = candidate("r4-explicit-like", "Explicit Like");
        RecipePersonalizationSignals snapshot = signals(
                candidate, "1", "0.75", "-0.49", 0, 100);

        RecipeRecommendation result = recommend(candidate, snapshot);

        assertEquals(0, new BigDecimal("0.875").compareTo(result.signals()
                .valueOf(RecommendationSignal.RECIPE_PREFERENCE).orElseThrow()));
        assertTrue(result.reasonCodes().contains(
                RecommendationReasonCode.RECIPE_EXPLICITLY_LIKED));
        assertFalse(result.reasonCodes().contains(
                RecommendationReasonCode.RECIPE_REPEATEDLY_DISMISSED));
    }

    @Test
    void explicitRatingThreeRemainsNeutralDespiteSelections() {
        Recipe candidate = candidate("r4-rating-three", "Rating Three");
        RecipePersonalizationSignals snapshot = signals(
                candidate, "1", "0", "0.49", 100, 0);

        RecipeRecommendation result = recommend(candidate, snapshot);

        assertEquals(0, new BigDecimal("0.5").compareTo(result.signals()
                .valueOf(RecommendationSignal.RECIPE_PREFERENCE).orElseThrow()));
        assertFalse(result.reasonCodes().stream().anyMatch(RecommendationR4InvariantTest::isPreferenceReason));
    }

    @Test
    void cookedFrequencyCannotBecomePositivePreference() {
        Recipe cookedManyTimes = candidate("r4-cooked-many", "Cooked Many Times");
        RecipePersonalizationSignals latestHistoryOnly = signals(
                cookedManyTimes, "0.25", null, "0", 0, 0);

        RecipeRecommendation result = recommend(cookedManyTimes, latestHistoryOnly);

        assertEquals(0, new BigDecimal("0.5").compareTo(result.signals()
                .valueOf(RecommendationSignal.RECIPE_PREFERENCE).orElseThrow()));
        assertTrue(result.reasonCodes().contains(RecommendationReasonCode.RECENTLY_COOKED));
        assertFalse(result.reasonCodes().stream().anyMatch(RecommendationR4InvariantTest::isPreferenceReason));
    }

    @Test
    void selectionsCannotChangeNeverCookedRecency() {
        Recipe selected = candidate("r4-selected-never", "Selected Never Cooked");
        RecipePersonalizationSignals selectedOnly = signals(
                selected, "1", null, "0.49", 100, 0);

        RecipeRecommendation result = recommend(selected, selectedOnly);

        assertTrue(result.reasonCodes().contains(RecommendationReasonCode.RECIPE_NEVER_COOKED));
        assertTrue(result.reasonCodes().contains(
                RecommendationReasonCode.RECIPE_REPEATEDLY_SELECTED));
        assertFalse(result.reasonCodes().contains(RecommendationReasonCode.RECENTLY_COOKED));
    }

    @Test
    void allNeverCookedCandidatesReceiveEqualVarietyAndPreserveBasisRanking() {
        Recipe FullPantry = candidate("r4-never-full", "Never Full Pantry");
        Recipe EmptyPantry = candidate("r4-never-empty", "Never Empty Pantry");
        Map<UUID, RecipePersonalizationSignals> snapshots = Map.of(
                FullPantry.getId(), signals(FullPantry, "1", null, "0", 0, 0),
                EmptyPantry.getId(), signals(EmptyPantry, "1", null, "0", 0, 0));

        List<RecipeRecommendation> ranking = recommend(
                List.of(EmptyPantry, FullPantry), stockForAll("100", FullPantry), snapshots);

        assertEquals(FullPantry, ranking.getFirst().recipe());
        assertTrue(ranking.stream().allMatch(result -> result.signals()
                .valueOf(RecommendationSignal.VARIETY_SCORE).orElseThrow()
                .compareTo(BigDecimal.ONE) == 0));
    }

    @Test
    void identicalFeedbackDoesNotChangeRelativeRanking() {
        Recipe Alpha = candidate("r4-same-alpha", "Alpha Same Preference");
        Recipe Beta = candidate("r4-same-beta", "Beta Same Preference");
        Map<UUID, RecipePersonalizationSignals> snapshots = Map.of(
                Alpha.getId(), signals(Alpha, "0.5", "0.75", "0", 0, 0),
                Beta.getId(), signals(Beta, "0.5", "0.75", "0", 0, 0));

        List<RecipeRecommendation> ranking = recommend(
                List.of(Beta, Alpha), stockForAll("100", Alpha, Beta), snapshots);

        assertEquals(List.of(Alpha, Beta), ranking.stream()
                .map(RecipeRecommendation::recipe).toList());
        assertEquals(0, ranking.getFirst().score().compareTo(ranking.getLast().score()));
    }

    @Test
    void controlledLikeCausesExpectedRankReversalButPantryGapReversesItBack() {
        Recipe Alpha = candidate("r4-reversal-alpha", "Alpha Baseline");
        Recipe Beta = candidate("r4-reversal-beta", "Beta Challenger");
        List<InventoryItem> fullInventory = stockForAll("100", Alpha, Beta);
        Map<UUID, RecipePersonalizationSignals> neutral = Map.of(
                Alpha.getId(), signals(Alpha, "1", null, "0", 0, 0),
                Beta.getId(), signals(Beta, "1", null, "0", 0, 0));
        Map<UUID, RecipePersonalizationSignals> betaLiked = Map.of(
                Alpha.getId(), signals(Alpha, "1", null, "0", 0, 0),
                Beta.getId(), signals(Beta, "1", "0.75", "0", 0, 0));

        assertEquals(Alpha, recommend(List.of(Beta, Alpha), fullInventory, neutral)
                .getFirst().recipe());
        assertEquals(Beta, recommend(List.of(Alpha, Beta), fullInventory, betaLiked)
                .getFirst().recipe());
        assertEquals(Alpha, recommend(List.of(Beta, Alpha),
                stockForAll("100", Alpha), betaLiked).getFirst().recipe());
    }

    @Test
    void singleSelectionAffectsScoreButDoesNotClaimRepeatedBehavior() {
        Recipe candidate = candidate("r4-single-selection", "Single Selection");
        RecipePersonalizationSignals snapshot = signals(
                candidate, "1", null, "0.1666666666666666666666666666666667", 1, 0);

        RecipeRecommendation result = recommend(candidate, snapshot);

        assertTrue(result.signals().valueOf(RecommendationSignal.RECIPE_PREFERENCE)
                .orElseThrow().compareTo(new BigDecimal("0.5")) > 0);
        assertFalse(result.reasonCodes().contains(
                RecommendationReasonCode.RECIPE_REPEATEDLY_SELECTED));
    }

    @Test
    void reasonsNeverContainContradictoryPreferenceOrHouseholdClaims() {
        Recipe candidate = candidate("r4-reason-consistency", "Reason Consistency");
        RecipePersonalizationSignals snapshot = signals(
                candidate, "1", "1", "-0.49", 0, 100);

        RecipeRecommendation result = recommend(candidate, snapshot);

        assertTrue(result.reasonCodes().contains(RecommendationReasonCode.RECIPE_STRONGLY_LIKED));
        assertFalse(result.reasonCodes().contains(RecommendationReasonCode.RECIPE_STRONGLY_DISLIKED));
        assertFalse(result.reasonCodes().contains(
                RecommendationReasonCode.RECIPE_REPEATEDLY_DISMISSED));
    }

    @Test
    void repeatedRunsAndInputPermutationsAreExactlyDeterministic() {
        Recipe Alpha = candidate("r4-det-alpha", "Alpha Deterministic");
        Recipe Beta = candidate("r4-det-beta", "Beta Deterministic");
        Map<UUID, RecipePersonalizationSignals> snapshots = Map.of(
                Alpha.getId(), signals(Alpha, "0.5", null, "0.25", 2, 0),
                Beta.getId(), signals(Beta, "0.5", null, "0.25", 2, 0));
        List<InventoryItem> inventory = stockForAll("100", Alpha, Beta);
        List<RecipeRecommendation> expected = recommend(
                List.of(Beta, Alpha), inventory, snapshots);

        for (int repetition = 0; repetition < 25; repetition++) {
            List<Recipe> input = repetition % 2 == 0
                    ? List.of(Alpha, Beta) : List.of(Beta, Alpha);
            assertEquals(expected, recommend(input, inventory.reversed(), snapshots));
        }
    }

    private RecipeRecommendation recommend(
            Recipe recipe, RecipePersonalizationSignals snapshot) {
        return recommend(List.of(recipe), stockForAll("100", recipe),
                Map.of(recipe.getId(), snapshot)).getFirst();
    }

    private List<RecipeRecommendation> recommend(
            List<Recipe> recipes, List<InventoryItem> inventory,
            Map<UUID, RecipePersonalizationSignals> snapshots) {
        RecommendationContext context = personalizedContext(
                inventory, TastePreferenceProfile.empty(), List.of(),
                RecommendationRequest.forServings(2), snapshots);
        return scorer.recommend(recipes, context).recommendations();
    }

    private static boolean isPreferenceReason(RecommendationReasonCode code) {
        return switch (code) {
            case RECIPE_EXPLICITLY_LIKED, RECIPE_EXPLICITLY_DISLIKED,
                    RECIPE_STRONGLY_LIKED, RECIPE_STRONGLY_DISLIKED,
                    RECIPE_REPEATEDLY_SELECTED, RECIPE_REPEATEDLY_DISMISSED -> true;
            default -> false;
        };
    }
}
