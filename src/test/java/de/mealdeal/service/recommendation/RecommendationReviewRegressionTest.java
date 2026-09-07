package de.mealdeal.service.recommendation;

import de.mealdeal.domain.DishType;
import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import de.mealdeal.service.RecipeScaler;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static de.mealdeal.service.recommendation.RecommendationTestFixtures.group;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.ingredient;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.option;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.recipe;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.stock;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.taste;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationReviewRegressionTest {

    private static final Taste SAVORY = taste("Review-Herzhaft");
    private final RecipeRecommendationService service = new RecipeRecommendationService();

    @Test
    void fullyPositiveHouseholdGetsOnlyPositiveExplanation() {
        Ingredient rice = ingredient("Positiver Haushaltsreis");
        Recipe candidate = oneIngredientRecipe("positive-household", rice, SAVORY);
        RecommendationContext context = context(candidate, List.of(
                member("a", SAVORY, "1"), member("b", SAVORY, "0.8")));

        RecipeRecommendation result = service.recommend(List.of(candidate), context)
                .recommendations().getFirst();

        assertTrue(result.reasonCodes().contains(
                RecommendationReasonCode.HOUSEHOLD_STRONG_MATCH));
        assertFalse(result.reasonCodes().contains(RecommendationReasonCode.HOUSEHOLD_CONFLICT));
        assertFalse(result.reasonCodes().contains(
                RecommendationReasonCode.HOUSEHOLD_MEMBER_DISLIKES));
    }

    @Test
    void normallyMixedHouseholdUsesHybridWithoutConflictReason() {
        Ingredient rice = ingredient("Gemischter Haushaltsreis");
        Recipe candidate = oneIngredientRecipe("mixed-household", rice, SAVORY);
        RecommendationContext context = context(candidate, List.of(
                member("cautious", SAVORY, "-0.5"), member("likes", SAVORY, "1")));

        RecipeRecommendation result = service.recommend(List.of(candidate), context)
                .recommendations().getFirst();

        assertEquals(0, new BigDecimal("0.3625").compareTo(result.signals()
                .valueOf(RecommendationSignal.HOUSEHOLD_PREFERENCE).orElseThrow()));
        assertFalse(result.reasonCodes().contains(RecommendationReasonCode.HOUSEHOLD_CONFLICT));
        assertFalse(result.reasonCodes().contains(
                RecommendationReasonCode.HOUSEHOLD_MEMBER_DISLIKES));
    }

    @Test
    void strongDislikeBoundaryIsAppliedOnAndBelowPointTwoOnly() {
        Ingredient rice = ingredient("Grenzwertreis");
        Recipe candidate = oneIngredientRecipe("household-boundary", rice, SAVORY);
        RecipeRecommendation below = service.recommend(List.of(candidate),
                        context(candidate, List.of(member("below", SAVORY, "-0.6002"))))
                .recommendations().getFirst();
        RecipeRecommendation above = service.recommend(List.of(candidate),
                        context(candidate, List.of(member("above", SAVORY, "-0.5998"))))
                .recommendations().getFirst();

        assertTrue(below.reasonCodes().contains(RecommendationReasonCode.HOUSEHOLD_CONFLICT));
        assertTrue(below.score().compareTo(new BigDecimal("0.39")) <= 0);
        assertFalse(above.reasonCodes().contains(RecommendationReasonCode.HOUSEHOLD_CONFLICT));
    }

    @Test
    void manyPositiveMembersCannotCreateStrongMatchBesideStrongDislike() {
        Ingredient rice = ingredient("Konfliktreis");
        Recipe candidate = oneIngredientRecipe("household-many", rice, SAVORY);
        RecommendationContext context = context(candidate, List.of(
                member("reject", SAVORY, "-1"),
                member("like-1", SAVORY, "1"),
                member("like-2", SAVORY, "1"),
                member("like-3", SAVORY, "1"),
                member("like-4", SAVORY, "1")));

        RecipeRecommendation result = service.recommend(List.of(candidate), context)
                .recommendations().getFirst();

        assertTrue(result.reasonCodes().contains(RecommendationReasonCode.HOUSEHOLD_CONFLICT));
        assertTrue(result.reasonCodes().contains(
                RecommendationReasonCode.HOUSEHOLD_MEMBER_DISLIKES));
        assertFalse(result.reasonCodes().contains(
                RecommendationReasonCode.HOUSEHOLD_STRONG_MATCH));
    }

    @Test
    void oneMemberWithStrongRejectionAndThreeLikesKeepsConflictCap() {
        Ingredient rice = ingredient("I2-Konfliktreis");
        Taste rejected = taste("I2-Abgelehnt");
        Taste likedA = taste("I2-Gemocht-A");
        Taste likedB = taste("I2-Gemocht-B");
        Taste likedC = taste("I2-Gemocht-C");
        Recipe candidate = recipe("i2-original", "I2 Original", List.of(
                        group("i2-original", List.of(
                                option("i2-original", rice, "100", Unit.GRAM, 0)), 0)),
                List.of(rejected, likedA, likedB, likedC),
                null, null, null, null, DishType.MAIN);
        HouseholdMemberPreference member = new HouseholdMemberPreference(
                "i2-member", new TastePreferenceProfile(Map.of(
                        rejected.getId(), new BigDecimal("-0.60"),
                        likedA.getId(), BigDecimal.ONE,
                        likedB.getId(), BigDecimal.ONE,
                        likedC.getId(), BigDecimal.ONE)),
                RecommendationConstraints.none());

        RecipeRecommendation result = service.recommend(
                        List.of(candidate), context(candidate, List.of(member)))
                .recommendations().getFirst();

        assertEquals(0, new BigDecimal("0.80").compareTo(result.signals()
                .valueOf(RecommendationSignal.HOUSEHOLD_PREFERENCE).orElseThrow()));
        assertTrue(result.score().compareTo(new BigDecimal("0.39")) <= 0);
        assertTrue(result.reasonCodes().contains(RecommendationReasonCode.HOUSEHOLD_CONFLICT));
        assertTrue(result.reasonCodes().contains(
                RecommendationReasonCode.HOUSEHOLD_MEMBER_DISLIKES));
        assertFalse(result.reasonCodes().contains(
                RecommendationReasonCode.HOUSEHOLD_STRONG_MATCH));
    }

    @Test
    void combinedEligibilityExclusionsAreAllReported() {
        Ingredient rice = ingredient("Ausgeschlossener Reis");
        Recipe candidate = oneIngredientRecipe("combined-exclusion", rice, SAVORY);
        RecommendationContext context = new RecommendationContext(
                new RecommendationRequest(2, Optional.empty(), Optional.of(DishType.SIDE)),
                List.of(), TastePreferenceProfile.empty(), List.of(),
                new RecommendationConstraints(
                        Set.of(candidate.getId()), Set.of(rice.getId())));

        CandidateEligibility eligibility = service.evaluateEligibility(candidate, context);

        assertFalse(eligibility.eligible());
        assertEquals(Set.of(
                        RecommendationReasonCode.DISH_TYPE_MISMATCH,
                        RecommendationReasonCode.RECIPE_HARD_EXCLUDED,
                        RecommendationReasonCode.INGREDIENT_GROUP_HARD_EXCLUDED),
                Set.copyOf(eligibility.reasonCodes()));
    }

    @Test
    void fullyExcludedCandidateSetReturnsOnlyStableExclusions() {
        Ingredient firstIngredient = ingredient("Voll ausgeschlossen A");
        Ingredient secondIngredient = ingredient("Voll ausgeschlossen B");
        Recipe first = oneIngredientRecipe("excluded-a", firstIngredient, SAVORY);
        Recipe second = oneIngredientRecipe("excluded-b", secondIngredient, SAVORY);
        RecommendationContext context = new RecommendationContext(
                RecommendationRequest.forServings(1), List.of(), TastePreferenceProfile.empty(),
                List.of(), new RecommendationConstraints(
                        Set.of(first.getId(), second.getId()), Set.of()));

        RecommendationOutcome outcome = service.recommend(List.of(second, first), context);

        assertTrue(outcome.recommendations().isEmpty());
        assertEquals(2, outcome.exclusions().size());
        assertTrue(outcome.exclusions().stream().allMatch(exclusion -> exclusion.reasonCodes()
                .contains(RecommendationReasonCode.RECIPE_HARD_EXCLUDED)));
    }

    @Test
    void minimalAndNullPublicInputsFollowTheContract() {
        RecommendationContext minimum = RecommendationContext.pantryOnly(1, List.of());

        RecommendationOutcome empty = service.recommend(List.of(), minimum);

        assertTrue(empty.recommendations().isEmpty());
        assertTrue(empty.exclusions().isEmpty());
        assertThrows(NullPointerException.class, () -> service.recommend(null, minimum));
        assertThrows(NullPointerException.class, () -> service.recommend(List.of(), null));
    }

    @Test
    void zeroWeightTasteProducesNoScoringReason() {
        Ingredient rice = ingredient("Gewichtsloser Geschmacksreis");
        Taste spicy = taste("Gewichtslos scharf");
        Recipe candidate = oneIngredientRecipe("zero-taste", rice, spicy);
        RecommendationScoringProfile profile = profileWith(
                RecommendationSignal.PANTRY_COVERAGE, BigDecimal.ONE);
        RecipeRecommendationService customService =
                new RecipeRecommendationService(profile, new RecipeScaler());
        RecommendationContext context = new RecommendationContext(
                RecommendationRequest.forServings(2),
                List.of(stock("zero-taste", rice, "100", Unit.GRAM)),
                new TastePreferenceProfile(Map.of(spicy.getId(), BigDecimal.ONE.negate())),
                List.of(), RecommendationConstraints.none());

        RecipeRecommendation result = customService.recommend(List.of(candidate), context)
                .recommendations().getFirst();

        assertTrue(result.signals().valueOf(RecommendationSignal.TASTE_AFFINITY).isPresent());
        assertFalse(result.reasonCodes().contains(RecommendationReasonCode.TASTE_MISMATCH));
        assertFalse(result.reasonCodes().contains(RecommendationReasonCode.TASTE_MATCH));
        assertFalse(result.reasonCodes().contains(RecommendationReasonCode.TASTE_STRONG_MATCH));
    }

    @Test
    void zeroWeightPantrySignalsProduceNoScoringReasons() {
        Ingredient rice = ingredient("Gewichtsloser Pantryreis");
        Recipe candidate = oneIngredientRecipe("zero-pantry", rice, SAVORY);
        RecommendationScoringProfile profile = profileWith(
                RecommendationSignal.TASTE_AFFINITY, BigDecimal.ONE);
        RecipeRecommendationService customService =
                new RecipeRecommendationService(profile, new RecipeScaler());
        RecommendationContext context = new RecommendationContext(
                RecommendationRequest.forServings(2), List.of(),
                new TastePreferenceProfile(Map.of(SAVORY.getId(), BigDecimal.ONE)),
                List.of(), RecommendationConstraints.none());

        RecipeRecommendation result = customService.recommend(List.of(candidate), context)
                .recommendations().getFirst();

        assertFalse(result.reasonCodes().contains(
                RecommendationReasonCode.PANTRY_LOW_COVERAGE));
        assertFalse(result.reasonCodes().contains(
                RecommendationReasonCode.MISSING_ONE_INGREDIENT_GROUP));
        assertTrue(result.reasonCodes().contains(
                RecommendationReasonCode.TASTE_STRONG_MATCH));
    }

    @Test
    void timeUnknownRemainsAnAvailabilityReasonWithZeroTimeWeight() {
        Ingredient rice = ingredient("Unbekannte Zeit Reis");
        Recipe candidate = oneIngredientRecipe("unknown-zero-time", rice, SAVORY);
        RecommendationScoringProfile profile = profileWith(
                RecommendationSignal.PANTRY_COVERAGE, BigDecimal.ONE);
        RecipeRecommendationService customService =
                new RecipeRecommendationService(profile, new RecipeScaler());
        RecommendationContext context = new RecommendationContext(
                new RecommendationRequest(
                        2, Optional.of(Duration.ofSeconds(1)), Optional.empty()),
                List.of(), TastePreferenceProfile.empty(), List.of(),
                RecommendationConstraints.none());

        RecipeRecommendation result = customService.recommend(List.of(candidate), context)
                .recommendations().getFirst();

        assertTrue(result.reasonCodes().contains(RecommendationReasonCode.TIME_UNKNOWN));
        assertFalse(result.reasonCodes().contains(RecommendationReasonCode.TIME_WITHIN_LIMIT));
        assertFalse(result.reasonCodes().contains(RecommendationReasonCode.TIME_OVER_LIMIT));
    }

    private static Recipe oneIngredientRecipe(String key, Ingredient ingredient, Taste taste) {
        return recipe(key, key, List.of(group(key, List.of(
                option(key, ingredient, "100", Unit.GRAM, 0)), 0)), taste);
    }

    private static HouseholdMemberPreference member(
            String id, Taste taste, String affinity) {
        return new HouseholdMemberPreference(id,
                new TastePreferenceProfile(Map.of(
                        taste.getId(), new BigDecimal(affinity))),
                RecommendationConstraints.none());
    }

    private static RecommendationContext context(
            Recipe candidate, List<HouseholdMemberPreference> household) {
        Ingredient ingredient = candidate.getIngredientGroups().getFirst()
                .getStandardOption().getIngredient();
        return new RecommendationContext(
                RecommendationRequest.forServings(2),
                List.of(stock("household:" + candidate.getId(),
                        ingredient, "100", Unit.GRAM)),
                TastePreferenceProfile.empty(), household,
                RecommendationConstraints.none());
    }

    private static RecommendationScoringProfile profileWith(
            RecommendationSignal activeSignal, BigDecimal weight) {
        EnumMap<RecommendationSignal, BigDecimal> weights =
                new EnumMap<>(RecommendationSignal.class);
        for (RecommendationSignal signal : RecommendationSignal.values()) {
            weights.put(signal, BigDecimal.ZERO);
        }
        weights.put(activeSignal, weight);
        return new RecommendationScoringProfile(
                "test", weights,
                new BigDecimal("0.80"), new BigDecimal("0.65"),
                new BigDecimal("0.50"), new BigDecimal("0.70"),
                new BigDecimal("0.20"), new BigDecimal("0.39"));
    }
}
