package de.mealdeal.ui.controller;

import de.mealdeal.domain.DishType;
import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredientGroup;
import de.mealdeal.domain.RecipeIngredientOption;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.repository.IngredientRepository;
import de.mealdeal.persistence.repository.RecipeRepository;
import de.mealdeal.persistence.repository.TasteRepository;
import de.mealdeal.service.CombinedRecipeSearchService;
import de.mealdeal.service.RecipeSearchService;
import de.mealdeal.ui.search.IngredientSearchModel;
import de.mealdeal.ui.search.TasteSearchModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class IngredientSearchControllerTest {

    @Test
    void opensExistingRecipeDetailFromResultSelection() {
        Recipe recipe = new Recipe("Kartoffelsuppe", 2, List.of(), List.of(),
                List.of(new Taste("Herzhaft")));
        AtomicReference<Recipe> navigatedRecipe = new AtomicReference<>();
        EmptyRecipeRepository recipes = new EmptyRecipeRepository();
        RecipeSearchService searchService = new RecipeSearchService();
        IngredientSearchModel model = new IngredientSearchModel(
                new EmptyIngredientRepository(), recipes, searchService);
        TasteSearchModel tasteModel = new TasteSearchModel(
                new EmptyTasteRepository(), recipes, searchService);
        IngredientSearchController controller =
                new IngredientSearchController(model, tasteModel, recipes,
                        new CombinedRecipeSearchService(searchService), navigatedRecipe::set);

        controller.openRecipe(recipe);

        assertSame(recipe, navigatedRecipe.get());
    }

    @Test
    void formatsMissingAlternativeGroupsInTheirOptionOrder() {
        Ingredient pasta = new Ingredient("Pasta");
        RecipeIngredientGroup matched = group(pasta);
        RecipeIngredientGroup alternatives = group(
                new Ingredient("Kalb"), new Ingredient("Hähnchen"));
        RecipeIngredientGroup single = group(new Ingredient("Knoblauch"));
        Recipe recipe = Recipe.withIngredientGroups("Flexibel", 2,
                List.of(matched, alternatives, single), List.of(),
                List.of(new Taste("Herzhaft")), DishType.MAIN);
        var result = new RecipeSearchService().searchByIngredients(
                List.of(recipe), List.of(pasta)).getFirst();

        assertEquals("Kalb oder Hähnchen, Knoblauch",
                IngredientSearchController.ingredientMissingText(result));
    }

    private static RecipeIngredientGroup group(Ingredient... ingredients) {
        List<RecipeIngredientOption> options = java.util.stream.IntStream
                .range(0, ingredients.length)
                .mapToObj(index -> new RecipeIngredientOption(ingredients[index],
                        BigDecimal.ONE, Unit.PIECE, index))
                .toList();
        return new RecipeIngredientGroup(options, options.getFirst());
    }

    private static final class EmptyTasteRepository implements TasteRepository {
        @Override public void save(Taste taste) { throw new UnsupportedOperationException(); }
        @Override public Optional<Taste> findById(UUID id) { return Optional.empty(); }
        @Override public List<Taste> findAll() { return List.of(); }
        @Override public boolean deleteById(UUID id) { throw new UnsupportedOperationException(); }
    }

    private static final class EmptyIngredientRepository implements IngredientRepository {
        @Override public void save(de.mealdeal.domain.Ingredient ingredient) { throw new UnsupportedOperationException(); }
        @Override public Optional<de.mealdeal.domain.Ingredient> findById(UUID id) { return Optional.empty(); }
        @Override public List<de.mealdeal.domain.Ingredient> findAll() { return List.of(); }
        @Override public boolean deleteById(UUID id) { throw new UnsupportedOperationException(); }
    }

    private static final class EmptyRecipeRepository implements RecipeRepository {
        @Override public void save(Recipe recipe) { throw new UnsupportedOperationException(); }
        @Override public Optional<Recipe> findById(UUID id) { return Optional.empty(); }
        @Override public List<Recipe> findAll() { return List.of(); }
        @Override public boolean deleteById(UUID id) { throw new UnsupportedOperationException(); }
    }
}
