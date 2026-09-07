package de.mealdeal.service.recommendation;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.InventoryItem;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredientGroup;
import de.mealdeal.domain.RecipeIngredientOption;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static de.mealdeal.service.recommendation.RecommendationTestFixtures.group;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.ingredient;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.option;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.recipe;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.stock;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.taste;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PantryOptimizerOracleTest {

    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;
    private static final BigDecimal COVERAGE_WEIGHT = new BigDecimal("0.35");
    private static final BigDecimal MISSING_WEIGHT = new BigDecimal("0.15");
    private static final Taste SAVORY = taste("Oracle-Herzhaft");

    @Test
    void branchAndBoundMatchesExhaustiveOracleForSmallGeneratedProblems() {
        for (int scenario = 0; scenario < 18; scenario++) {
            Ingredient first = ingredient("Oracle A " + scenario);
            Ingredient second = ingredient("Oracle B " + scenario);
            List<RecipeIngredientGroup> groups = new ArrayList<>();
            for (int groupIndex = 0; groupIndex < 3; groupIndex++) {
                int firstAmount = 1 + Math.floorMod(scenario + groupIndex, 3);
                int secondAmount = 1 + Math.floorMod(scenario * 2 + groupIndex, 3);
                groups.add(group("oracle-" + scenario + "-" + groupIndex, List.of(
                        option("oracle-a-" + scenario + "-" + groupIndex,
                                first, Integer.toString(firstAmount), Unit.GRAM, 0),
                        option("oracle-b-" + scenario + "-" + groupIndex,
                                second, Integer.toString(secondAmount), Unit.GRAM, 1)), 0));
            }
            Recipe candidate = recipe("oracle-" + scenario, "Oracle " + scenario,
                    groups, SAVORY);
            List<InventoryItem> inventory = List.of(
                    stock("oracle-a-" + scenario, first,
                            Integer.toString(Math.floorMod(scenario, 6)), Unit.GRAM),
                    stock("oracle-b-" + scenario, second,
                            Integer.toString(Math.floorMod(scenario * 3, 6)), Unit.GRAM));

            RecipeRecommendation result = new RecipeRecommendationService()
                    .recommend(List.of(candidate), RecommendationContext.pantryOnly(2, inventory))
                    .recommendations().getFirst();
            BigDecimal actualObjective = pantryObjective(result, groups.size());
            BigDecimal oracleObjective = exhaustiveObjective(groups, inventory);

            assertTrue(oracleObjective.subtract(actualObjective).abs()
                            .compareTo(new BigDecimal("1E-30")) < 0,
                    "Scenario " + scenario + " must match exhaustive enumeration: oracle="
                            + oracleObjective + ", actual=" + actualObjective);
        }
    }

    @Test
    void realisticLargerAlternativeRecipeCompletesWithinGuardrail() {
        assertTimeout(Duration.ofSeconds(5), () -> {
            List<Ingredient> ingredients = java.util.stream.IntStream.range(0, 6)
                    .mapToObj(index -> ingredient("Performance " + index))
                    .toList();
            List<RecipeIngredientGroup> groups = new ArrayList<>();
            for (int groupIndex = 0; groupIndex < 10; groupIndex++) {
                List<RecipeIngredientOption> options = new ArrayList<>();
                for (int optionIndex = 0; optionIndex < 3; optionIndex++) {
                    Ingredient ingredient = ingredients.get((groupIndex + optionIndex) % 6);
                    options.add(option("performance-" + groupIndex + "-" + optionIndex,
                            ingredient, Integer.toString(80 + optionIndex * 20),
                            Unit.GRAM, optionIndex));
                }
                groups.add(group("performance-" + groupIndex, options, 0));
            }
            List<InventoryItem> inventory = new ArrayList<>();
            for (int index = 0; index < ingredients.size(); index++) {
                inventory.add(stock("performance-" + index, ingredients.get(index),
                        Integer.toString(120 + index * 15), Unit.GRAM));
            }
            Recipe candidate = recipe("performance", "Realistische Größe", groups, SAVORY);

            RecipeRecommendation result = new RecipeRecommendationService()
                    .recommend(List.of(candidate),
                            RecommendationContext.pantryOnly(4, inventory))
                    .recommendations().getFirst();

            assertEquals(groups.size(), result.suggestedIngredientOptions().size());
        });
    }

    private static BigDecimal pantryObjective(
            RecipeRecommendation result, int groupCount) {
        BigDecimal coverage = result.signals()
                .valueOf(RecommendationSignal.PANTRY_COVERAGE).orElseThrow();
        BigDecimal coveredShare = BigDecimal.valueOf(
                        groupCount - result.missingIngredientGroupCount())
                .divide(BigDecimal.valueOf(groupCount), CALCULATION_CONTEXT);
        return COVERAGE_WEIGHT.multiply(coverage)
                .add(MISSING_WEIGHT.multiply(coveredShare));
    }

    private static BigDecimal exhaustiveObjective(
            List<RecipeIngredientGroup> groups, List<InventoryItem> inventory) {
        Map<UUID, Integer> stockByIngredient = new HashMap<>();
        for (InventoryItem item : inventory) {
            stockByIngredient.merge(item.getIngredient().getId(),
                    item.getQuantity().intValueExact(), Integer::sum);
        }
        OracleSearch search = new OracleSearch(groups, stockByIngredient);
        search.selectOptions(0, new ArrayList<>());
        return search.best.divide(BigDecimal.valueOf(groups.size()), CALCULATION_CONTEXT);
    }

    private static final class OracleSearch {

        private final List<RecipeIngredientGroup> groups;
        private final Map<UUID, Integer> stockByIngredient;
        private BigDecimal best = BigDecimal.ZERO;

        private OracleSearch(
                List<RecipeIngredientGroup> groups, Map<UUID, Integer> stockByIngredient) {
            this.groups = groups;
            this.stockByIngredient = stockByIngredient;
        }

        private void selectOptions(int index, List<RecipeIngredientOption> selected) {
            if (index == groups.size()) {
                allocate(0, selected, new HashMap<>(stockByIngredient),
                        BigDecimal.ZERO, 0);
                return;
            }
            for (RecipeIngredientOption option : groups.get(index).getOptions()) {
                selected.add(option);
                selectOptions(index + 1, selected);
                selected.removeLast();
            }
        }

        private void allocate(
                int index,
                List<RecipeIngredientOption> selected,
                Map<UUID, Integer> remaining,
                BigDecimal coverageSum,
                int fullyCovered) {
            if (index == selected.size()) {
                BigDecimal objective = COVERAGE_WEIGHT.multiply(coverageSum)
                        .add(MISSING_WEIGHT.multiply(BigDecimal.valueOf(fullyCovered)));
                best = best.max(objective);
                return;
            }
            RecipeIngredientOption option = selected.get(index);
            UUID ingredientId = option.getIngredient().getId();
            int available = remaining.getOrDefault(ingredientId, 0);
            int required = option.getQuantity().intValueExact();
            for (int used = 0; used <= Math.min(available, required); used++) {
                remaining.put(ingredientId, available - used);
                BigDecimal coverage = BigDecimal.valueOf(used)
                        .divide(BigDecimal.valueOf(required), CALCULATION_CONTEXT);
                allocate(index + 1, selected, remaining,
                        coverageSum.add(coverage),
                        fullyCovered + (used == required ? 1 : 0));
            }
            remaining.put(ingredientId, available);
        }
    }
}
