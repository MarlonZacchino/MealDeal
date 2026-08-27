package de.mealdeal.service;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.RecipeStep;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeSearchServiceTest {

    private final RecipeSearchService service = new RecipeSearchService();
    private Ingredient pasta;
    private Ingredient tomato;
    private Ingredient beef;
    private Taste savory;
    private Taste spicy;
    private Taste hearty;
    private Taste sweet;

    @BeforeEach
    void setUp() {
        pasta = new Ingredient("Pasta");
        tomato = new Ingredient("Tomato");
        beef = new Ingredient("Beef");
        savory = new Taste("Savory");
        spicy = new Taste("Spicy");
        hearty = new Taste("Hearty");
        sweet = new Taste("Sweet");
    }

    @Test
    void returnsPerfectSingleIngredientMatchAndExcludesZeroMatch() {
        Recipe matching = recipe("Matching", List.of(pasta), List.of(savory));
        Recipe notMatching = recipe("Not matching", List.of(tomato), List.of(savory));

        List<IngredientSearchResult> results =
                service.searchByIngredients(List.of(notMatching, matching), List.of(pasta));

        assertEquals(1, results.size());
        assertEquals(matching, results.getFirst().getRecipe());
        assertEquals(MatchQuality.PERFECT, results.getFirst().getMatchQuality());
    }

    @Test
    void ranksThreeTwoAndOneIngredientMatchesAndReportsDetails() {
        Recipe all = recipe("All", List.of(pasta, tomato, beef), List.of(savory));
        Recipe two = recipe("Two", List.of(pasta, tomato), List.of(savory));
        Recipe one = recipe("One", List.of(pasta), List.of(savory));
        Recipe none = recipe("None", List.of(new Ingredient("Rice")), List.of(savory));

        List<IngredientSearchResult> results = service.searchByIngredients(
                List.of(one, none, two, all), List.of(pasta, tomato, beef));

        assertEquals(List.of(all, two, one), results.stream()
                .map(IngredientSearchResult::getRecipe).toList());
        assertEquals(List.of(pasta, tomato), results.get(1).getMatchedIngredients());
        assertEquals(List.of(beef), results.get(1).getMissingIngredients());
        assertEquals(2, results.get(1).getMatchedCount());
        assertEquals(3, results.get(1).getSelectedCount());
        assertEquals(new BigDecimal("2").divide(new BigDecimal("3"), MathContext.DECIMAL128),
                results.get(1).getMatchRatio());
        assertEquals(MatchQuality.GOOD, results.get(1).getMatchQuality());
        assertEquals(MatchQuality.PARTIAL, results.get(2).getMatchQuality());
        assertThrows(UnsupportedOperationException.class,
                () -> results.get(1).getMissingIngredients().clear());
    }

    @Test
    void sortsEqualIngredientMatchesAlphabetically() {
        Recipe zulu = recipe("Zulu", List.of(pasta), List.of(savory));
        Recipe alpha = recipe("Alpha", List.of(pasta), List.of(savory));

        List<IngredientSearchResult> results =
                service.searchByIngredients(List.of(zulu, alpha), List.of(pasta, tomato));

        assertEquals(List.of("Alpha", "Zulu"), results.stream()
                .map(result -> result.getRecipe().getName()).toList());
    }

    @Test
    void acceptsTenIngredientsAndRejectsInvalidSelections() {
        List<Ingredient> tenIngredients = new ArrayList<>();
        for (int index = 1; index <= 10; index++) {
            tenIngredients.add(new Ingredient("Ingredient " + index));
        }

        assertTrue(service.searchByIngredients(List.of(), tenIngredients).isEmpty());

        List<Ingredient> elevenIngredients = new ArrayList<>(tenIngredients);
        elevenIngredients.add(new Ingredient("Ingredient 11"));
        assertThrows(IllegalArgumentException.class,
                () -> service.searchByIngredients(List.of(), elevenIngredients));
        assertThrows(IllegalArgumentException.class,
                () -> service.searchByIngredients(List.of(), List.of()));
        assertThrows(NullPointerException.class,
                () -> service.searchByIngredients(List.of(), null));
    }

    @Test
    void rejectsDuplicateIngredientIdentities() {
        Ingredient sameIdentity = new Ingredient(pasta.getId(), "Renamed pasta");

        assertThrows(IllegalArgumentException.class,
                () -> service.searchByIngredients(List.of(), List.of(pasta, sameIdentity)));
    }

    @Test
    void tasteAndRequiresEverySelectedTaste() {
        List<Recipe> recipes = tasteFilterRecipes();

        List<TasteSearchResult> results = service.searchByTastes(
                recipes, List.of(savory, spicy), TasteFilterMode.AND);

        assertEquals(List.of("A"), recipeNames(results));
        assertEquals(MatchQuality.PERFECT, results.getFirst().getMatchQuality());
    }

    @Test
    void tasteOrRequiresAtLeastOneSelectedTaste() {
        List<TasteSearchResult> results = service.searchByTastes(
                tasteFilterRecipes(), List.of(savory, spicy), TasteFilterMode.OR);

        assertEquals(List.of("A", "B", "C"), recipeNames(results));
    }

    @Test
    void ranksTasteMatchesAndReportsMissingTastes() {
        Recipe all = recipe("A", List.of(pasta), List.of(savory, hearty, spicy));
        Recipe two = recipe("B", List.of(pasta), List.of(savory, hearty));
        Recipe one = recipe("C", List.of(pasta), List.of(spicy));
        Recipe none = recipe("D", List.of(pasta), List.of(sweet));

        List<TasteSearchResult> results = service.searchByTastes(
                List.of(one, none, two, all), List.of(savory, hearty, spicy),
                TasteFilterMode.RANKING);

        assertEquals(List.of("A", "B", "C"), recipeNames(results));
        assertEquals(List.of(savory, hearty), results.get(1).getMatchedTastes());
        assertEquals(List.of(spicy), results.get(1).getMissingTastes());
        assertEquals(MatchQuality.GOOD, results.get(1).getMatchQuality());
        assertThrows(UnsupportedOperationException.class,
                () -> results.get(1).getMatchedTastes().clear());
    }

    @Test
    void rejectsInvalidTasteCriteriaAndNullMode() {
        Taste sameIdentity = new Taste(savory.getId(), "Renamed savory");

        assertThrows(IllegalArgumentException.class,
                () -> service.searchByTastes(List.of(), List.of(), TasteFilterMode.OR));
        assertThrows(IllegalArgumentException.class,
                () -> service.searchByTastes(
                        List.of(), List.of(savory, sameIdentity), TasteFilterMode.OR));
        assertThrows(NullPointerException.class,
                () -> service.searchByTastes(List.of(), null, TasteFilterMode.OR));
        assertThrows(NullPointerException.class,
                () -> service.searchByTastes(List.of(), List.of(savory), null));
    }

    @Test
    void validatesRecipeCollectionAndAllowsEmptyCollection() {
        assertTrue(service.searchByIngredients(List.of(), List.of(pasta)).isEmpty());
        assertThrows(NullPointerException.class,
                () -> service.searchByIngredients(null, List.of(pasta)));

        List<Recipe> recipesWithNull = new ArrayList<>();
        recipesWithNull.add(null);
        assertThrows(IllegalArgumentException.class,
                () -> service.searchByIngredients(recipesWithNull, List.of(pasta)));
    }

    private List<Recipe> tasteFilterRecipes() {
        return List.of(
                recipe("A", List.of(pasta), List.of(savory, spicy)),
                recipe("B", List.of(pasta), List.of(savory)),
                recipe("C", List.of(pasta), List.of(spicy)),
                recipe("D", List.of(pasta), List.of(sweet))
        );
    }

    private Recipe recipe(String name, List<Ingredient> ingredients, List<Taste> tastes) {
        List<RecipeIngredient> recipeIngredients = ingredients.stream()
                .map(ingredient -> new RecipeIngredient(ingredient, BigDecimal.ONE, Unit.PIECE))
                .toList();
        return new Recipe(UUID.randomUUID(), name, 2, recipeIngredients,
                List.of(new RecipeStep(1, "Cook.")), tastes);
    }

    private static List<String> recipeNames(List<TasteSearchResult> results) {
        return results.stream().map(result -> result.getRecipe().getName()).toList();
    }
}
