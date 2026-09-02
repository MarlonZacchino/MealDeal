package de.mealdeal.ui.controller;

import de.mealdeal.domain.DishType;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.Taste;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.repository.RecipeRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecipesControllerTest {

    private static final Taste SAVORY = new Taste(
            UUID.fromString("00000000-0000-0000-0000-000000000100"), "Herzhaft");

    @Test
    void loadsRecipesAlphabeticallyWithUuidFallback() {
        Recipe laterPasta = recipe("00000000-0000-0000-0000-000000000002", "Pasta");
        Recipe ziti = recipe("00000000-0000-0000-0000-000000000003", "Ziti");
        Recipe earlierPasta = recipe("00000000-0000-0000-0000-000000000001", "Pasta");
        Recipe alfredo = recipe("00000000-0000-0000-0000-000000000004", "Alfredo");
        RecipesController controller = new RecipesController(
                new StubRecipeRepository(List.of(laterPasta, ziti, earlierPasta, alfredo)));

        List<Recipe> result = controller.loadSortedRecipes();

        assertEquals(List.of(alfredo, earlierPasta, laterPasta, ziti), result);
    }

    @Test
    void returnsEmptyListForEmptyRepository() {
        RecipesController controller = new RecipesController(
                new StubRecipeRepository(List.of()));

        assertEquals(List.of(), controller.loadSortedRecipes());
    }

    @Test
    void groupsMainSideAndDessertWhileKeepingSortedOrderInsideEachGroup() {
        Recipe dessert = recipe("00000000-0000-0000-0000-000000000005",
                "Eis", DishType.DESSERT);
        Recipe laterMain = recipe("00000000-0000-0000-0000-000000000004",
                "Suppe", DishType.MAIN);
        Recipe side = recipe("00000000-0000-0000-0000-000000000003",
                "Salat", DishType.SIDE);
        Recipe earlierMain = recipe("00000000-0000-0000-0000-000000000002",
                "Auflauf", DishType.MAIN);
        RecipesController controller = new RecipesController(new StubRecipeRepository(
                List.of(dessert, laterMain, side, earlierMain)));

        var groups = controller.groupByDishType(controller.loadSortedRecipes());

        assertEquals(List.of(earlierMain, laterMain), groups.get(DishType.MAIN));
        assertEquals(List.of(side), groups.get(DishType.SIDE));
        assertEquals(List.of(dessert), groups.get(DishType.DESSERT));
    }

    @Test
    void keepsEmptyDishTypeGroupsVisibleInTheGroupedModel() {
        RecipesController controller = new RecipesController(new StubRecipeRepository(List.of()));

        var groups = controller.groupByDishType(List.of());

        assertEquals(List.of(), groups.get(DishType.MAIN));
        assertEquals(List.of(), groups.get(DishType.SIDE));
        assertEquals(List.of(), groups.get(DishType.DESSERT));
    }

    @Test
    void groupedRecipeEntryKeepsExistingDetailNavigation() {
        Recipe recipe = recipe("00000000-0000-0000-0000-000000000007",
                "Suppe", DishType.MAIN);
        AtomicReference<Recipe> opened = new AtomicReference<>();
        RecipesController controller = new RecipesController(
                new StubRecipeRepository(List.of(recipe)), opened::set);

        controller.openRecipe(recipe);

        assertEquals(recipe, opened.get());
    }

    @Test
    void doesNotSwallowRepositoryFailure() {
        PersistenceException failure = new PersistenceException("Database unavailable.");
        RecipesController controller = new RecipesController(new StubRecipeRepository(failure));

        assertEquals(failure,
                assertThrows(PersistenceException.class, controller::loadSortedRecipes));
    }

    private static Recipe recipe(String id, String name) {
        return new Recipe(UUID.fromString(id), name, 2, List.of(), List.of(), List.of(SAVORY));
    }

    private static Recipe recipe(String id, String name, DishType dishType) {
        return new Recipe(UUID.fromString(id), name, 2, List.of(), List.of(),
                List.of(SAVORY), null, null, null, dishType);
    }

    private static final class StubRecipeRepository implements RecipeRepository {
        private final List<Recipe> recipes;
        private final PersistenceException failure;

        private StubRecipeRepository(List<Recipe> recipes) {
            this.recipes = recipes;
            failure = null;
        }

        private StubRecipeRepository(PersistenceException failure) {
            recipes = List.of();
            this.failure = failure;
        }

        @Override
        public void save(Recipe recipe) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Recipe> findById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Recipe> findAll() {
            if (failure != null) {
                throw failure;
            }
            return recipes;
        }

        @Override
        public boolean deleteById(UUID id) {
            throw new UnsupportedOperationException();
        }
    }
}
