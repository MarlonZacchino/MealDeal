package de.mealdeal.service.recommendation;

import de.mealdeal.domain.DishType;
import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.InventoryItem;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredientGroup;
import de.mealdeal.domain.RecipeIngredientOption;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeRecommendationServiceTest {

    private static final Taste SAVORY = taste("Herzhaft");
    private final RecipeRecommendationService service = new RecipeRecommendationService();

    @Test
    void hardRecipeExclusionRemovesCandidateBeforeRanking() {
        Ingredient rice = ingredient("Reis");
        Recipe candidate = singleIngredientRecipe("rice", "Reis", rice, "100", Unit.GRAM);
        RecommendationContext context = context(2, List.of(), TastePreferenceProfile.empty(),
                List.of(), new RecommendationConstraints(Set.of(candidate.getId()), Set.of()),
                Optional.empty(), Optional.empty());

        RecommendationOutcome outcome = service.recommend(List.of(candidate), context);

        assertTrue(outcome.recommendations().isEmpty());
        assertEquals(List.of(RecommendationReasonCode.RECIPE_HARD_EXCLUDED),
                outcome.exclusions().getFirst().reasonCodes());
    }

    @Test
    void allExcludedAlternativesMakeIngredientGroupIneligible() {
        Ingredient chicken = ingredient("Hähnchen");
        Ingredient tofu = ingredient("Tofu");
        Recipe recipe = alternativeRecipe("alternative", chicken, tofu);
        RecommendationConstraints constraints = new RecommendationConstraints(
                Set.of(), Set.of(chicken.getId(), tofu.getId()));

        CandidateEligibility eligibility = service.evaluateEligibility(
                recipe, context(2, List.of(), TastePreferenceProfile.empty(), List.of(),
                        constraints, Optional.empty(), Optional.empty()));

        assertFalse(eligibility.eligible());
        assertTrue(eligibility.reasonCodes().contains(
                RecommendationReasonCode.INGREDIENT_GROUP_HARD_EXCLUDED));
    }

    @Test
    void safeAlternativeKeepsRecipeEligibleAndIgnoresExcludedOption() {
        Ingredient chicken = ingredient("Hähnchen");
        Ingredient tofu = ingredient("Tofu");
        Recipe candidate = alternativeRecipe("safe-alternative", chicken, tofu);
        RecommendationContext context = context(2,
                List.of(stock("tofu", tofu, "100", Unit.GRAM)),
                TastePreferenceProfile.empty(), List.of(),
                new RecommendationConstraints(Set.of(), Set.of(chicken.getId())),
                Optional.empty(), Optional.empty());

        RecipeRecommendation recommendation = service.recommend(List.of(candidate), context)
                .recommendations().getFirst();

        assertEquals(tofu, selectedIngredient(candidate, recommendation));
        assertTrue(recommendation.reasonCodes().contains(
                RecommendationReasonCode.HARD_EXCLUDED_ALTERNATIVE_IGNORED));
    }

    @Test
    void compatibleInventoryUnitsProvideFullPantryCoverage() {
        Ingredient flour = ingredient("Mehl");
        Recipe candidate = singleIngredientRecipe("flour", "Brot", flour, "1000", Unit.GRAM);

        RecipeRecommendation recommendation = service.recommend(List.of(candidate),
                RecommendationContext.pantryOnly(2,
                        List.of(stock("flour", flour, "1", Unit.KILOGRAM))))
                .recommendations().getFirst();

        assertSignalEquals(BigDecimal.ONE, recommendation,
                RecommendationSignal.PANTRY_COVERAGE);
        assertTrue(recommendation.reasonCodes().contains(
                RecommendationReasonCode.PANTRY_FULL_COVERAGE));
    }

    @Test
    void incompatibleUnitsDoNotContributeToCoverage() {
        Ingredient eggs = ingredient("Eier");
        Recipe candidate = singleIngredientRecipe("eggs", "Ei", eggs, "2", Unit.PIECE);

        RecipeRecommendation recommendation = service.recommend(List.of(candidate),
                RecommendationContext.pantryOnly(2,
                        List.of(stock("eggs", eggs, "200", Unit.GRAM))))
                .recommendations().getFirst();

        assertSignalEquals(BigDecimal.ZERO, recommendation,
                RecommendationSignal.PANTRY_COVERAGE);
    }

    @Test
    void alternativeCanFullyCoverOneGroupWithoutDoubleReward() {
        Ingredient chicken = ingredient("Hähnchen");
        Ingredient tofu = ingredient("Tofu");
        Recipe candidate = alternativeRecipe("rescue", chicken, tofu);
        RecommendationContext context = RecommendationContext.pantryOnly(2, List.of(
                stock("chicken", chicken, "100", Unit.GRAM),
                stock("tofu", tofu, "100", Unit.GRAM)));

        RecipeRecommendation recommendation = service.recommend(List.of(candidate), context)
                .recommendations().getFirst();

        assertSignalEquals(BigDecimal.ONE, recommendation,
                RecommendationSignal.PANTRY_COVERAGE);
        assertEquals(0, recommendation.missingIngredientGroupCount());
        assertEquals(candidate.getIngredientGroups().getFirst().getStandardOptionId(),
                recommendation.suggestedIngredientOptions().values().iterator().next());
        assertFalse(recommendation.reasonCodes().contains(
                RecommendationReasonCode.ALTERNATIVE_AVAILABLE));
    }

    @Test
    void betterAlternativeIsSuggestedAndExplained() {
        Ingredient chicken = ingredient("Hähnchen");
        Ingredient tofu = ingredient("Tofu");
        Recipe candidate = alternativeRecipe("better", chicken, tofu);

        RecipeRecommendation recommendation = service.recommend(List.of(candidate),
                RecommendationContext.pantryOnly(2,
                        List.of(stock("tofu", tofu, "100", Unit.GRAM))))
                .recommendations().getFirst();

        assertEquals(tofu, selectedIngredient(candidate, recommendation));
        assertTrue(recommendation.reasonCodes().contains(
                RecommendationReasonCode.ALTERNATIVE_AVAILABLE));
        assertTrue(recommendation.reasonCodes().contains(
                RecommendationReasonCode.ALTERNATIVE_IMPROVES_COVERAGE));
        assertTrue(recommendation.signals().valueOf(
                RecommendationSignal.INGREDIENT_ALTERNATIVE_FIT).isEmpty());
    }

    @Test
    void requestedServingsScaleRequiredQuantityBeforeCoverage() {
        Ingredient rice = ingredient("Reis");
        Recipe candidate = singleIngredientRecipe("scaled", "Reis", rice, "100", Unit.GRAM);

        RecipeRecommendation recommendation = service.recommend(List.of(candidate),
                RecommendationContext.pantryOnly(4,
                        List.of(stock("rice", rice, "100", Unit.GRAM))))
                .recommendations().getFirst();

        assertSignalEquals(new BigDecimal("0.5"), recommendation,
                RecommendationSignal.PANTRY_COVERAGE);
        assertEquals(1, recommendation.missingIngredientGroupCount());
    }

    @Test
    void addingMatchingPantryQuantityNeverReducesOptimalPantryUtility() {
        Ingredient rice = ingredient("Reis");
        Recipe candidate = singleIngredientRecipe("monotonic", "Reis", rice, "100", Unit.GRAM);
        BigDecimal less = pantryUtility(candidate,
                List.of(stock("less", rice, "25", Unit.GRAM)));
        BigDecimal more = pantryUtility(candidate,
                List.of(stock("more", rice, "75", Unit.GRAM)));

        assertTrue(more.compareTo(less) >= 0);
    }

    @Test
    void fulfillingAnotherGroupCannotIncreaseMissingPenalty() {
        Ingredient rice = ingredient("Reis");
        Ingredient beans = ingredient("Bohnen");
        Recipe candidate = recipe("two-groups", "Reis mit Bohnen", List.of(
                oneGroup("rice-group", rice).getFirst(),
                oneGroup("bean-group", beans).getFirst()), SAVORY);
        RecipeRecommendation oneCovered = service.recommend(List.of(candidate),
                RecommendationContext.pantryOnly(2,
                        List.of(stock("only-rice", rice, "100", Unit.GRAM))))
                .recommendations().getFirst();
        RecipeRecommendation bothCovered = service.recommend(List.of(candidate),
                RecommendationContext.pantryOnly(2, List.of(
                        stock("all-rice", rice, "100", Unit.GRAM),
                        stock("all-beans", beans, "100", Unit.GRAM))))
                .recommendations().getFirst();

        BigDecimal firstPenalty = oneCovered.signals()
                .valueOf(RecommendationSignal.MISSING_INGREDIENT_PENALTY).orElseThrow();
        BigDecimal secondPenalty = bothCovered.signals()
                .valueOf(RecommendationSignal.MISSING_INGREDIENT_PENALTY).orElseThrow();
        assertTrue(secondPenalty.compareTo(firstPenalty) <= 0);
    }

    @Test
    void multipleUndercoveredGroupsProduceExplicitReason() {
        Ingredient rice = ingredient("Reis");
        Ingredient beans = ingredient("Bohnen");
        Recipe candidate = recipe("missing-many", "Leer", List.of(
                oneGroup("missing-rice", rice).getFirst(),
                oneGroup("missing-beans", beans).getFirst()), SAVORY);

        RecipeRecommendation result = service.recommend(List.of(candidate),
                RecommendationContext.pantryOnly(2, List.of()))
                .recommendations().getFirst();

        assertEquals(2, result.missingIngredientGroupCount());
        assertTrue(result.reasonCodes().contains(
                RecommendationReasonCode.MISSING_MULTIPLE_INGREDIENT_GROUPS));
    }

    @Test
    void inventoryOrderDoesNotChangeScoreOrSuggestedOptions() {
        Ingredient rice = ingredient("Reis");
        Recipe candidate = singleIngredientRecipe("order", "Reis", rice, "100", Unit.GRAM);
        InventoryItem first = stock("first", rice, "40", Unit.GRAM);
        InventoryItem second = stock("second", rice, "0.06", Unit.KILOGRAM);
        RecipeRecommendation forward = service.recommend(List.of(candidate),
                RecommendationContext.pantryOnly(2, List.of(first, second)))
                .recommendations().getFirst();
        RecipeRecommendation reverse = service.recommend(List.of(candidate),
                RecommendationContext.pantryOnly(2, List.of(second, first)))
                .recommendations().getFirst();

        assertEquals(0, forward.score().compareTo(reverse.score()));
        assertEquals(forward.suggestedIngredientOptions(), reverse.suggestedIngredientOptions());
    }

    @Test
    void explicitTasteAffinityRanksStrongMatchAheadOfDislike() {
        Ingredient rice = ingredient("Reis");
        Taste spicy = taste("Scharf");
        Taste mild = taste("Mild");
        Recipe liked = recipe("liked", "Scharf", oneGroup("liked", rice), spicy);
        Recipe disliked = recipe("disliked", "Mild", oneGroup("disliked", rice), mild);
        TastePreferenceProfile preferences = new TastePreferenceProfile(Map.of(
                spicy.getId(), BigDecimal.ONE, mild.getId(), BigDecimal.ONE.negate()));
        RecommendationContext context = context(2,
                List.of(stock("rice-taste", rice, "100", Unit.GRAM)), preferences,
                List.of(), RecommendationConstraints.none(), Optional.empty(), Optional.empty());

        RecommendationOutcome outcome = service.recommend(List.of(disliked, liked), context);

        assertEquals(liked, outcome.recommendations().getFirst().recipe());
        assertTrue(outcome.recommendations().getFirst().reasonCodes().contains(
                RecommendationReasonCode.TASTE_STRONG_MATCH));
    }

    @Test
    void knownRecipeWithinLimitRanksBeforeUnknownDurationOnTie() {
        Ingredient rice = ingredient("Reis");
        Recipe known = recipe("known", "Bekannt", oneGroup("known", rice), List.of(SAVORY),
                10, 10, null, null, DishType.MAIN);
        Recipe unknown = recipe("unknown", "Unbekannt", oneGroup("unknown", rice), SAVORY);
        RecommendationContext context = context(2,
                List.of(stock("rice-time", rice, "200", Unit.GRAM)),
                TastePreferenceProfile.empty(), List.of(), RecommendationConstraints.none(),
                Optional.of(Duration.ofMinutes(30)), Optional.empty());

        RecommendationOutcome outcome = service.recommend(List.of(unknown, known), context);

        assertEquals(known, outcome.recommendations().getFirst().recipe());
        assertTrue(outcome.recommendations().get(1).reasonCodes().contains(
                RecommendationReasonCode.TIME_UNKNOWN));
    }

    @Test
    void householdStrongRejectionCapsOtherwiseHighScore() {
        Ingredient rice = ingredient("Reis");
        Taste dislikedTaste = taste("Bitter");
        Recipe disliked = recipe("household-disliked", "Bitter", oneGroup("hd", rice), dislikedTaste);
        Recipe neutral = recipe("household-neutral", "Neutral", oneGroup("hn", rice), SAVORY);
        HouseholdMemberPreference member = new HouseholdMemberPreference(
                "Alex", new TastePreferenceProfile(Map.of(
                        dislikedTaste.getId(), BigDecimal.ONE.negate())),
                RecommendationConstraints.none());
        RecommendationContext context = context(2,
                List.of(stock("rice-household", rice, "200", Unit.GRAM)),
                TastePreferenceProfile.empty(), List.of(member), RecommendationConstraints.none(),
                Optional.empty(), Optional.empty());

        RecommendationOutcome outcome = service.recommend(List.of(disliked, neutral), context);
        RecipeRecommendation rejected = outcome.recommendations().stream()
                .filter(result -> result.recipe().equals(disliked)).findFirst().orElseThrow();

        assertEquals(neutral, outcome.recommendations().getFirst().recipe());
        assertTrue(rejected.score().compareTo(new BigDecimal("0.39")) <= 0);
        assertTrue(rejected.reasonCodes().contains(
                RecommendationReasonCode.HOUSEHOLD_MEMBER_DISLIKES));
        assertTrue(rejected.reasonCodes().contains(
                RecommendationReasonCode.HOUSEHOLD_CONFLICT));
        assertFalse(rejected.reasonCodes().contains(
                RecommendationReasonCode.HOUSEHOLD_STRONG_MATCH));
    }

    @Test
    void householdHardConstraintExcludesBeforePreferenceScoring() {
        Ingredient rice = ingredient("Reis");
        Recipe candidate = singleIngredientRecipe(
                "household-hard", "Reis", rice, "100", Unit.GRAM);
        HouseholdMemberPreference member = new HouseholdMemberPreference(
                "Alex", TastePreferenceProfile.empty(),
                new RecommendationConstraints(Set.of(), Set.of(rice.getId())));
        RecommendationContext context = context(2, List.of(), TastePreferenceProfile.empty(),
                List.of(member), RecommendationConstraints.none(),
                Optional.empty(), Optional.empty());

        RecommendationOutcome outcome = service.recommend(List.of(candidate), context);

        assertTrue(outcome.recommendations().isEmpty());
        assertTrue(outcome.exclusions().getFirst().reasonCodes().contains(
                RecommendationReasonCode.INGREDIENT_GROUP_HARD_EXCLUDED));
    }

    @Test
    void timeOverLimitUsesLimitToTotalRatio() {
        Ingredient rice = ingredient("Reis");
        Recipe candidate = recipe("slow", "Langsam", oneGroup("slow", rice), List.of(SAVORY),
                20, 40, null, null, DishType.MAIN);
        RecommendationContext context = context(2, List.of(), TastePreferenceProfile.empty(),
                List.of(), RecommendationConstraints.none(), Optional.of(Duration.ofMinutes(30)),
                Optional.empty());

        RecipeRecommendation result = service.recommend(List.of(candidate), context)
                .recommendations().getFirst();

        assertSignalEquals(new BigDecimal("0.5"), result,
                RecommendationSignal.PREPARATION_TIME_FIT);
        assertTrue(result.reasonCodes().contains(RecommendationReasonCode.TIME_OVER_LIMIT));
    }

    @Test
    void duplicatingPositiveMembersCannotRemoveStrongRejectionCap() {
        Ingredient rice = ingredient("Reis");
        Taste taste = taste("Kräftig");
        Recipe candidate = recipe("fairness", "Kräftig", oneGroup("fairness", rice), taste);
        HouseholdMemberPreference rejection = member("reject", taste, "-1");
        HouseholdMemberPreference approval = member("approve", taste, "1");
        List<HouseholdMemberPreference> manyApprovals = new ArrayList<>();
        manyApprovals.add(rejection);
        manyApprovals.add(approval);
        manyApprovals.add(member("approve-2", taste, "1"));
        manyApprovals.add(member("approve-3", taste, "1"));
        RecommendationContext context = context(2,
                List.of(stock("rice-fairness", rice, "100", Unit.GRAM)),
                TastePreferenceProfile.empty(), manyApprovals, RecommendationConstraints.none(),
                Optional.empty(), Optional.empty());

        RecipeRecommendation result = service.recommend(List.of(candidate), context)
                .recommendations().getFirst();

        assertTrue(result.score().compareTo(new BigDecimal("0.39")) <= 0);
    }

    @Test
    void desiredDishTypeIsAnEligibilityConstraint() {
        Ingredient rice = ingredient("Reis");
        Recipe main = singleIngredientRecipe("main", "Hauptgericht", rice, "100", Unit.GRAM);
        Recipe side = recipe("side", "Beilage", oneGroup("side", rice), List.of(SAVORY),
                null, null, null, null, DishType.SIDE);
        RecommendationContext context = context(2, List.of(), TastePreferenceProfile.empty(),
                List.of(), RecommendationConstraints.none(), Optional.empty(),
                Optional.of(DishType.SIDE));

        RecommendationOutcome outcome = service.recommend(List.of(main, side), context);

        assertEquals(List.of(side), outcome.recommendations().stream()
                .map(RecipeRecommendation::recipe).toList());
        assertEquals(RecommendationReasonCode.DISH_TYPE_MISMATCH,
                outcome.exclusions().getFirst().reasonCodes().getFirst());
    }

    @Test
    void exactScoreTieUsesStableRecipeIdentityAfterName() {
        Ingredient rice = ingredient("Reis");
        Recipe first = recipe("a", "Gleich", oneGroup("tie-a", rice), SAVORY);
        Recipe second = recipe("b", "Gleich", oneGroup("tie-b", rice), SAVORY);
        List<Recipe> expected = first.getId().compareTo(second.getId()) < 0
                ? List.of(first, second) : List.of(second, first);

        List<Recipe> actual = service.recommend(List.of(second, first),
                        RecommendationContext.pantryOnly(2, List.of()))
                .recommendations().stream().map(RecipeRecommendation::recipe).toList();

        assertEquals(expected, actual);
    }

    @Test
    void repeatedEvaluationWithSameInputsIsExactlyDeterministic() {
        Ingredient rice = ingredient("Reis");
        Recipe candidate = singleIngredientRecipe(
                "deterministic", "Reis", rice, "100", Unit.GRAM);
        RecommendationContext context = RecommendationContext.pantryOnly(2,
                List.of(stock("deterministic", rice, "75", Unit.GRAM)));

        RecipeRecommendation first = service.recommend(List.of(candidate), context)
                .recommendations().getFirst();
        RecipeRecommendation second = service.recommend(List.of(candidate), context)
                .recommendations().getFirst();

        assertEquals(first.score(), second.score());
        assertEquals(first.reasonCodes(), second.reasonCodes());
        assertEquals(first.suggestedIngredientOptions(), second.suggestedIngredientOptions());
    }

    @Test
    void absentPreferenceTimeHistoryAndVarietyDataStayUnavailable() {
        Ingredient rice = ingredient("Reis");
        Recipe candidate = singleIngredientRecipe("missing-data", "Reis", rice, "100", Unit.GRAM);

        RecommendationSignals signals = service.recommend(List.of(candidate),
                        RecommendationContext.pantryOnly(2, List.of()))
                .recommendations().getFirst().signals();

        assertTrue(signals.valueOf(RecommendationSignal.TASTE_AFFINITY).isEmpty());
        assertTrue(signals.valueOf(RecommendationSignal.PREPARATION_TIME_FIT).isEmpty());
        assertTrue(signals.valueOf(RecommendationSignal.RECENT_MEAL_PENALTY).isEmpty());
        assertTrue(signals.valueOf(RecommendationSignal.VARIETY_SCORE).isEmpty());
        assertTrue(signals.valueOf(RecommendationSignal.RECIPE_PREFERENCE).isEmpty());
        assertTrue(signals.valueOf(RecommendationSignal.HOUSEHOLD_PREFERENCE).isEmpty());
    }

    @Test
    void recipeWithoutIngredientGroupsIsExcludedAsStructurallyIncomplete() {
        Recipe incomplete = recipe("empty", "Leer", List.of(), SAVORY);

        CandidateEligibility eligibility = service.evaluateEligibility(
                incomplete, RecommendationContext.pantryOnly(2, List.of()));

        assertFalse(eligibility.eligible());
        assertEquals(List.of(RecommendationReasonCode.MISSING_REQUIRED_INGREDIENT_STRUCTURE),
                eligibility.reasonCodes());
    }

    private BigDecimal pantryUtility(Recipe recipe, List<InventoryItem> inventory) {
        RecipeRecommendation result = service.recommend(
                        List.of(recipe), RecommendationContext.pantryOnly(2, inventory))
                .recommendations().getFirst();
        BigDecimal coverage = result.signals()
                .valueOf(RecommendationSignal.PANTRY_COVERAGE).orElseThrow();
        BigDecimal completeness = BigDecimal.ONE.subtract(result.signals()
                .valueOf(RecommendationSignal.MISSING_INGREDIENT_PENALTY).orElseThrow());
        return new BigDecimal("0.35").multiply(coverage)
                .add(new BigDecimal("0.15").multiply(completeness));
    }

    private static Recipe singleIngredientRecipe(
            String key, String name, Ingredient ingredient, String quantity, Unit unit) {
        RecipeIngredientOption option = option(key, ingredient, quantity, unit, 0);
        return recipe(key, name, List.of(group(key, List.of(option), 0)), SAVORY);
    }

    private static Recipe alternativeRecipe(String key, Ingredient standard, Ingredient alternative) {
        RecipeIngredientOption first = option(key + ":standard", standard, "100", Unit.GRAM, 0);
        RecipeIngredientOption second = option(key + ":alternative", alternative, "100", Unit.GRAM, 1);
        return recipe(key, "Alternative " + key,
                List.of(group(key, List.of(first, second), 0)), SAVORY);
    }

    private static List<RecipeIngredientGroup> oneGroup(String key, Ingredient ingredient) {
        RecipeIngredientOption option = option(key, ingredient, "100", Unit.GRAM, 0);
        return List.of(group(key, List.of(option), 0));
    }

    private static RecommendationContext context(
            int servings,
            List<InventoryItem> inventory,
            TastePreferenceProfile preferences,
            List<HouseholdMemberPreference> household,
            RecommendationConstraints constraints,
            Optional<Duration> availableTime,
            Optional<DishType> dishType) {
        return new RecommendationContext(
                new RecommendationRequest(servings, availableTime, dishType),
                inventory, preferences, household, constraints);
    }

    private static HouseholdMemberPreference member(
            String id, Taste taste, String affinity) {
        return new HouseholdMemberPreference(id,
                new TastePreferenceProfile(Map.of(taste.getId(), new BigDecimal(affinity))),
                RecommendationConstraints.none());
    }

    private static Ingredient selectedIngredient(
            Recipe recipe, RecipeRecommendation recommendation) {
        var group = recipe.getIngredientGroups().getFirst();
        var optionId = recommendation.suggestedIngredientOptions().get(group.getId());
        return group.getOptions().stream().filter(option -> option.getId().equals(optionId))
                .findFirst().orElseThrow().getIngredient();
    }

    private static void assertSignalEquals(
            BigDecimal expected,
            RecipeRecommendation recommendation,
            RecommendationSignal signal) {
        assertEquals(0, expected.compareTo(
                recommendation.signals().valueOf(signal).orElseThrow()));
    }
}
