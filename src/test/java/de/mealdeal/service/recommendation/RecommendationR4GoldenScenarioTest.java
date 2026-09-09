package de.mealdeal.service.recommendation;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.InventoryItem;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static de.mealdeal.service.recommendation.R4EvaluationFixtures.SAVORY;
import static de.mealdeal.service.recommendation.R4EvaluationFixtures.SWEET;
import static de.mealdeal.service.recommendation.R4EvaluationFixtures.alternativeCandidate;
import static de.mealdeal.service.recommendation.R4EvaluationFixtures.candidate;
import static de.mealdeal.service.recommendation.R4EvaluationFixtures.pantryContext;
import static de.mealdeal.service.recommendation.R4EvaluationFixtures.personalizedContext;
import static de.mealdeal.service.recommendation.R4EvaluationFixtures.signals;
import static de.mealdeal.service.recommendation.R4EvaluationFixtures.stockForAll;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.ingredient;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.stock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Central, product-readable R4 Golden Suite covering categories A through N. */
@Tag("r4-golden")
class RecommendationR4GoldenScenarioTest {

    private final RecipeRecommendationService scorer = new RecipeRecommendationService();

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenScenarios")
    void goldenRankingAndExplanationsMatchProductExpectation(GoldenScenario scenario) {
        RecommendationOutcome outcome = scorer.recommend(scenario.candidates(), scenario.context());
        List<Recipe> actualOrder = outcome.recommendations().stream()
                .map(RecipeRecommendation::recipe)
                .toList();

        assertEquals(scenario.expectedOrder(), actualOrder,
                scenario.id() + ": " + scenario.rationale());
        Map<UUID, RecipeRecommendation> byRecipe = outcome.recommendations().stream()
                .collect(java.util.stream.Collectors.toMap(
                        recommendation -> recommendation.recipe().getId(),
                        recommendation -> recommendation));
        scenario.requiredReasons().forEach((recipeId, reasons) ->
                assertTrue(byRecipe.get(recipeId).reasonCodes().containsAll(reasons),
                        scenario.id() + " must explain " + reasons));
        scenario.forbiddenReasons().forEach((recipeId, reasons) ->
                assertTrue(java.util.Collections.disjoint(
                                byRecipe.get(recipeId).reasonCodes(), reasons),
                        scenario.id() + " must not explain " + reasons));
        if (scenario.cappedRecipeId().isPresent()) {
            assertTrue(byRecipe.get(scenario.cappedRecipeId().orElseThrow()).score()
                            .compareTo(new java.math.BigDecimal("0.39")) <= 0,
                    scenario.id() + " must preserve the household cap");
        }
    }

    static Stream<GoldenScenario> goldenScenarios() {
        return Stream.of(
                pantryDominance(), missingIngredients(), tastePreference(), timeFit(),
                recency(), explicitFeedback(), implicitInteractions(), householdConflict(),
                strongMatchGuardrail(), ingredientAlternative(), compoundTradeOff(),
                neutralColdStart(), deterministicTie(), edgeSamePersonalization());
    }

    private static GoldenScenario pantryDominance() {
        Recipe PantryPerfectNeutral = candidate("r4-a-perfect", "Pantry Perfect Neutral");
        Recipe PantryWeakLiked = candidate("r4-a-weak", "Pantry Weak Liked");
        List<InventoryItem> inventory = stockForAll("100", PantryPerfectNeutral);
        Map<UUID, RecipePersonalizationSignals> personalization = Map.of(
                PantryPerfectNeutral.getId(), signals(
                        PantryPerfectNeutral, "1", null, "0", 0, 0),
                PantryWeakLiked.getId(), signals(
                        PantryWeakLiked, "1", "1", "0", 0, 0));
        return scenario("A-PANTRY", "Pantry dominance",
                List.of(PantryWeakLiked, PantryPerfectNeutral),
                personalizedContext(inventory, TastePreferenceProfile.empty(), List.of(),
                        RecommendationRequest.forServings(2), personalization),
                List.of(PantryPerfectNeutral, PantryWeakLiked),
                reasons(PantryPerfectNeutral, RecommendationReasonCode.PANTRY_FULL_COVERAGE),
                Map.of(), Optional.empty(),
                "A perfect pantry fit must beat a liked candidate with no stock.");
    }

    private static GoldenScenario missingIngredients() {
        Recipe OneMissing = candidate("r4-b-one", "One Missing", SAVORY, null, 2);
        Recipe TwoMissing = candidate("r4-b-two", "Two Missing", SAVORY, null, 3);
        List<InventoryItem> inventory = List.of(
                stock("r4-b-one-stock", ingredientAt(OneMissing, 0), "100", Unit.GRAM),
                stock("r4-b-two-stock", ingredientAt(TwoMissing, 0), "100", Unit.GRAM));
        return scenario("B-MISSING", "Missing ingredient groups",
                List.of(TwoMissing, OneMissing), pantryContext(inventory),
                List.of(OneMissing, TwoMissing),
                mergeReasons(
                        reasons(OneMissing, RecommendationReasonCode.MISSING_ONE_INGREDIENT_GROUP),
                        reasons(TwoMissing,
                                RecommendationReasonCode.MISSING_MULTIPLE_INGREDIENT_GROUPS)),
                Map.of(), Optional.empty(),
                "Fewer missing groups and higher coverage must rank first.");
    }

    private static GoldenScenario tastePreference() {
        Recipe TastePerfect = candidate("r4-c-liked", "Taste Perfect", SAVORY, null);
        Recipe TasteConflict = candidate("r4-c-conflict", "Taste Conflict", SWEET, null);
        TastePreferenceProfile preferences = new TastePreferenceProfile(Map.of(
                SAVORY.getId(), java.math.BigDecimal.ONE,
                SWEET.getId(), java.math.BigDecimal.ONE.negate()));
        return scenario("C-TASTE", "Taste preference",
                List.of(TasteConflict, TastePerfect),
                personalizedContext(stockForAll("100", TastePerfect, TasteConflict),
                        preferences, List.of(), RecommendationRequest.forServings(2), Map.of()),
                List.of(TastePerfect, TasteConflict),
                mergeReasons(
                        reasons(TastePerfect, RecommendationReasonCode.TASTE_STRONG_MATCH),
                        reasons(TasteConflict, RecommendationReasonCode.TASTE_MISMATCH)),
                Map.of(), Optional.empty(),
                "An explicit perfect taste fit must beat an explicit mismatch.");
    }

    private static GoldenScenario timeFit() {
        Recipe WithinBudget = candidate("r4-d-within", "Within Budget", SAVORY, 30);
        Recipe OverBudget = candidate("r4-d-over", "Over Budget", SAVORY, 90);
        RecommendationRequest request = new RecommendationRequest(
                2, Optional.of(Duration.ofMinutes(30)), Optional.empty());
        return scenario("D-TIME", "Time fit",
                List.of(OverBudget, WithinBudget),
                personalizedContext(stockForAll("100", WithinBudget, OverBudget),
                        TastePreferenceProfile.empty(), List.of(), request, Map.of()),
                List.of(WithinBudget, OverBudget),
                mergeReasons(
                        reasons(WithinBudget, RecommendationReasonCode.TIME_WITHIN_LIMIT),
                        reasons(OverBudget, RecommendationReasonCode.TIME_OVER_LIMIT)),
                Map.of(), Optional.empty(),
                "A recipe exactly on the time limit must beat one far over it.");
    }

    private static GoldenScenario recency() {
        Recipe NeverCooked = candidate("r4-e-never", "Never Cooked");
        Recipe CookedYesterday = candidate("r4-e-yesterday", "Cooked Yesterday");
        Map<UUID, RecipePersonalizationSignals> personalization = Map.of(
                NeverCooked.getId(), signals(NeverCooked, "1", null, "0", 0, 0),
                CookedYesterday.getId(), signals(
                        CookedYesterday, "0.0714285714285714", null, "0", 0, 0));
        return scenario("E-RECENCY", "Recipe recency",
                List.of(CookedYesterday, NeverCooked),
                personalizedContext(stockForAll("100", NeverCooked, CookedYesterday),
                        TastePreferenceProfile.empty(), List.of(),
                        RecommendationRequest.forServings(2), personalization),
                List.of(NeverCooked, CookedYesterday),
                mergeReasons(
                        reasons(NeverCooked, RecommendationReasonCode.RECIPE_NEVER_COOKED),
                        reasons(CookedYesterday, RecommendationReasonCode.RECENTLY_COOKED)),
                Map.of(), Optional.empty(),
                "Yesterday remains eligible while the never-cooked recipe provides more variety.");
    }

    private static GoldenScenario explicitFeedback() {
        Recipe ExplicitlyLiked = candidate("r4-f-liked", "Explicitly Liked");
        Recipe Neutral = candidate("r4-f-neutral", "Neutral");
        Recipe ExplicitlyDisliked = candidate("r4-f-disliked", "Explicitly Disliked");
        Map<UUID, RecipePersonalizationSignals> personalization = Map.of(
                ExplicitlyLiked.getId(), signals(ExplicitlyLiked, "1", "1", "0", 0, 0),
                Neutral.getId(), signals(Neutral, "1", null, "0", 0, 0),
                ExplicitlyDisliked.getId(), signals(
                        ExplicitlyDisliked, "1", "-1", "0", 0, 0));
        return scenario("F-FEEDBACK", "Explicit feedback",
                List.of(ExplicitlyDisliked, Neutral, ExplicitlyLiked),
                personalizedContext(stockForAll(
                                "100", ExplicitlyLiked, Neutral, ExplicitlyDisliked),
                        TastePreferenceProfile.empty(), List.of(),
                        RecommendationRequest.forServings(2), personalization),
                List.of(ExplicitlyLiked, Neutral, ExplicitlyDisliked),
                mergeReasons(
                        reasons(ExplicitlyLiked, RecommendationReasonCode.RECIPE_STRONGLY_LIKED),
                        reasons(ExplicitlyDisliked,
                                RecommendationReasonCode.RECIPE_STRONGLY_DISLIKED)),
                Map.of(), Optional.empty(),
                "Explicit positive, neutral and negative preferences must order monotonically.");
    }

    private static GoldenScenario implicitInteractions() {
        Recipe InteractionSelected = candidate("r4-g-selected", "Interaction Selected");
        Recipe Neutral = candidate("r4-g-neutral", "Neutral");
        Recipe InteractionDismissed = candidate("r4-g-dismissed", "Interaction Dismissed");
        Map<UUID, RecipePersonalizationSignals> personalization = Map.of(
                InteractionSelected.getId(), signals(
                        InteractionSelected, "1", null, "0.25", 2, 0),
                Neutral.getId(), signals(Neutral, "1", null, "0", 0, 0),
                InteractionDismissed.getId(), signals(
                        InteractionDismissed, "1", null, "-0.25", 0, 2));
        return scenario("G-INTERACTION", "Implicit interactions",
                List.of(InteractionDismissed, Neutral, InteractionSelected),
                personalizedContext(stockForAll(
                                "100", InteractionSelected, Neutral, InteractionDismissed),
                        TastePreferenceProfile.empty(), List.of(),
                        RecommendationRequest.forServings(2), personalization),
                List.of(InteractionSelected, Neutral, InteractionDismissed),
                mergeReasons(
                        reasons(InteractionSelected,
                                RecommendationReasonCode.RECIPE_REPEATEDLY_SELECTED),
                        reasons(InteractionDismissed,
                                RecommendationReasonCode.RECIPE_REPEATEDLY_DISMISSED)),
                Map.of(), Optional.empty(),
                "Repeated explicit actions produce a weak, symmetric implicit preference.");
    }

    private static GoldenScenario householdConflict() {
        Recipe HouseholdAccepted = candidate("r4-h-accepted", "Household Accepted", SAVORY, null);
        Recipe HouseholdRejected = candidate("r4-h-rejected", "Household Rejected", SWEET, null);
        HouseholdMemberPreference household = new HouseholdMemberPreference(
                "R4 member", new TastePreferenceProfile(Map.of(
                        SAVORY.getId(), java.math.BigDecimal.ONE,
                        SWEET.getId(), java.math.BigDecimal.ONE.negate())),
                RecommendationConstraints.none());
        return scenario("H-HOUSEHOLD", "Household conflict",
                List.of(HouseholdRejected, HouseholdAccepted),
                personalizedContext(stockForAll("100", HouseholdAccepted, HouseholdRejected),
                        TastePreferenceProfile.empty(), List.of(household),
                        RecommendationRequest.forServings(2), Map.of()),
                List.of(HouseholdAccepted, HouseholdRejected),
                mergeReasons(
                        reasons(HouseholdAccepted,
                                RecommendationReasonCode.HOUSEHOLD_STRONG_MATCH),
                        reasons(HouseholdRejected,
                                RecommendationReasonCode.HOUSEHOLD_CONFLICT,
                                RecommendationReasonCode.HOUSEHOLD_MEMBER_DISLIKES)),
                forbidden(HouseholdRejected, RecommendationReasonCode.HOUSEHOLD_STRONG_MATCH),
                Optional.of(HouseholdRejected.getId()),
                "A strong household rejection must retain its cap and conflict reasons.");
    }

    private static GoldenScenario strongMatchGuardrail() {
        Recipe PersonalizedOnly = candidate("r4-i-personal", "Personalized Only");
        Map<UUID, RecipePersonalizationSignals> personalization = Map.of(
                PersonalizedOnly.getId(), signals(
                        PersonalizedOnly, "1", "1", "0.49", 100, 0));
        return scenario("I-STRONG", "Strong-match guardrail",
                List.of(PersonalizedOnly),
                personalizedContext(stockForAll("0", PersonalizedOnly),
                        TastePreferenceProfile.empty(), List.of(),
                        RecommendationRequest.forServings(2), personalization),
                List.of(PersonalizedOnly),
                reasons(PersonalizedOnly, RecommendationReasonCode.RECIPE_STRONGLY_LIKED),
                forbidden(PersonalizedOnly,
                        RecommendationReasonCode.TASTE_STRONG_MATCH,
                        RecommendationReasonCode.HOUSEHOLD_STRONG_MATCH),
                Optional.empty(),
                "Personalization must not manufacture taste or household strong-match reasons.");
    }

    private static GoldenScenario ingredientAlternative() {
        Ingredient standard = ingredient("R4 alternative standard");
        Ingredient replacement = ingredient("R4 alternative replacement");
        Recipe AlternativeAvailable = alternativeCandidate(
                "r4-j-alternative", "Alternative Available", standard, replacement);
        List<InventoryItem> inventory = List.of(
                stock("r4-j-alternative", replacement, "100", Unit.GRAM));
        return scenario("J-ALTERNATIVE", "Ingredient alternatives",
                List.of(AlternativeAvailable), pantryContext(inventory),
                List.of(AlternativeAvailable),
                reasons(AlternativeAvailable,
                        RecommendationReasonCode.PANTRY_FULL_COVERAGE,
                        RecommendationReasonCode.ALTERNATIVE_AVAILABLE,
                        RecommendationReasonCode.ALTERNATIVE_IMPROVES_COVERAGE),
                Map.of(), Optional.empty(),
                "The available non-default option must satisfy the group without a second bonus.");
    }

    private static GoldenScenario compoundTradeOff() {
        Recipe BasisStrongNeutral = candidate("r4-k-strong", "Basis Strong Neutral");
        Recipe BasisWeakLiked = candidate("r4-k-weak", "Basis Weak Liked");
        Map<UUID, RecipePersonalizationSignals> personalization = Map.of(
                BasisStrongNeutral.getId(), signals(
                        BasisStrongNeutral, "0.000001", null, "0", 0, 0),
                BasisWeakLiked.getId(), signals(BasisWeakLiked, "1", "1", "0", 0, 0));
        return scenario("K-COMPOUND", "Compound trade-off",
                List.of(BasisWeakLiked, BasisStrongNeutral),
                personalizedContext(stockForAll("100", BasisStrongNeutral),
                        TastePreferenceProfile.empty(), List.of(),
                        RecommendationRequest.forServings(2), personalization),
                List.of(BasisStrongNeutral, BasisWeakLiked),
                reasons(BasisStrongNeutral, RecommendationReasonCode.PANTRY_FULL_COVERAGE),
                Map.of(), Optional.empty(),
                "Maximum preference plus variety must not overcome a full pantry-fit gap.");
    }

    private static GoldenScenario neutralColdStart() {
        Recipe Alpha = candidate("r4-l-alpha", "Alpha Cold Start");
        Recipe Beta = candidate("r4-l-beta", "Beta Cold Start");
        Map<UUID, RecipePersonalizationSignals> personalization = Map.of(
                Alpha.getId(), signals(Alpha, "1", null, "0", 0, 0),
                Beta.getId(), signals(Beta, "1", null, "0", 0, 0));
        return scenario("L-NEUTRAL", "Neutral cold start",
                List.of(Beta, Alpha),
                personalizedContext(stockForAll("100", Alpha, Beta),
                        TastePreferenceProfile.empty(), List.of(),
                        RecommendationRequest.forServings(2), personalization),
                List.of(Alpha, Beta),
                mergeReasons(
                        reasons(Alpha, RecommendationReasonCode.RECIPE_NEVER_COOKED),
                        reasons(Beta, RecommendationReasonCode.RECIPE_NEVER_COOKED)),
                Map.of(), Optional.empty(),
                "Equal cold-start freshness is neutral to relative ranking; stable name wins.");
    }

    private static GoldenScenario deterministicTie() {
        Recipe Alpha = candidate("r4-m-alpha", "Alpha Deterministic");
        Recipe Beta = candidate("r4-m-beta", "Beta Deterministic");
        return scenario("M-TIE", "Deterministic tie",
                List.of(Beta, Alpha), pantryContext(stockForAll("100", Alpha, Beta)),
                List.of(Alpha, Beta), Map.of(), Map.of(), Optional.empty(),
                "A complete score tie must use the documented stable fallback.");
    }

    private static GoldenScenario edgeSamePersonalization() {
        Recipe Alpha = candidate("r4-n-alpha", "Alpha Same Feedback");
        Recipe Beta = candidate("r4-n-beta", "Beta Same Feedback");
        Map<UUID, RecipePersonalizationSignals> personalization = Map.of(
                Alpha.getId(), signals(Alpha, "0.5", "0.75", "0", 0, 0),
                Beta.getId(), signals(Beta, "0.5", "0.75", "0", 0, 0));
        return scenario("N-EDGE", "Identical personalization",
                List.of(Beta, Alpha),
                personalizedContext(stockForAll("100", Alpha, Beta),
                        TastePreferenceProfile.empty(), List.of(),
                        RecommendationRequest.forServings(2), personalization),
                List.of(Alpha, Beta),
                mergeReasons(
                        reasons(Alpha, RecommendationReasonCode.RECIPE_EXPLICITLY_LIKED),
                        reasons(Beta, RecommendationReasonCode.RECIPE_EXPLICITLY_LIKED)),
                Map.of(), Optional.empty(),
                "Equal feedback and freshness must not change the prior relative order.");
    }

    private static GoldenScenario scenario(
            String id, String description, List<Recipe> candidates,
            RecommendationContext context, List<Recipe> expectedOrder,
            Map<UUID, Set<RecommendationReasonCode>> requiredReasons,
            Map<UUID, Set<RecommendationReasonCode>> forbiddenReasons,
            Optional<UUID> cappedRecipeId, String rationale) {
        return new GoldenScenario(id, description, candidates, context, expectedOrder,
                requiredReasons, forbiddenReasons, cappedRecipeId, rationale);
    }

    private static Ingredient ingredientAt(Recipe recipe, int groupIndex) {
        return recipe.getIngredientGroups().get(groupIndex)
                .getStandardOption().getIngredient();
    }

    private static Map<UUID, Set<RecommendationReasonCode>> reasons(
            Recipe recipe, RecommendationReasonCode... reasons) {
        return Map.of(recipe.getId(), Set.of(reasons));
    }

    private static Map<UUID, Set<RecommendationReasonCode>> forbidden(
            Recipe recipe, RecommendationReasonCode... reasons) {
        return Map.of(recipe.getId(), Set.of(reasons));
    }

    @SafeVarargs
    private static Map<UUID, Set<RecommendationReasonCode>> mergeReasons(
            Map<UUID, Set<RecommendationReasonCode>>... maps) {
        Map<UUID, Set<RecommendationReasonCode>> merged = new LinkedHashMap<>();
        for (Map<UUID, Set<RecommendationReasonCode>> map : maps) {
            merged.putAll(map);
        }
        return Map.copyOf(merged);
    }

    record GoldenScenario(
            String id,
            String description,
            List<Recipe> candidates,
            RecommendationContext context,
            List<Recipe> expectedOrder,
            Map<UUID, Set<RecommendationReasonCode>> requiredReasons,
            Map<UUID, Set<RecommendationReasonCode>> forbiddenReasons,
            Optional<UUID> cappedRecipeId,
            String rationale) {

        GoldenScenario {
            candidates = List.copyOf(candidates);
            expectedOrder = List.copyOf(expectedOrder);
            requiredReasons = Map.copyOf(requiredReasons);
            forbiddenReasons = Map.copyOf(forbiddenReasons);
        }

        @Override
        public String toString() {
            return id + " – " + description;
        }
    }
}
