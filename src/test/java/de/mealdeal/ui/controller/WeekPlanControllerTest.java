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

    private static final class EmptyMealPlanRepository implements MealPlanRepository {
        @Override public void save(MealPlanEntry entry) { throw new UnsupportedOperationException(); }
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
