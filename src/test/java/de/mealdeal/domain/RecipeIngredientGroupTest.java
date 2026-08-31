package de.mealdeal.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeIngredientGroupTest {

    private final Ingredient flour = new Ingredient("Mehl");
    private final Ingredient almondFlour = new Ingredient("Mandelmehl");

    @Test
    void singleOptionGroupUsesItsOnlyOptionAsTheStandard() {
        RecipeIngredientOption option = option(flour, "250", Unit.GRAM, 0);

        RecipeIngredientGroup group = new RecipeIngredientGroup(List.of(option), option);

        assertEquals(option, group.getStandardOption());
        assertEquals(option.getId(), group.getStandardOptionId());
        assertEquals(List.of(option), group.getOptions());
        assertTrue(group.getId() != null);
    }

    @Test
    void groupAndOptionEqualityUseTheirStableUuidIdentities() {
        UUID optionId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        RecipeIngredientOption first = new RecipeIngredientOption(optionId, flour,
                new BigDecimal("250"), Unit.GRAM, 0);
        RecipeIngredientOption recreated = new RecipeIngredientOption(optionId, almondFlour,
                new BigDecimal("0.3"), Unit.KILOGRAM, 1);

        RecipeIngredientGroup group = new RecipeIngredientGroup(groupId, List.of(first), first);
        RecipeIngredientGroup recreatedGroup = new RecipeIngredientGroup(groupId,
                List.of(recreated), recreated);

        assertEquals(first, recreated);
        assertEquals(group, recreatedGroup);
        assertNotEquals(first, option(flour, "250", Unit.GRAM, 0));
    }

    @Test
    void multiOptionGroupOrdersAlternativesByPositionAndRetainsTheirOwnAmountsAndUnits() {
        RecipeIngredientOption first = option(flour, "250", Unit.GRAM, 0);
        RecipeIngredientOption second = option(almondFlour, "0.3", Unit.KILOGRAM, 1);

        RecipeIngredientGroup group = new RecipeIngredientGroup(List.of(second, first), second);

        assertEquals(List.of(first, second), group.getOptions());
        assertEquals(second, group.getStandardOption());
        assertEquals(new BigDecimal("250"), group.getOptions().get(0).getQuantity());
        assertEquals(Unit.GRAM, group.getOptions().get(0).getUnit());
        assertEquals(new BigDecimal("0.3"), group.getOptions().get(1).getQuantity());
        assertEquals(Unit.KILOGRAM, group.getOptions().get(1).getUnit());
    }

    @Test
    void rejectsEmptyGroupAndStandardOptionThatDoesNotBelongToIt() {
        RecipeIngredientOption option = option(flour, "250", Unit.GRAM, 0);
        RecipeIngredientOption outside = option(almondFlour, "2", Unit.PIECE, 0);

        assertThrows(IllegalArgumentException.class,
                () -> new RecipeIngredientGroup(List.of(), UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeIngredientGroup(List.of(option), outside));
        assertThrows(NullPointerException.class,
                () -> new RecipeIngredientGroup(List.of(option), (UUID) null));
    }

    @Test
    void rejectsInvalidOptionQuantitiesAndNonDeterministicPositions() {
        assertThrows(IllegalArgumentException.class,
                () -> option(flour, "0", Unit.GRAM, 0));
        assertThrows(IllegalArgumentException.class,
                () -> option(flour, "-1", Unit.GRAM, 0));
        assertThrows(IllegalArgumentException.class,
                () -> option(flour, "1", Unit.GRAM, -1));

        RecipeIngredientOption first = option(flour, "1", Unit.GRAM, 0);
        RecipeIngredientOption second = option(almondFlour, "2", Unit.PIECE, 0);
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeIngredientGroup(List.of(first, second), first));
    }

    @Test
    void recipeUsesOrderedGroupsAndProjectsOnlyTheirStandardOptionsForCompatibility() {
        RecipeIngredientOption flourOption = option(flour, "250", Unit.GRAM, 0);
        RecipeIngredientGroup flourGroup = new RecipeIngredientGroup(List.of(flourOption), flourOption);
        RecipeIngredientOption standardAlternative = option(almondFlour, "300", Unit.GRAM, 0);
        RecipeIngredientOption nonStandardAlternative = option(flour, "200", Unit.GRAM, 1);
        RecipeIngredientGroup alternativeGroup = new RecipeIngredientGroup(
                List.of(standardAlternative, nonStandardAlternative), standardAlternative);

        Recipe recipe = Recipe.withIngredientGroups("Kuchen", 4,
                List.of(alternativeGroup, flourGroup), List.of(), List.of(new Taste("Süß")),
                DishType.MAIN);

        assertEquals(List.of(alternativeGroup, flourGroup), recipe.getIngredientGroups());
        assertEquals(List.of(almondFlour, flour), recipe.getIngredients().stream()
                .map(RecipeIngredient::getIngredient).toList());
        assertEquals(List.of(new BigDecimal("300"), new BigDecimal("250")),
                recipe.getIngredients().stream().map(RecipeIngredient::getQuantity).toList());
        assertThrows(UnsupportedOperationException.class, () -> recipe.getIngredientGroups().clear());
    }

    @Test
    void legacyRecipeIngredientsBecomeSingleOptionGroupsWithoutASecondStoredList() {
        RecipeIngredient legacy = new RecipeIngredient(flour, new BigDecimal("250"), Unit.GRAM);

        Recipe recipe = new Recipe("Kuchen", List.of(legacy), List.of(),
                List.of(new Taste("Süß")));

        RecipeIngredientGroup group = recipe.getIngredientGroups().getFirst();
        assertEquals(1, group.getOptions().size());
        assertEquals(group.getStandardOption().getIngredient(), recipe.getIngredients()
                .getFirst().getIngredient());
        assertEquals(legacy.getQuantity(), group.getStandardOption().getQuantity());
    }

    private static RecipeIngredientOption option(Ingredient ingredient, String quantity, Unit unit,
                                                  int position) {
        return new RecipeIngredientOption(ingredient, new BigDecimal(quantity), unit, position);
    }
}
