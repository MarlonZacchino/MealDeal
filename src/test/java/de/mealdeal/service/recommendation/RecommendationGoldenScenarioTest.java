package de.mealdeal.service.recommendation;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.InventoryItem;
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

    @Test
    void partiallyAvailableAlternativeProducesProportionalCoverage() {
        Ingredient standard = ingredient("Golden Standard");
        Ingredient alternative = ingredient("Golden Teilalternative");
        RecipeIngredientOption standardOption = option(
                "golden-partial-standard", standard, "100", Unit.GRAM, 0);
        RecipeIngredientOption alternativeOption = option(
                "golden-partial-alternative", alternative, "100", Unit.GRAM, 1);
        RecipeIngredientGroup group = group("golden-partial",
                List.of(standardOption, alternativeOption), 0);
        Recipe candidate = recipe("golden-partial", "Teilalternative",
                List.of(group), SAVORY);

        RecipeRecommendation result = service.recommend(List.of(candidate),
                        RecommendationContext.pantryOnly(2, List.of(
                                stock("golden-partial", alternative, "50", Unit.GRAM))))
                .recommendations().getFirst();

        assertEquals(0, new BigDecimal("0.5").compareTo(result.signals()
                .valueOf(RecommendationSignal.PANTRY_COVERAGE).orElseThrow()));
        assertEquals(alternativeOption.getId(),
                result.suggestedIngredientOptions().get(group.getId()));
        assertTrue(result.reasonCodes().contains(
                RecommendationReasonCode.ALTERNATIVE_IMPROVES_COVERAGE));
    }

    @Test
    void bestOfThreeOptionsIsSelectedOnce() {
        Ingredient standard = ingredient("Golden Drei Standard");
        Ingredient partial = ingredient("Golden Drei Teil");
        Ingredient full = ingredient("Golden Drei Voll");
        RecipeIngredientOption standardOption = option(
                "golden-three-standard", standard, "100", Unit.GRAM, 0);
        RecipeIngredientOption partialOption = option(
                "golden-three-partial", partial, "100", Unit.GRAM, 1);
        RecipeIngredientOption fullOption = option(
                "golden-three-full", full, "100", Unit.GRAM, 2);
        RecipeIngredientGroup group = group("golden-three",
                List.of(standardOption, partialOption, fullOption), 0);
        Recipe candidate = recipe("golden-three", "Drei Optionen", List.of(group), SAVORY);

        RecipeRecommendation result = service.recommend(List.of(candidate),
                        RecommendationContext.pantryOnly(2, List.of(
                                stock("golden-three-standard", standard, "10", Unit.GRAM),
                                stock("golden-three-partial", partial, "50", Unit.GRAM),
                                stock("golden-three-full", full, "100", Unit.GRAM))))
                .recommendations().getFirst();

        assertEquals(fullOption.getId(),
                result.suggestedIngredientOptions().get(group.getId()));
        assertEquals(0, BigDecimal.ONE.compareTo(result.signals()
                .valueOf(RecommendationSignal.PANTRY_COVERAGE).orElseThrow()));
    }

    @Test
    void missingGroupShareComparisonUsesEqualGroupCounts() {
        Ingredient a = ingredient("Golden Anteil A");
        Ingredient b = ingredient("Golden Anteil B");
        Ingredient c = ingredient("Golden Anteil C");
        Ingredient d = ingredient("Golden Anteil D");
        Ingredient e = ingredient("Golden Anteil E");
        Ingredient f = ingredient("Golden Anteil F");
        Recipe oneMissing = recipe("golden-one-missing", "Eine fehlt", List.of(
                singleGroup("golden-a", a), singleGroup("golden-b", b),
                singleGroup("golden-c", c)), SAVORY);
        Recipe twoMissing = recipe("golden-two-missing", "Zwei fehlen", List.of(
                singleGroup("golden-d", d), singleGroup("golden-e", e),
                singleGroup("golden-f", f)), SAVORY);
        List<InventoryItem> inventory = List.of(
                stock("golden-a", a, "100", Unit.GRAM),
                stock("golden-b", b, "100", Unit.GRAM),
                stock("golden-d", d, "100", Unit.GRAM));

        List<Recipe> ranking = service.recommend(List.of(twoMissing, oneMissing),
                        RecommendationContext.pantryOnly(2, inventory))
                .recommendations().stream().map(RecipeRecommendation::recipe).toList();

        assertEquals(List.of(oneMissing, twoMissing), ranking);
    }

    private static Recipe singleIngredientRecipe(String key, Ingredient ingredient) {
        return recipe(key, key, List.of(singleGroup(key, ingredient)), SAVORY);
    }

    private static RecipeIngredientGroup singleGroup(String key, Ingredient ingredient) {
        RecipeIngredientOption option = option(key, ingredient, "100", Unit.GRAM, 0);
        return group(key, List.of(option), 0);
    }
}
