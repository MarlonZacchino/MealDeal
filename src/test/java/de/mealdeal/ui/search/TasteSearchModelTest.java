package de.mealdeal.ui.search;

import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.Taste;
import de.mealdeal.persistence.repository.RecipeRepository;
import de.mealdeal.persistence.repository.TasteRepository;
import de.mealdeal.service.MatchQuality;
import de.mealdeal.service.RecipeSearchService;
import de.mealdeal.service.TasteFilterMode;
import de.mealdeal.service.TasteSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TasteSearchModelTest {

    private Taste savory;
    private Taste spicy;
    private Taste fresh;

    @BeforeEach
    void setUp() {
        savory = new Taste("Herzhaft");
        spicy = new Taste("Scharf");
        fresh = new Taste("Frisch");
    }

    @Test
    void selectsEachTasteOnlyOnceAndRemovesItAgain() {
        TasteSearchModel model = model(List.of(savory), List.of());

        assertEquals(TasteSearchModel.SelectionResult.ADDED, model.select(savory));
        assertEquals(TasteSearchModel.SelectionResult.ALREADY_SELECTED, model.select(savory));
        assertEquals(List.of(savory), model.getSelectedTastes());

        model.remove(savory);

        assertTrue(model.getSelectedTastes().isEmpty());
    }

    @Test
    void andReturnsOnlyRecipesContainingEverySelectedTaste() {
        Recipe all = recipe("Alle", savory, spicy);
        Recipe one = recipe("Eine", savory);
        TasteSearchModel model = selectedModel(List.of(one, all), savory, spicy);

        List<TasteSearchResult> results = model.search(TasteFilterMode.AND);

        assertEquals(List.of(all), recipes(results));
        assertEquals(MatchQuality.PERFECT, results.getFirst().getMatchQuality());
    }

    @Test
    void orReturnsEveryRecipeWithAtLeastOneSelectedTasteAlphabetically() {
        Recipe zulu = recipe("Zulu", savory);
        Recipe alpha = recipe("Alpha", spicy);
        Recipe none = recipe("Ohne", fresh);
        TasteSearchModel model = selectedModel(List.of(zulu, none, alpha), savory, spicy);

        List<TasteSearchResult> results = model.search(TasteFilterMode.OR);

        assertEquals(List.of(alpha, zulu), recipes(results));
    }

    @Test
    void rankingUsesServiceOrderAndReportsMissingTastes() {
        Recipe all = recipe("Alle", savory, spicy, fresh);
        Recipe two = recipe("Zwei", savory, spicy);
        Recipe one = recipe("Eine", savory);
        TasteSearchModel model = selectedModel(List.of(one, two, all), savory, spicy, fresh);

        List<TasteSearchResult> results = model.search(TasteFilterMode.RANKING);

        assertEquals(List.of(all, two, one), recipes(results));
        assertEquals(List.of(MatchQuality.PERFECT, MatchQuality.GOOD, MatchQuality.PARTIAL),
                results.stream().map(TasteSearchResult::getMatchQuality).toList());
        assertEquals(List.of(fresh), results.get(1).getMissingTastes());
    }

    @Test
    void returnsEmptyStateDataWhenNoTasteMatches() {
        TasteSearchModel model = selectedModel(List.of(recipe("Frisch", fresh)), savory);

        assertTrue(model.search(TasteFilterMode.RANKING).isEmpty());
    }

    @Test
    void blocksSearchWithoutTasteBeforeLoadingRecipes() {
        CountingRecipeRepository recipes = new CountingRecipeRepository(List.of());
        TasteSearchModel model = new TasteSearchModel(
                new StubTasteRepository(List.of()), recipes, new RecipeSearchService());

        assertThrows(IllegalArgumentException.class,
                () -> model.search(TasteFilterMode.RANKING));
        assertEquals(0, recipes.findAllCalls);
    }

    private TasteSearchModel selectedModel(List<Recipe> recipes, Taste... tastes) {
        TasteSearchModel model = model(List.of(tastes), recipes);
        for (Taste taste : tastes) {
            model.select(taste);
        }
        return model;
    }

    private static TasteSearchModel model(List<Taste> tastes, List<Recipe> recipes) {
        return new TasteSearchModel(new StubTasteRepository(tastes),
                new CountingRecipeRepository(recipes), new RecipeSearchService());
    }

    private static Recipe recipe(String name, Taste... tastes) {
        return new Recipe(name, 2, List.of(), List.of(), List.of(tastes));
    }

    private static List<Recipe> recipes(List<TasteSearchResult> results) {
        return results.stream().map(TasteSearchResult::getRecipe).toList();
    }

    private static final class StubTasteRepository implements TasteRepository {
        private final List<Taste> tastes;

        private StubTasteRepository(List<Taste> tastes) {
            this.tastes = tastes;
        }

        @Override public void save(Taste taste) { throw new UnsupportedOperationException(); }
        @Override public Optional<Taste> findById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public List<Taste> findAll() { return tastes; }
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
