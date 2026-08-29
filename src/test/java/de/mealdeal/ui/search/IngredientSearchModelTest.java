package de.mealdeal.ui.search;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.RecipeStep;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.repository.IngredientRepository;
import de.mealdeal.persistence.repository.RecipeRepository;
import de.mealdeal.service.IngredientSearchResult;
import de.mealdeal.service.MatchQuality;
import de.mealdeal.service.RecipeSearchService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngredientSearchModelTest {

    @Test
    void acceptsOneThroughTenIngredientsAndBlocksEleventh() {
        IngredientSearchModel model = model(List.of(), List.of());
        List<Ingredient> ingredients = new ArrayList<>();
        for (int index = 1; index <= 11; index++) {
            ingredients.add(new Ingredient("Zutat " + index));
        }

        assertEquals(IngredientSearchModel.SelectionResult.ADDED,
                model.select(ingredients.getFirst()));
        for (int index = 1; index < 10; index++) {
            assertEquals(IngredientSearchModel.SelectionResult.ADDED,
                    model.select(ingredients.get(index)));
        }
        assertEquals(IngredientSearchModel.SelectionResult.LIMIT_REACHED,
                model.select(ingredients.get(10)));
        assertEquals(10, model.getSelectedIngredients().size());
    }

    @Test
    void blocksSearchWithoutSelectedIngredientBeforeLoadingRecipes() {
        CountingRecipeRepository recipes = new CountingRecipeRepository(List.of());
        IngredientSearchModel model = new IngredientSearchModel(
                new StubIngredientRepository(List.of()), recipes, new RecipeSearchService());

        assertThrows(IllegalArgumentException.class, model::search);
        assertEquals(0, recipes.findAllCalls);
    }

    @Test
    void delegatesRankingCountsAndMissingIngredientsToSearchService() {
        Ingredient pasta = new Ingredient("Pasta");
        Ingredient tomato = new Ingredient("Tomate");
        Ingredient cheese = new Ingredient("Käse");
        Recipe perfect = recipe("Perfekt", pasta, tomato, cheese);
        Recipe good = recipe("Gut", pasta, tomato);
        Recipe partial = recipe("Teilweise", pasta);
        IngredientSearchModel model = model(
                List.of(pasta, tomato, cheese), List.of(partial, good, perfect));
        model.select(pasta);
        model.select(tomato);
        model.select(cheese);

        List<IngredientSearchResult> results = model.search();

        assertEquals(List.of(perfect, good, partial), results.stream()
                .map(IngredientSearchResult::getRecipe).toList());
        assertEquals(List.of(MatchQuality.PERFECT, MatchQuality.GOOD, MatchQuality.PARTIAL),
                results.stream().map(IngredientSearchResult::getMatchQuality).toList());
        assertEquals(2, results.get(1).getMatchedCount());
        assertEquals(3, results.get(1).getSelectedCount());
        assertEquals(List.of(cheese), results.get(1).getMissingIngredients());
    }

    @Test
    void returnsEmptyStateDataWhenNoRecipeMatches() {
        Ingredient selected = new Ingredient("Pasta");
        Ingredient other = new Ingredient("Reis");
        IngredientSearchModel model = model(
                List.of(selected, other), List.of(recipe("Reisgericht", other)));
        model.select(selected);

        assertTrue(model.search().isEmpty());
    }

    @Test
    void loadsAvailableIngredientsInDeterministicNameOrder() {
        IngredientSearchModel model = model(
                List.of(new Ingredient("Zwiebel"), new Ingredient("Apfel")), List.of());

        assertEquals(List.of("Apfel", "Zwiebel"), model.loadAvailableIngredients().stream()
                .map(Ingredient::getName).toList());
    }

    @Test
    void clearsEverySelectedIngredient() {
        IngredientSearchModel model = model(List.of(), List.of());
        model.select(new Ingredient("Pasta"));
        model.select(new Ingredient("Tomate"));

        model.clear();

        assertTrue(model.getSelectedIngredients().isEmpty());
    }

    private static IngredientSearchModel model(List<Ingredient> ingredients,
                                               List<Recipe> recipes) {
        return new IngredientSearchModel(new StubIngredientRepository(ingredients),
                new CountingRecipeRepository(recipes), new RecipeSearchService());
    }

    private static Recipe recipe(String name, Ingredient... ingredients) {
        List<RecipeIngredient> entries = java.util.Arrays.stream(ingredients)
                .map(ingredient -> new RecipeIngredient(ingredient, BigDecimal.ONE, Unit.PIECE))
                .toList();
        return new Recipe(name, 2, entries, List.of(new RecipeStep(1, "Zubereiten.")),
                List.of(new Taste("Herzhaft")));
    }

    private static final class StubIngredientRepository implements IngredientRepository {
        private final List<Ingredient> ingredients;

        private StubIngredientRepository(List<Ingredient> ingredients) {
            this.ingredients = ingredients;
        }

        @Override public void save(Ingredient ingredient) { throw new UnsupportedOperationException(); }
        @Override public Optional<Ingredient> findById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public List<Ingredient> findAll() { return ingredients; }
        @Override public boolean deleteById(UUID id) { throw new UnsupportedOperationException(); }
    }

    private static final class CountingRecipeRepository implements RecipeRepository {
        private final List<Recipe> recipes;
        private int findAllCalls;

        private CountingRecipeRepository(List<Recipe> recipes) {
            this.recipes = recipes;
        }

        @Override public void save(Recipe recipe) { throw new UnsupportedOperationException(); }
        @Override public Optional<Recipe> findById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public List<Recipe> findAll() { findAllCalls++; return recipes; }
        @Override public boolean deleteById(UUID id) { throw new UnsupportedOperationException(); }
    }
}
