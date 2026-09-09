package de.mealdeal.service.recommendation;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredientGroup;
import de.mealdeal.domain.Unit;
import de.mealdeal.service.RecipeScaler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static de.mealdeal.service.recommendation.RecommendationTestFixtures.group;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.ingredient;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.option;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.recipe;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.stock;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.taste;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesiredIngredientPreferenceTest {

    private final Ingredient tomato = ingredient("Wunsch Tomate");
    private final Ingredient onion = ingredient("Wunsch Zwiebel");
    private final Ingredient garlic = ingredient("Wunsch Knoblauch");

    @Test
    void absentWishKeepsPreviousScoresSignalsAndRankingExactly() {
        Recipe alpha = candidate("empty-a", "Alpha", tomato, onion, garlic);
        Recipe beta = candidate("empty-b", "Beta",
                ingredient("A"), ingredient("B"), ingredient("C"));
        var oldContext = RecommendationContext.pantryOnly(2, List.of());
        var explicitEmpty = context(List.of());
        var service = new RecipeRecommendationService();

        RecommendationOutcome oldResult = service.recommend(List.of(beta, alpha), oldContext);
        RecommendationOutcome emptyResult = service.recommend(List.of(beta, alpha), explicitEmpty);

        assertEquals(oldResult, emptyResult);
        assertTrue(emptyResult.recommendations().stream().allMatch(result -> result.signals()
                .valueOf(RecommendationSignal.DESIRED_INGREDIENT_FIT).isEmpty()));
    }

    @Test
    void fullPartialAndZeroMatchesAreMonotonicWithoutFilteringCandidates() {
        Recipe full = candidate("match-full", "Full", tomato, onion, garlic);
        Recipe partial = candidate("match-partial", "Partial", tomato,
                ingredient("Partial B"), ingredient("Partial C"));
        Recipe none = candidate("match-none", "None",
                ingredient("None A"), ingredient("None B"), ingredient("None C"));

        RecommendationOutcome result = new RecipeRecommendationService().recommend(
                List.of(none, partial, full), context(List.of(tomato, onion, garlic)));

        assertEquals(List.of(full, partial, none), result.recommendations().stream()
                .map(RecipeRecommendation::recipe).toList());
        assertEquals(0, signal(result, full).compareTo(BigDecimal.ONE));
        assertEquals(0, signal(result, partial).compareTo(
                BigDecimal.ONE.divide(BigDecimal.valueOf(3), java.math.MathContext.DECIMAL128)));
        assertEquals(0, signal(result, none).compareTo(BigDecimal.ZERO));
        assertEquals(3, result.recommendations().size());
        assertTrue(recommendation(result, full).reasonCodes()
                .contains(RecommendationReasonCode.DESIRED_INGREDIENT_MATCH));
        assertTrue(recommendation(result, partial).reasonCodes()
                .contains(RecommendationReasonCode.DESIRED_INGREDIENT_PARTIAL_MATCH));
        assertFalse(recommendation(result, none).reasonCodes().stream()
                .anyMatch(code -> code == RecommendationReasonCode.DESIRED_INGREDIENT_MATCH
                        || code == RecommendationReasonCode.DESIRED_INGREDIENT_PARTIAL_MATCH));
    }

    @Test
    void nonDefaultAlternativeFulfilsDesiredIngredientWithoutChangingPantryChoice() {
        Ingredient milk = ingredient("Wunsch Milch");
        Ingredient oatMilk = ingredient("Wunsch Hafermilch");
        Recipe recipe = recipe("alternative-wish", "Alternative", List.of(group(
                "alternative-wish", List.of(
                        option("wish-milk", milk, "100", Unit.MILLILITER, 0),
                        option("wish-oat", oatMilk, "100", Unit.MILLILITER, 1)), 0)),
                taste("Herzhaft"));
        var inventory = List.of(stock("wish-milk", milk, "100", Unit.MILLILITER));
        RecommendationContext withoutWish = RecommendationContext.pantryOnly(2, inventory);
        RecommendationContext withWish = new RecommendationContext(
                RecommendationRequest.forServings(2), inventory, TastePreferenceProfile.empty(),
                DesiredIngredientProfile.fromIngredients(List.of(oatMilk)), List.of(),
                RecommendationConstraints.none());
        var service = new RecipeRecommendationService();

        RecipeRecommendation baseline = service.recommend(List.of(recipe), withoutWish)
                .recommendations().getFirst();
        RecipeRecommendation desired = service.recommend(List.of(recipe), withWish)
                .recommendations().getFirst();

        assertEquals(0, desired.signals().valueOf(
                RecommendationSignal.DESIRED_INGREDIENT_FIT).orElseThrow()
                .compareTo(BigDecimal.ONE));
        assertEquals(baseline.signals().valueOf(RecommendationSignal.PANTRY_COVERAGE),
                desired.signals().valueOf(RecommendationSignal.PANTRY_COVERAGE));
        assertEquals(baseline.missingIngredientGroupCount(),
                desired.missingIngredientGroupCount());
        assertEquals(baseline.suggestedIngredientOptions(), desired.suggestedIngredientOptions());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.03", "0.05", "0.07"})
    void evaluatedCandidateWeightsDoNotLetWishOvercomeClearPantryDeficit(String weight) {
        Ingredient wished = ingredient("Gewünscht");
        Ingredient stocked = ingredient("Vorrat");
        Recipe desiredButMissing = candidate("weight-wish", "Wunsch", wished);
        Recipe pantryStrong = candidate("weight-pantry", "Vorrat", stocked);
        RecommendationContext context = new RecommendationContext(
                RecommendationRequest.forServings(2),
                List.of(stock("weight-pantry", stocked, "100", Unit.GRAM)),
                TastePreferenceProfile.empty(),
                DesiredIngredientProfile.fromIngredients(List.of(wished)),
                List.of(), RecommendationConstraints.none());

        List<Recipe> order = serviceWithDesiredWeight(weight)
                .recommend(List.of(desiredButMissing, pantryStrong), context)
                .recommendations().stream().map(RecipeRecommendation::recipe).toList();

        assertEquals(List.of(pantryStrong, desiredButMissing), order);
    }

    @Test
    void desiredIngredientRankingIsIndependentOfInputOrder() {
        Recipe full = candidate("stable-full", "Full", tomato, onion);
        Recipe none = candidate("stable-none", "None",
                ingredient("Stable A"), ingredient("Stable B"));
        RecommendationContext context = context(List.of(onion, tomato));
        var service = new RecipeRecommendationService();

        var forward = service.recommend(List.of(none, full), context);
        var reverse = service.recommend(List.of(full, none), context);

        assertEquals(forward, reverse);
    }

    private RecommendationContext context(List<Ingredient> desired) {
        return new RecommendationContext(
                RecommendationRequest.forServings(2), List.of(), TastePreferenceProfile.empty(),
                DesiredIngredientProfile.fromIngredients(desired), List.of(),
                RecommendationConstraints.none());
    }

    private static Recipe candidate(String key, String name, Ingredient... ingredients) {
        List<RecipeIngredientGroup> groups = java.util.stream.IntStream.range(0, ingredients.length)
                .mapToObj(index -> group(key + "-" + index, List.of(option(
                        key + "-" + index, ingredients[index], "100", Unit.GRAM, 0)), 0))
                .toList();
        return recipe(key, name, groups, taste("Wunsch Test"));
    }

    private static RecipeRecommendation recommendation(
            RecommendationOutcome outcome, Recipe recipe) {
        return outcome.recommendations().stream()
                .filter(result -> result.recipe().equals(recipe)).findFirst().orElseThrow();
    }

    private static BigDecimal signal(RecommendationOutcome outcome, Recipe recipe) {
        return recommendation(outcome, recipe).signals()
                .valueOf(RecommendationSignal.DESIRED_INGREDIENT_FIT).orElseThrow();
    }

    private static RecipeRecommendationService serviceWithDesiredWeight(String value) {
        EnumMap<RecommendationSignal, BigDecimal> weights = new EnumMap<>(
                RecommendationScoringProfile.v1().weights());
        weights.put(RecommendationSignal.DESIRED_INGREDIENT_FIT, new BigDecimal(value));
        return new RecipeRecommendationService(new RecommendationScoringProfile(
                "weight-" + value, weights,
                new BigDecimal("0.80"), new BigDecimal("0.65"), new BigDecimal("0.50"),
                new BigDecimal("0.70"), new BigDecimal("0.20"), new BigDecimal("0.39")),
                new RecipeScaler());
    }
}
