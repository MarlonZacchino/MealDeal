package de.mealdeal.ui.controller;

import de.mealdeal.domain.DishType;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.Taste;
import de.mealdeal.persistence.repository.RecipeRepository;
import de.mealdeal.service.RecipeScaler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
                List.of(new Taste("Herzhaft")), 10, null, 70, null, DishType.MAIN);

        assertEquals(List.of(
                new RecipeDetailController.TimeDisplay("Vorbereitungszeit", "10 Min."),
                new RecipeDetailController.TimeDisplay("Backzeit", "1 Std. 10 Min."),
                new RecipeDetailController.TimeDisplay("Gesamtzeit", "1 Std. 20 Min.")),
                RecipeDetailController.timeDisplays(recipe));
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
