package de.mealdeal.service.recommendation;

import de.mealdeal.domain.DishType;
import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.InventoryItem;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredientGroup;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import de.mealdeal.service.RecipeScaler;
import org.junit.jupiter.api.Test;

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

class RecommendationTieBreakTest {

    private static final Taste SAVORY = taste("Tie-Herzhaft");

    @Test
    void equalScoresPreferFewerMissingGroupsBeforeCoverage() {
        Ingredient a = ingredient("Missing A");
        Ingredient b = ingredient("Missing B");
        Ingredient c = ingredient("Missing C");
        Ingredient d = ingredient("Missing D");
        Recipe fewerMissing = recipe("fewer-missing", "Fewer", List.of(
                singleGroup("missing-a", a), singleGroup("missing-b", b)), SAVORY);
        Recipe moreMissing = recipe("more-missing", "More", List.of(
                singleGroup("missing-c", c), singleGroup("missing-d", d)), SAVORY);
        RecommendationContext context = tasteOnlyContext(List.of(
                stock("missing-a", a, "100", Unit.GRAM),
                stock("missing-c", c, "50", Unit.GRAM),
                stock("missing-d", d, "50", Unit.GRAM)));

        List<Recipe> ranking = tasteOnlyService().recommend(
                        List.of(moreMissing, fewerMissing), context)
                .recommendations().stream().map(RecipeRecommendation::recipe).toList();

        assertEquals(List.of(fewerMissing, moreMissing), ranking);
    }

    @Test
    void equalScoresAndMissingCountsPreferHigherCoverage() {
        Ingredient highIngredient = ingredient("Coverage Hoch");
        Ingredient lowIngredient = ingredient("Coverage Niedrig");
        Recipe high = recipe("coverage-high", "High",
                List.of(singleGroup("coverage-high", highIngredient)), SAVORY);
        Recipe low = recipe("coverage-low", "Low",
                List.of(singleGroup("coverage-low", lowIngredient)), SAVORY);
        RecommendationContext context = tasteOnlyContext(List.of(
                stock("coverage-high", highIngredient, "75", Unit.GRAM),
                stock("coverage-low", lowIngredient, "50", Unit.GRAM)));

        List<Recipe> ranking = tasteOnlyService().recommend(List.of(low, high), context)
                .recommendations().stream().map(RecipeRecommendation::recipe).toList();

        assertEquals(List.of(high, low), ranking);
    }

    @Test
    void completeTechnicalTiePrefersShorterKnownDuration() {
        Ingredient shortIngredient = ingredient("Kurz Zutat");
        Ingredient longIngredient = ingredient("Lang Zutat");
        Recipe shortRecipe = recipe(
                "short-duration", "Späterer Name",
                List.of(singleGroup("short-duration", shortIngredient)), List.of(SAVORY),
                10, null, null, null, DishType.MAIN);
        Recipe longRecipe = recipe(
                "long-duration", "Früherer Name",
                List.of(singleGroup("long-duration", longIngredient)), List.of(SAVORY),
                20, null, null, null, DishType.MAIN);
        RecommendationContext context = RecommendationContext.pantryOnly(2, List.of(
                stock("short-duration", shortIngredient, "100", Unit.GRAM),
                stock("long-duration", longIngredient, "100", Unit.GRAM)));

        List<Recipe> ranking = new RecipeRecommendationService()
                .recommend(List.of(longRecipe, shortRecipe), context)
                .recommendations().stream().map(RecipeRecommendation::recipe).toList();

        assertEquals(List.of(shortRecipe, longRecipe), ranking);
    }

    @Test
    void finalNameAndStableIdRulesIgnoreCandidateInputOrder() {
        Ingredient ingredient = ingredient("Final Tie Zutat");
        Recipe alpha = recipe("final-alpha", "Alpha",
                List.of(singleGroup("final-alpha", ingredient)), SAVORY);
        Recipe sameNameFirst = recipe("final-same-a", "Gleich",
                List.of(singleGroup("final-same-a", ingredient)), SAVORY);
        Recipe sameNameSecond = recipe("final-same-b", "Gleich",
                List.of(singleGroup("final-same-b", ingredient)), SAVORY);
        Recipe lowerId = sameNameFirst.getId().compareTo(sameNameSecond.getId()) < 0
                ? sameNameFirst : sameNameSecond;
        Recipe higherId = lowerId == sameNameFirst ? sameNameSecond : sameNameFirst;
        RecommendationContext context = RecommendationContext.pantryOnly(2, List.of());
        RecipeRecommendationService service = new RecipeRecommendationService();

        List<Recipe> forward = service.recommend(
                        List.of(higherId, alpha, lowerId), context)
                .recommendations().stream().map(RecipeRecommendation::recipe).toList();
        List<Recipe> reverse = service.recommend(
                        List.of(lowerId, alpha, higherId), context)
                .recommendations().stream().map(RecipeRecommendation::recipe).toList();

        assertEquals(List.of(alpha, lowerId, higherId), forward);
        assertEquals(forward, reverse);
    }

    private static RecipeIngredientGroup singleGroup(String key, Ingredient ingredient) {
        return group(key, List.of(option(key, ingredient, "100", Unit.GRAM, 0)), 0);
    }

    private static RecipeRecommendationService tasteOnlyService() {
        EnumMap<RecommendationSignal, BigDecimal> weights =
                new EnumMap<>(RecommendationSignal.class);
        for (RecommendationSignal signal : RecommendationSignal.values()) {
            weights.put(signal, BigDecimal.ZERO);
        }
        weights.put(RecommendationSignal.TASTE_AFFINITY, BigDecimal.ONE);
        RecommendationScoringProfile profile = new RecommendationScoringProfile(
                "tie-test", weights,
                new BigDecimal("0.80"), new BigDecimal("0.65"),
                new BigDecimal("0.50"), new BigDecimal("0.70"),
                new BigDecimal("0.20"), new BigDecimal("0.39"));
        return new RecipeRecommendationService(profile, new RecipeScaler());
    }

    private static RecommendationContext tasteOnlyContext(List<InventoryItem> inventory) {
        return new RecommendationContext(
                RecommendationRequest.forServings(2), inventory,
                new TastePreferenceProfile(Map.of(SAVORY.getId(), BigDecimal.ONE)),
                List.of(), RecommendationConstraints.none());
    }
}
