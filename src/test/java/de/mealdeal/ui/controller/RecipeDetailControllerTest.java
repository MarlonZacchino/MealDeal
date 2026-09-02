package de.mealdeal.ui.controller;

import de.mealdeal.domain.DishType;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.RecipeIngredientGroup;
import de.mealdeal.domain.RecipeIngredientOption;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.repository.RecipeRepository;
import de.mealdeal.service.RecipeScaler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecipeDetailControllerTest {

    @Test
    void cancelledConfirmationDoesNotCallRepository() {
        RecordingRecipeRepository repository = new RecordingRecipeRepository(true);
        RecipeDetailController controller = new RecipeDetailController(
                repository, new RecipeScaler());
        Recipe recipe = recipe();

        RecipeDetailController.DeletionOutcome outcome =
                controller.deleteAfterConfirmation(recipe, () -> false);

        assertEquals(RecipeDetailController.DeletionOutcome.CANCELLED, outcome);
        assertEquals(0, repository.deleteCalls);
    }

    @Test
    void confirmedDeletionUsesRecipeRepository() {
        RecordingRecipeRepository repository = new RecordingRecipeRepository(true);
        RecipeDetailController controller = new RecipeDetailController(
                repository, new RecipeScaler());
        Recipe recipe = recipe();

        RecipeDetailController.DeletionOutcome outcome =
                controller.deleteAfterConfirmation(recipe, () -> true);

        assertEquals(RecipeDetailController.DeletionOutcome.DELETED, outcome);
        assertEquals(1, repository.deleteCalls);
        assertEquals(recipe.getId(), repository.deletedId);
    }

    @Test
    void displaysOnlyPresentIndividualTimesAndTheirDerivedTotal() {
        Recipe recipe = new Recipe("Baked pasta", 2, List.of(), List.of(),
                List.of(new Taste("Herzhaft")), 10, null, 70, 20, null, DishType.MAIN);

        assertEquals(List.of(
                new RecipeDetailController.TimeDisplay("Vorbereitungszeit", "10 Min."),
                new RecipeDetailController.TimeDisplay("Backzeit", "1 Std. 10 Min."),
                new RecipeDetailController.TimeDisplay("Ruhezeit", "20 Min."),
                new RecipeDetailController.TimeDisplay("Gesamtzeit", "1 Std. 40 Min.")),
                RecipeDetailController.timeDisplays(recipe));
    }

    @Test
    void displaysSingleIngredientGroupAsOneCompactOption() {
        Recipe recipe = new Recipe("Omelett", 2, List.of(new RecipeIngredient(
                new Ingredient("Ei"), new BigDecimal("4"), Unit.PIECE)),
                List.of(), List.of(new Taste("Herzhaft")));

        var displays = RecipeDetailController.ingredientGroupDisplays(
                recipe, 2, new RecipeScaler());

        assertEquals(List.of(new RecipeDetailController.IngredientGroupDisplay(List.of(
                new RecipeDetailController.IngredientOptionDisplay(
                        "4 Stück", "Ei", true)))), displays);
    }

    @Test
    void displaysAlternativeOptionsInOrderWithScaledQuantitiesAndStandard() {
        RecipeIngredientOption veal = new RecipeIngredientOption(new Ingredient("Kalb"),
                new BigDecimal("400"), Unit.GRAM, 0);
        RecipeIngredientOption chicken = new RecipeIngredientOption(new Ingredient("Hähnchen"),
                new BigDecimal("2"), Unit.PIECE, 1);
        RecipeIngredientOption pork = new RecipeIngredientOption(new Ingredient("Schwein"),
                new BigDecimal("350"), Unit.GRAM, 2);
        Recipe recipe = Recipe.withIngredientGroups("Schnitzel", 2,
                List.of(new RecipeIngredientGroup(List.of(veal, chicken, pork), chicken)),
                List.of(), List.of(new Taste("Herzhaft")), DishType.MAIN);

        var options = RecipeDetailController.ingredientGroupDisplays(
                recipe, 4, new RecipeScaler()).getFirst().options();

        assertEquals(List.of(
                new RecipeDetailController.IngredientOptionDisplay(
                        "800 g", "Kalb", false),
                new RecipeDetailController.IngredientOptionDisplay(
                        "4 Stück", "Hähnchen", true),
                new RecipeDetailController.IngredientOptionDisplay(
                        "700 g", "Schwein", false)), options);
    }

    private static Recipe recipe() {
        return new Recipe("Pasta", List.of(), List.of(), List.of(new Taste("Herzhaft")));
    }

    private static final class RecordingRecipeRepository implements RecipeRepository {
        private final boolean deletionResult;
        private int deleteCalls;
        private UUID deletedId;

        private RecordingRecipeRepository(boolean deletionResult) {
            this.deletionResult = deletionResult;
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
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deleteById(UUID id) {
            deleteCalls++;
            deletedId = id;
            return deletionResult;
        }
    }
}
