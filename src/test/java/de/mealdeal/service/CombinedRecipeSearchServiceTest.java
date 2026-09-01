package de.mealdeal.service;

import de.mealdeal.domain.DishType;
import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.RecipeIngredientGroup;
import de.mealdeal.domain.RecipeIngredientOption;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombinedRecipeSearchServiceTest {

    private final RecipeSearchService recipeSearchService = new RecipeSearchService();
    private final CombinedRecipeSearchService service =
            new CombinedRecipeSearchService(recipeSearchService);
    private Ingredient pasta;
    private Ingredient tomato;
    private Ingredient cheese;
    private Ingredient rice;
    private Taste savory;
    private Taste spicy;
    private Taste fresh;

    @BeforeEach
    void setUp() {
        pasta = new Ingredient("Pasta");
        tomato = new Ingredient("Tomate");
        cheese = new Ingredient("Käse");
        rice = new Ingredient("Reis");
        savory = new Taste("Herzhaft");
        spicy = new Taste("Scharf");
        fresh = new Taste("Frisch");
    }

    @Test
    void ingredientOnlyKeepsExistingIngredientSearchBehavior() {
        Recipe perfect = recipe("Perfekt", List.of(pasta, tomato), savory);
        Recipe partial = recipe("Teilweise", List.of(pasta), savory);
        List<Recipe> recipes = List.of(partial, perfect);

        List<CombinedSearchResult> results = service.search(
                recipes, List.of(pasta, tomato), List.of(), TasteFilterMode.RANKING);

        assertEquals(recipeSearchService.searchByIngredients(
                        recipes, List.of(pasta, tomato)).stream()
                        .map(IngredientSearchResult::getRecipe).toList(),
                resultRecipes(results));
        assertTrue(results.stream().allMatch(result -> result.getTasteResult().isEmpty()));
    }

    @Test
    void tasteOnlyKeepsExistingTasteSearchBehavior() {
        Recipe alpha = recipe("Alpha", List.of(pasta), savory);
        Recipe zulu = recipe("Zulu", List.of(pasta), spicy);
        List<Recipe> recipes = List.of(zulu, alpha);

        List<CombinedSearchResult> results = service.search(
                recipes, List.of(), List.of(savory, spicy), TasteFilterMode.OR);

        assertEquals(recipeSearchService.searchByTastes(
                        recipes, List.of(savory, spicy), TasteFilterMode.OR).stream()
                        .map(TasteSearchResult::getRecipe).toList(),
                resultRecipes(results));
        assertTrue(results.stream().allMatch(result -> result.getIngredientResult().isEmpty()));
    }

    @Test
    void combinedAndRequiresIngredientMatchAndEveryTaste() {
        Recipe both = recipe("Beide", List.of(pasta), savory, spicy);
        Recipe ingredientsOnly = recipe("Nur Zutaten", List.of(pasta), savory);
        Recipe tastesOnly = recipe("Nur Geschmack", List.of(rice), savory, spicy);

        List<CombinedSearchResult> results = service.search(
                List.of(ingredientsOnly, tastesOnly, both), List.of(pasta),
                List.of(savory, spicy), TasteFilterMode.AND);

        assertEquals(List.of(both), resultRecipes(results));
    }

    @Test
    void combinedOrRequiresIngredientMatchAndAtLeastOneTaste() {
        Recipe first = recipe("Erster", List.of(pasta), savory);
        Recipe second = recipe("Zweiter", List.of(pasta), spicy);
        Recipe ingredientsOnly = recipe("Nur Zutaten", List.of(pasta), fresh);
        Recipe tastesOnly = recipe("Nur Geschmack", List.of(rice), savory);

        List<CombinedSearchResult> results = service.search(
                List.of(second, ingredientsOnly, tastesOnly, first), List.of(pasta),
                List.of(savory, spicy), TasteFilterMode.OR);

        assertEquals(List.of(first, second), resultRecipes(results));
    }

    @Test
    void combinedRankingUsesTasteRankingToBreakEqualIngredientMatches() {
        Recipe ingredientWinner = recipe(
                "Zutaten-Sieger", List.of(pasta, tomato, cheese), savory);
        Recipe tasteWinner = recipe(
                "Zulu", List.of(pasta, tomato, rice), savory, spicy);
        Recipe lowerTasteMatch = recipe(
                "Alpha", List.of(pasta, tomato, rice), savory);

        List<CombinedSearchResult> results = service.search(
                List.of(lowerTasteMatch, tasteWinner, ingredientWinner),
                List.of(pasta, tomato, cheese), List.of(savory, spicy),
                TasteFilterMode.RANKING);

        assertEquals(List.of(ingredientWinner, tasteWinner, lowerTasteMatch),
                resultRecipes(results));
        assertEquals(MatchQuality.GOOD,
                results.get(1).getIngredientResult().orElseThrow().getMatchQuality());
        assertEquals(MatchQuality.PERFECT,
                results.get(1).getTasteResult().orElseThrow().getMatchQuality());
    }

    @Test
    void combinedSearchMatchesAlternativeWithoutChangingTasteFiltering() {
        RecipeIngredientOption pastaOption = new RecipeIngredientOption(
                pasta, BigDecimal.ONE, Unit.PIECE, 0);
        RecipeIngredientOption tomatoOption = new RecipeIngredientOption(
                tomato, BigDecimal.ONE, Unit.PIECE, 1);
        Recipe flexible = Recipe.withIngredientGroups("Flexibel", 2,
                List.of(new RecipeIngredientGroup(
                        List.of(pastaOption, tomatoOption), pastaOption)),
                List.of(), List.of(savory, spicy), DishType.MAIN);
        RecipeIngredientOption wrongTasteOption = new RecipeIngredientOption(
                tomato, BigDecimal.ONE, Unit.PIECE, 0);
        Recipe wrongTaste = Recipe.withIngredientGroups("Falscher Geschmack", 2,
                List.of(new RecipeIngredientGroup(
                        List.of(wrongTasteOption), wrongTasteOption)),
                List.of(), List.of(fresh), DishType.MAIN);

        List<CombinedSearchResult> results = service.search(
                List.of(wrongTaste, flexible), List.of(tomato), List.of(savory, spicy),
                TasteFilterMode.AND);

        assertEquals(List.of(flexible), resultRecipes(results));
        assertEquals(MatchQuality.PERFECT,
                results.getFirst().getIngredientResult().orElseThrow().getMatchQuality());
        assertEquals(MatchQuality.PERFECT,
                results.getFirst().getTasteResult().orElseThrow().getMatchQuality());
    }

    @Test
    void ingredientGroupRatioRemainsAheadOfTasteRanking() {
        Recipe perfectIngredients = recipe(
                "Perfekte Zutaten", List.of(pasta, tomato), savory);
        Recipe betterTaste = recipe(
                "Mehr Geschmack", List.of(pasta, tomato, rice), savory, spicy);

        List<CombinedSearchResult> results = service.search(
                List.of(betterTaste, perfectIngredients), List.of(pasta, tomato),
                List.of(savory, spicy), TasteFilterMode.RANKING);

        assertEquals(List.of(perfectIngredients, betterTaste), resultRecipes(results));
        assertEquals(MatchQuality.PERFECT,
                results.getFirst().getIngredientResult().orElseThrow().getMatchQuality());
        assertEquals(MatchQuality.GOOD,
                results.get(1).getIngredientResult().orElseThrow().getMatchQuality());
    }

    @Test
    void returnsEmptyWhenTheTwoActiveFiltersHaveNoCommonRecipe() {
        Recipe ingredientMatch = recipe("Zutat", List.of(pasta), fresh);
        Recipe tasteMatch = recipe("Geschmack", List.of(rice), savory);

        assertTrue(service.search(List.of(ingredientMatch, tasteMatch), List.of(pasta),
                List.of(savory), TasteFilterMode.OR).isEmpty());
    }

    @Test
    void breaksCompleteRankingTiesByNameAndUuid() {
        Recipe later = recipe("Gleich", "00000000-0000-0000-0000-000000000002",
                List.of(pasta), savory);
        Recipe earlier = recipe("Gleich", "00000000-0000-0000-0000-000000000001",
                List.of(pasta), savory);

        List<CombinedSearchResult> results = service.search(
                List.of(later, earlier), List.of(pasta), List.of(savory),
                TasteFilterMode.RANKING);

        assertEquals(List.of(earlier, later), resultRecipes(results));
    }

    @Test
    void rejectsSearchWithoutAnyActiveFilter() {
        assertThrows(IllegalArgumentException.class,
                () -> service.search(List.of(), List.of(), List.of(), TasteFilterMode.OR));
    }

    private Recipe recipe(String name, List<Ingredient> ingredients, Taste... tastes) {
        return recipe(name, UUID.randomUUID().toString(), ingredients, tastes);
    }

    private Recipe recipe(String name, String id, List<Ingredient> ingredients, Taste... tastes) {
        List<RecipeIngredient> recipeIngredients = ingredients.stream()
                .map(ingredient -> new RecipeIngredient(
                        ingredient, BigDecimal.ONE, Unit.PIECE))
                .toList();
        return new Recipe(UUID.fromString(id), name, 2,
                recipeIngredients, List.of(), List.of(tastes));
    }

    private static List<Recipe> resultRecipes(List<CombinedSearchResult> results) {
        return results.stream().map(CombinedSearchResult::getRecipe).toList();
    }
}
