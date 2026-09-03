package de.mealdeal.ui.controller;

import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.Taste;
import de.mealdeal.persistence.repository.MealPlanRepository;
import de.mealdeal.persistence.repository.RecipeRepository;
import de.mealdeal.service.WeeklyMealPlanService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WeekPlanControllerTest {

    @Test
    void opensPlannedRecipeInExistingDetailNavigation() {
        Recipe recipe = new Recipe("Kartoffelsuppe", 2, List.of(), List.of(),
                List.of(new Taste("Herzhaft")));
        AtomicReference<Recipe> navigatedRecipe = new AtomicReference<>();
        WeekPlanController controller = new WeekPlanController(
                new WeeklyMealPlanService(
                        new EmptyMealPlanRepository(), new EmptyRecipeRepository()),
                navigatedRecipe::set);

        controller.openRecipe(recipe);

        assertSame(recipe, navigatedRecipe.get());
    }

    @Test
    void formatsServingCountForCompactViewRows() {
        assertEquals("1 Person", MealPlanEntryRowFactory.servingCountText(1));
        assertEquals("2 Personen", MealPlanEntryRowFactory.servingCountText(2));
    }

    @Test
    void selectsCompactAndWideViewportStylesAtTheirBoundaries() {
        assertEquals(List.of("viewport-compact"),
                MainController.viewportStyleClassesFor(1099));
        assertEquals(List.of(), MainController.viewportStyleClassesFor(1100));
        assertEquals(List.of("viewport-wide"),
                MainController.viewportStyleClassesFor(1440));
        assertEquals(List.of("viewport-wide", "viewport-extra-wide"),
                MainController.viewportStyleClassesFor(2100));
    }

    private static final class EmptyMealPlanRepository implements MealPlanRepository {
        @Override public void save(MealPlanEntry entry) { throw new UnsupportedOperationException(); }
        @Override public void applyChanges(List<MealPlanEntry> entriesToSave,
                                           List<UUID> entryIdsToDelete) {
            throw new UnsupportedOperationException();
        }
        @Override public Optional<MealPlanEntry> findById(UUID id) { return Optional.empty(); }
        @Override public Optional<MealPlanEntry> findByDate(LocalDate date) {
            return Optional.empty();
        }
        @Override public List<MealPlanEntry> findBetween(
                LocalDate startInclusive, LocalDate endInclusive) { return List.of(); }
        @Override public boolean deleteById(UUID id) { return false; }
        @Override public int deleteBefore(LocalDate cutoffExclusive) { return 0; }
    }

    private static final class EmptyRecipeRepository implements RecipeRepository {
        @Override public void save(Recipe recipe) { throw new UnsupportedOperationException(); }
        @Override public Optional<Recipe> findById(UUID id) { return Optional.empty(); }
        @Override public List<Recipe> findAll() { return List.of(); }
        @Override public boolean deleteById(UUID id) { return false; }
    }
}
