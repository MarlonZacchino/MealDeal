package de.mealdeal.service.recommendation;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredientGroup;
import de.mealdeal.domain.RecipeIngredientOption;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static de.mealdeal.service.recommendation.RecommendationTestFixtures.group;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.ingredient;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.option;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.recipe;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.stock;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.taste;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationGoldenScenarioTest {

    private static final Taste SAVORY = taste("Herzhaft");
    private final RecipeRecommendationService service = new RecipeRecommendationService();

    @ParameterizedTest(name = "{0}")
    @MethodSource("pantryBoundaries")
    void pantryGoldenScenariosUseExpectedBoundaryAndReason(
            String name,
            String stockAmount,
            String expectedCoverage,
            RecommendationReasonCode expectedReason) {
        Ingredient rice = ingredient("Reis-" + name);
        Recipe candidate = singleIngredientRecipe(name, rice);

        RecipeRecommendation result = service.recommend(List.of(candidate),
                RecommendationContext.pantryOnly(2,
                        List.of(stock(name, rice, stockAmount, Unit.GRAM))))
                .recommendations().getFirst();

        assertEquals(0, new BigDecimal(expectedCoverage).compareTo(result.signals()
                .valueOf(RecommendationSignal.PANTRY_COVERAGE).orElseThrow()));
        assertTrue(result.reasonCodes().contains(expectedReason));
    }

    static Stream<Arguments> pantryBoundaries() {
        return Stream.of(
                Arguments.of("full", "100", "1", RecommendationReasonCode.PANTRY_FULL_COVERAGE),
                Arguments.of("mostly-boundary", "75", "0.75",
                        RecommendationReasonCode.PANTRY_MOSTLY_COVERED),
                Arguments.of("below-mostly", "74", "0.74",
                        RecommendationReasonCode.PANTRY_LOW_COVERAGE),
                Arguments.of("empty", "0", "0", RecommendationReasonCode.PANTRY_LOW_COVERAGE));
    }

    @Test
    void fullPantryGoldenScenarioRanksAheadOfOtherwiseEqualPartialCoverage() {
        Ingredient fullIngredient = ingredient("Vollständig");
        Ingredient partialIngredient = ingredient("Teilweise");
        Recipe full = singleIngredientRecipe("full-ranking", fullIngredient);
        Recipe partial = singleIngredientRecipe("partial-ranking", partialIngredient);
        RecommendationContext context = RecommendationContext.pantryOnly(2, List.of(
                stock("full-ranking", fullIngredient, "100", Unit.GRAM),
                stock("partial-ranking", partialIngredient, "50", Unit.GRAM)));

        List<Recipe> ranking = service.recommend(List.of(partial, full), context)
                .recommendations().stream().map(RecipeRecommendation::recipe).toList();

        assertEquals(List.of(full, partial), ranking);
    }

    private static Recipe singleIngredientRecipe(String key, Ingredient ingredient) {
        RecipeIngredientOption option = option(key, ingredient, "100", Unit.GRAM, 0);
        RecipeIngredientGroup group = group(key, List.of(option), 0);
        return recipe(key, key, List.of(group), SAVORY);
    }
}
