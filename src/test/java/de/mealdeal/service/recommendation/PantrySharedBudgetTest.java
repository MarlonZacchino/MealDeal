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
import java.util.List;

import static de.mealdeal.service.recommendation.RecommendationTestFixtures.group;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.ingredient;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.option;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.recipe;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.stock;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.taste;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PantrySharedBudgetTest {

    private static final Taste SAVORY = taste("Budget-Herzhaft");
    private final RecipeRecommendationService service = new RecipeRecommendationService();

    @Test
    void oneHundredGramsCannotFullyCoverTwoHundredGramGroups() {
        Ingredient ingredient = ingredient("Geteiltes X");
        Recipe candidate = recipe("shared-100", "Geteilt", List.of(
                singleGroup("shared-a", ingredient, "100"),
                singleGroup("shared-b", ingredient, "100")), SAVORY);

        RecipeRecommendation result = recommend(candidate,
                List.of(stock("shared-100", ingredient, "100", Unit.GRAM)));

        assertSignalEquals(new BigDecimal("0.5"), result,
                RecommendationSignal.PANTRY_COVERAGE);
        assertEquals(1, result.missingIngredientGroupCount());
        assertFalse(result.reasonCodes().contains(
                RecommendationReasonCode.PANTRY_FULL_COVERAGE));
    }

    @Test
    void twoHundredGramsFullyCoverTwoHundredGramGroups() {
        Ingredient ingredient = ingredient("Ausreichendes X");
        Recipe candidate = recipe("shared-200", "Ausreichend", List.of(
                singleGroup("enough-a", ingredient, "100"),
                singleGroup("enough-b", ingredient, "100")), SAVORY);

        RecipeRecommendation result = recommend(candidate,
                List.of(stock("shared-200", ingredient, "200", Unit.GRAM)));

        assertSignalEquals(BigDecimal.ONE, result, RecommendationSignal.PANTRY_COVERAGE);
        assertEquals(0, result.missingIngredientGroupCount());
    }

    @Test
    void competingGroupsCannotReuseTheSameAlternativeStock() {
        Ingredient absentA = ingredient("Fehlender Standard A");
        Ingredient absentB = ingredient("Fehlender Standard B");
        Ingredient shared = ingredient("Gemeinsame Alternative");
        Recipe candidate = recipe("alternative-conflict", "Konflikt", List.of(
                alternativeGroup("conflict-a", absentA, shared),
                alternativeGroup("conflict-b", absentB, shared)), SAVORY);

        RecipeRecommendation result = recommend(candidate,
                List.of(stock("alternative-conflict", shared, "100", Unit.GRAM)));

        assertSignalEquals(new BigDecimal("0.5"), result,
                RecommendationSignal.PANTRY_COVERAGE);
        assertEquals(1, result.missingIngredientGroupCount());
    }

    @Test
    void alternativeCanResolveCompetitionAndFullyCoverBothGroups() {
        Ingredient x = ingredient("Konflikt X");
        Ingredient y = ingredient("Ausweichendes Y");
        RecipeIngredientGroup flexible = alternativeGroup("resolvable-flex", x, y);
        RecipeIngredientGroup fixed = singleGroup("resolvable-fixed", x, "100");
        Recipe candidate = recipe("resolvable", "Auflösbar",
                List.of(flexible, fixed), SAVORY);

        RecipeRecommendation result = recommend(candidate, List.of(
                stock("resolvable-x", x, "100", Unit.GRAM),
                stock("resolvable-y", y, "100", Unit.GRAM)));

        assertSignalEquals(BigDecimal.ONE, result, RecommendationSignal.PANTRY_COVERAGE);
        assertEquals(0, result.missingIngredientGroupCount());
        assertEquals(flexible.getOptions().get(1).getId(),
                result.suggestedIngredientOptions().get(flexible.getId()));
        assertTrue(result.reasonCodes().contains(
                RecommendationReasonCode.ALTERNATIVE_AVAILABLE));
        assertTrue(result.reasonCodes().contains(
                RecommendationReasonCode.ALTERNATIVE_IMPROVES_COVERAGE));
    }

    @Test
    void groupOptionAndInventoryInputOrderDoNotChangeTheOptimum() {
        Ingredient x = ingredient("Order X");
        Ingredient y = ingredient("Order Y");
        RecipeIngredientOption xOption = option("order-x", x, "100", Unit.GRAM, 0);
        RecipeIngredientOption yOption = option("order-y", y, "100", Unit.GRAM, 1);
        RecipeIngredientGroup flexibleForward = group(
                "order-flex", List.of(xOption, yOption), 0);
        RecipeIngredientGroup flexibleReverse = new RecipeIngredientGroup(
                flexibleForward.getId(), List.of(yOption, xOption), xOption.getId());
        RecipeIngredientGroup fixed = singleGroup("order-fixed", x, "100");
        InventoryItem xStock = stock("order-x", x, "100", Unit.GRAM);
        InventoryItem yStock = stock("order-y", y, "100", Unit.GRAM);
        Recipe forwardRecipe = recipe("order-forward", "Order",
                List.of(flexibleForward, fixed), SAVORY);
        Recipe reverseRecipe = recipe("order-reverse", "Order",
                List.of(fixed, flexibleReverse), SAVORY);

        RecipeRecommendation forward = recommend(
                forwardRecipe, List.of(xStock, yStock));
        RecipeRecommendation reverse = recommend(
                reverseRecipe, List.of(yStock, xStock));

        assertEquals(0, forward.score().compareTo(reverse.score()));
        assertEquals(forward.missingIngredientGroupCount(),
                reverse.missingIngredientGroupCount());
        assertEquals(forward.suggestedIngredientOptions(),
                reverse.suggestedIngredientOptions());
    }

    @Test
    void quantityImmediatelyBelowRequirementIsNeverMarkedComplete() {
        Ingredient ingredient = ingredient("Präzisionsmenge");
        Recipe candidate = recipe("precision", "Präzision",
                List.of(singleGroup("precision", ingredient,
                        "1.00000000000000000000000000000000001")), SAVORY);

        RecipeRecommendation result = recommend(candidate, List.of(stock(
                "precision", ingredient, "1", Unit.GRAM)));

        assertEquals(1, result.missingIngredientGroupCount());
        assertFalse(result.reasonCodes().contains(
                RecommendationReasonCode.PANTRY_FULL_COVERAGE));
    }

    @Test
    void addingInventoryCannotLowerScoreThroughAlternativeSemantics() {
        Ingredient standard = ingredient("Monoton Standard");
        Ingredient alternative = ingredient("Monoton Alternative");
        Recipe candidate = recipe("alternative-monotonic", "Monoton",
                List.of(alternativeGroup("alternative-monotonic", standard, alternative)),
                SAVORY);

        RecipeRecommendation less = recommend(candidate,
                List.of(stock("monotonic-less", alternative, "50", Unit.GRAM)));
        RecipeRecommendation more = recommend(candidate,
                List.of(stock("monotonic-more", alternative, "100", Unit.GRAM)));

        assertTrue(more.score().compareTo(less.score()) >= 0);
    }

    @Test
    void unusedAlternativeCannotLowerScore() {
        Ingredient standard = ingredient("Unbenutzt Standard");
        Ingredient unused = ingredient("Unbenutzt Alternative");
        RecipeIngredientOption standardOption = option(
                "unused-standard", standard, "100", Unit.GRAM, 0);
        Recipe withoutAlternative = recipe("unused-before", "Ohne Alternative", List.of(
                group("unused-group", List.of(standardOption), 0)), SAVORY);
        Recipe withAlternative = recipe("unused-after", "Mit Alternative", List.of(
                group("unused-group", List.of(
                        standardOption,
                        option("unused-extra", unused, "100", Unit.GRAM, 1)), 0)), SAVORY);
        List<InventoryItem> inventory = List.of(
                stock("unused-stock", standard, "100", Unit.GRAM));

        RecipeRecommendation before = recommend(withoutAlternative, inventory);
        RecipeRecommendation after = recommend(withAlternative, inventory);

        assertEquals(0, before.score().compareTo(after.score()));
        assertFalse(after.reasonCodes().contains(
                RecommendationReasonCode.ALTERNATIVE_AVAILABLE));
    }

    @Test
    void severalAvailableAlternativesDoNotCreateRepeatedBonus() {
        Ingredient standard = ingredient("Dreifach Standard");
        Ingredient firstAlternative = ingredient("Dreifach A");
        Ingredient secondAlternative = ingredient("Dreifach B");
        RecipeIngredientOption standardOption = option(
                "triple-standard", standard, "100", Unit.GRAM, 0);
        Recipe candidate = recipe("triple", "Dreifach", List.of(group("triple", List.of(
                standardOption,
                option("triple-a", firstAlternative, "100", Unit.GRAM, 1),
                option("triple-b", secondAlternative, "100", Unit.GRAM, 2)), 0)), SAVORY);

        RecipeRecommendation result = recommend(candidate, List.of(
                stock("triple-standard", standard, "100", Unit.GRAM),
                stock("triple-a", firstAlternative, "100", Unit.GRAM),
                stock("triple-b", secondAlternative, "100", Unit.GRAM)));

        assertEquals(standardOption.getId(),
                result.suggestedIngredientOptions().values().iterator().next());
        assertTrue(result.signals().valueOf(
                RecommendationSignal.INGREDIENT_ALTERNATIVE_FIT).isEmpty());
        assertEquals(0, service.scoringProfile().weightOf(
                RecommendationSignal.INGREDIENT_ALTERNATIVE_FIT).compareTo(BigDecimal.ZERO));
    }

    @Test
    void equalNonStandardOptionsUsePositionAfterExcludedDefault() {
        Ingredient excluded = ingredient("Tie Ausgeschlossen");
        Ingredient later = ingredient("Tie Später");
        Ingredient earlier = ingredient("Tie Früher");
        RecipeIngredientOption defaultOption = option(
                "tie-default", excluded, "100", Unit.GRAM, 0);
        RecipeIngredientOption laterOption = option(
                "tie-later", later, "100", Unit.GRAM, 2);
        RecipeIngredientOption earlierOption = option(
                "tie-earlier", earlier, "100", Unit.GRAM, 1);
        RecipeIngredientGroup group = group("tie-options",
                List.of(defaultOption, laterOption, earlierOption), 0);
        Recipe candidate = recipe("tie-options", "Options-Tie", List.of(group), SAVORY);
        RecommendationContext context = new RecommendationContext(
                RecommendationRequest.forServings(2), List.of(
                        stock("tie-later", later, "100", Unit.GRAM),
                        stock("tie-earlier", earlier, "100", Unit.GRAM)),
                TastePreferenceProfile.empty(), List.of(),
                new RecommendationConstraints(
                        java.util.Set.of(), java.util.Set.of(excluded.getId())));

        RecipeRecommendation result = service.recommend(List.of(candidate), context)
                .recommendations().getFirst();

        assertEquals(earlierOption.getId(),
                result.suggestedIngredientOptions().get(group.getId()));
    }

    private RecipeRecommendation recommend(Recipe recipe, List<InventoryItem> inventory) {
        return service.recommend(List.of(recipe), RecommendationContext.pantryOnly(2, inventory))
                .recommendations().getFirst();
    }

    private static RecipeIngredientGroup singleGroup(
            String key, Ingredient ingredient, String quantity) {
        RecipeIngredientOption only = option(key, ingredient, quantity, Unit.GRAM, 0);
        return group(key, List.of(only), 0);
    }

    private static RecipeIngredientGroup alternativeGroup(
            String key, Ingredient standard, Ingredient alternative) {
        return group(key, List.of(
                option(key + "-standard", standard, "100", Unit.GRAM, 0),
                option(key + "-alternative", alternative, "100", Unit.GRAM, 1)), 0);
    }

    private static void assertSignalEquals(
            BigDecimal expected,
            RecipeRecommendation recommendation,
            RecommendationSignal signal) {
        assertEquals(0, expected.compareTo(
                recommendation.signals().valueOf(signal).orElseThrow()));
    }
}
