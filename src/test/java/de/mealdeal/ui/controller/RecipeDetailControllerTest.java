package de.mealdeal.ui.controller;

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
