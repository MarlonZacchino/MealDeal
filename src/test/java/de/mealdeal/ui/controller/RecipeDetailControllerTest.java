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
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
    void displaysAllTimesWithPlaceholdersAndSecondPreciseDerivedTotal() {
        Recipe recipe = Recipe.withIngredientGroupDurations(UUID.randomUUID(), "Baked pasta", 2,
                List.of(), List.of(), List.of(new Taste("Herzhaft")),
                Duration.ofMinutes(10), null, Duration.ofHours(1), Duration.ofSeconds(30),
                null, DishType.MAIN);

        assertEquals(List.of(
                new RecipeDetailController.TimeDisplay("Vorbereitung", "10 Min."),
                new RecipeDetailController.TimeDisplay("Kochen", "–"),
                new RecipeDetailController.TimeDisplay("Backen", "1 Std."),
                new RecipeDetailController.TimeDisplay("Ruhezeit", "30 Sek."),
                new RecipeDetailController.TimeDisplay("Gesamt", "1 Std. 10 Min. 30 Sek.")),
                RecipeDetailController.timeDisplays(recipe));
    }

    @Test
    void displaysSingleIngredientGroupAsOneCompactScaledRow() {
        Recipe recipe = new Recipe("Omelett", 2, List.of(new RecipeIngredient(
                new Ingredient("Ei"), new BigDecimal("4"), Unit.PIECE)),
                List.of(), List.of(new Taste("Herzhaft")));

        var rows = new RecipeDetailIngredientModel(recipe, new RecipeScaler()).rows();

        assertEquals(1, rows.size());
        assertEquals("Ei", rows.getFirst().ingredientName());
        assertEquals("4 Stück", rows.getFirst().quantity());
        assertFalse(rows.getFirst().hasAlternatives());
    }

    @Test
    void temporaryAlternativeSelectionScalesAndDoesNotChangeRecipeDefault() {
        RecipeIngredientOption veal = new RecipeIngredientOption(new Ingredient("Kalb"),
                new BigDecimal("400"), Unit.GRAM, 0);
        RecipeIngredientOption chicken = new RecipeIngredientOption(new Ingredient("Hähnchen"),
                new BigDecimal("2"), Unit.PIECE, 1);
        RecipeIngredientOption pork = new RecipeIngredientOption(new Ingredient("Schwein"),
                new BigDecimal("350"), Unit.GRAM, 2);
        Recipe recipe = Recipe.withIngredientGroups("Schnitzel", 2,
                List.of(new RecipeIngredientGroup(List.of(veal, chicken, pork), chicken)),
                List.of(), List.of(new Taste("Herzhaft")), DishType.MAIN);

        RecipeDetailIngredientModel model = new RecipeDetailIngredientModel(
                recipe, new RecipeScaler());
        model.setServingCount(4);
        var defaultRow = model.rows().getFirst();
        model.selectOption(defaultRow.groupId(), veal.getId());
        var selectedRow = model.rows().getFirst();

        assertEquals("Hähnchen", defaultRow.ingredientName());
        assertEquals("4 Stück", defaultRow.quantity());
        assertEquals(List.of("Kalb", "Hähnchen", "Schwein"), defaultRow.alternatives().stream()
                .map(RecipeDetailIngredientModel.Alternative::name).toList());
        assertEquals("Kalb", selectedRow.ingredientName());
        assertEquals("800 g", selectedRow.quantity());
        assertEquals(chicken.getId(), recipe.getIngredientGroups().getFirst()
                .getStandardOptionId());

        var reopened = new RecipeDetailIngredientModel(recipe, new RecipeScaler());
        assertEquals("Hähnchen", reopened.rows().getFirst().ingredientName());
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
