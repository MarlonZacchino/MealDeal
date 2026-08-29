package de.mealdeal.ui.controller;

import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.Taste;
import de.mealdeal.persistence.repository.IngredientRepository;
import de.mealdeal.persistence.repository.RecipeRepository;
import de.mealdeal.persistence.repository.TasteRepository;
import de.mealdeal.service.RecipeSearchService;
import de.mealdeal.ui.search.IngredientSearchModel;
import de.mealdeal.ui.search.TasteSearchModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertSame;

class IngredientSearchControllerTest {

    @Test
    void opensExistingRecipeDetailFromResultSelection() {
        Recipe recipe = new Recipe("Kartoffelsuppe", 2, List.of(), List.of(),
                List.of(new Taste("Herzhaft")));
        AtomicReference<Recipe> navigatedRecipe = new AtomicReference<>();
        IngredientSearchModel model = new IngredientSearchModel(
                new EmptyIngredientRepository(), new EmptyRecipeRepository(),
                new RecipeSearchService());
        TasteSearchModel tasteModel = new TasteSearchModel(
                new EmptyTasteRepository(), new EmptyRecipeRepository(),
                new RecipeSearchService());
        IngredientSearchController controller =
                new IngredientSearchController(model, tasteModel, navigatedRecipe::set);

        controller.openRecipe(recipe);

        assertSame(recipe, navigatedRecipe.get());
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
