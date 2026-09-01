package de.mealdeal.service;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.DishType;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.RecipeIngredientGroup;
import de.mealdeal.domain.RecipeIngredientOption;
import de.mealdeal.domain.RecipeStep;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecipeScalerTest {

    private final RecipeScaler scaler = new RecipeScaler();
    private Ingredient pasta;

    @BeforeEach
    void setUp() {
        pasta = new Ingredient("Pasta");
    }

    @Test
    void keepsAmountForStandardServingCount() {
        assertScaledAmount(recipe(2, "500"), 2, "500");
    }

    @Test
    void halvesAmountForHalfTheServings() {
        assertScaledAmount(recipe(2, "500"), 1, "250");
    }

    @Test
    void doublesAmountForTwiceTheServings() {
        assertScaledAmount(recipe(2, "500"), 4, "1000");
    }

    @Test
    void scalesSliceQuantityWithoutChangingItsUnit() {
        Recipe recipe = new Recipe("Toast", 2,
                List.of(new RecipeIngredient(pasta, new BigDecimal("3"), Unit.SLICE)),
                List.of(new RecipeStep(1, "Toast.")), List.of(new Taste("Savory")));

        RecipeIngredient scaled = scaler.scale(recipe, 4).getFirst();

        assertEquals(new BigDecimal("6"), scaled.getQuantity());
        assertEquals(Unit.SLICE, scaled.getUnit());
    }

    @Test
    void scalesClovesAndSprigsWithoutChangingTheirUnits() {
        Recipe recipe = new Recipe("Seasoning", 2, List.of(
                new RecipeIngredient(pasta, new BigDecimal("2"), Unit.CLOVE),
                new RecipeIngredient(new Ingredient("Thyme"), BigDecimal.ONE, Unit.SPRIG)),
                List.of(), List.of(new Taste("Savory")));

        List<RecipeIngredient> scaled = scaler.scale(recipe, 4);

        assertEquals(new BigDecimal("4"), scaled.get(0).getQuantity());
        assertEquals(Unit.CLOVE, scaled.get(0).getUnit());
        assertEquals(new BigDecimal("2"), scaled.get(1).getQuantity());
        assertEquals(Unit.SPRIG, scaled.get(1).getUnit());
    }

    @Test
    void scalesOnlyTheStandardOptionOfAnAlternativeGroup() {
        Ingredient rice = new Ingredient("Rice");
        RecipeIngredientOption pastaOption = new RecipeIngredientOption(
                pasta, new BigDecimal("500"), Unit.GRAM, 0);
        RecipeIngredientOption riceOption = new RecipeIngredientOption(
                rice, new BigDecimal("300"), Unit.GRAM, 1);
        RecipeIngredientGroup group = new RecipeIngredientGroup(
                List.of(pastaOption, riceOption), riceOption);
        Recipe recipe = Recipe.withIngredientGroups("Flexible", 2, List.of(group),
                List.of(), List.of(new Taste("Savory")), DishType.MAIN);

        RecipeIngredient scaled = scaler.scale(recipe, 4).getFirst();

        assertEquals(rice, scaled.getIngredient());
        assertEquals(new BigDecimal("600"), scaled.getQuantity());
    }

    @Test
    void scalesAllAlternativeOptionsAndKeepsGroupSemantics() {
        Ingredient rice = new Ingredient("Rice");
        RecipeIngredientOption pastaOption = new RecipeIngredientOption(
                pasta, new BigDecimal("500"), Unit.GRAM, 0);
        RecipeIngredientOption riceOption = new RecipeIngredientOption(
                rice, new BigDecimal("300"), Unit.GRAM, 1);
        RecipeIngredientGroup group = new RecipeIngredientGroup(
                List.of(pastaOption, riceOption), riceOption);
        Recipe recipe = Recipe.withIngredientGroups("Flexible", 2, List.of(group),
                List.of(), List.of(new Taste("Savory")), DishType.MAIN);

        RecipeIngredientGroup scaled = scaler.scaleIngredientGroups(recipe, 4).getFirst();

        assertEquals(group.getId(), scaled.getId());
        assertEquals(List.of(pastaOption.getId(), riceOption.getId()), scaled.getOptions().stream()
                .map(option -> option.getId()).toList());
        assertEquals(riceOption.getId(), scaled.getStandardOptionId());
        assertEquals(List.of(new BigDecimal("1000"), new BigDecimal("600")),
                scaled.getOptions().stream().map(option -> option.getQuantity()).toList());
        assertEquals(List.of(0, 1), scaled.getOptions().stream()
                .map(option -> option.getPosition()).toList());
    }

    @Test
    void scalesToOddServingCount() {
        assertScaledAmount(recipe(2, "500"), 5, "1250");
    }

    @Test
    void usesDecimal128ForRepeatingResults() {
        BigDecimal expected = new BigDecimal("100")
                .divide(new BigDecimal("3"), MathContext.DECIMAL128);

        BigDecimal actual = scaler.scale(recipe(3, "100"), 1).getFirst().getQuantity();

        assertEquals(expected, actual);
        assertEquals(34, actual.precision());
    }

    @Test
    void rejectsNonPositiveServingCounts() {
        Recipe recipe = recipe(2, "500");

        assertThrows(IllegalArgumentException.class, () -> scaler.scale(recipe, 0));
        assertThrows(IllegalArgumentException.class, () -> scaler.scale(recipe, -1));
    }

    @Test
    void leavesOriginalRecipeUnchanged() {
        Recipe recipe = recipe(2, "500");
        RecipeIngredient originalIngredient = recipe.getIngredients().getFirst();

        List<RecipeIngredient> scaledIngredients = scaler.scale(recipe, 4);

        assertEquals(new BigDecimal("500"), originalIngredient.getQuantity());
        assertEquals(2, recipe.getStandardServingCount());
        assertNotSame(originalIngredient, scaledIngredients.getFirst());
        assertEquals(pasta, scaledIngredients.getFirst().getIngredient());
        assertThrows(UnsupportedOperationException.class, scaledIngredients::clear);
    }

    @Test
    void rejectsNullRecipe() {
        assertThrows(NullPointerException.class, () -> scaler.scale(null, 2));
    }

    private Recipe recipe(int servings, String amount) {
        return new Recipe("Pasta", servings,
                List.of(new RecipeIngredient(pasta, new BigDecimal(amount), Unit.GRAM)),
                List.of(new RecipeStep(1, "Cook.")), List.of(new Taste("Savory")));
    }

    private void assertScaledAmount(
            Recipe recipe, int requestedServings, String expectedAmount) {
        BigDecimal actual = scaler.scale(recipe, requestedServings).getFirst().getQuantity();
        assertEquals(new BigDecimal(expectedAmount), actual);
    }
}
