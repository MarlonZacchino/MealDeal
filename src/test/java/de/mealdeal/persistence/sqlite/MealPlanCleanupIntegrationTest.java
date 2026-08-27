package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.RecipeStep;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import de.mealdeal.service.MealPlanCleanupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MealPlanCleanupIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void deletesOnlyEntriesOlderThanThirtyCalendarDays() {
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("cleanup.db"));
        var ingredientRepository = new SqliteIngredientRepository(database);
        var tasteRepository = new SqliteTasteRepository(database);
        var recipeRepository = new SqliteRecipeRepository(database);
        var mealPlanRepository = new SqliteMealPlanRepository(database);

        Ingredient ingredient = new Ingredient("Pasta");
        Taste taste = new Taste("Savory");
        ingredientRepository.save(ingredient);
        tasteRepository.save(taste);
        Recipe recipe = new Recipe("Pasta recipe",
                List.of(new RecipeIngredient(ingredient, BigDecimal.ONE, Unit.PIECE)),
                List.of(new RecipeStep(1, "Cook.")), List.of(taste));
        recipeRepository.save(recipe);

        for (LocalDate date : List.of(
                LocalDate.of(2026, 8, 31),
                LocalDate.of(2026, 8, 30),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 7, 31),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 9, 5))) {
            mealPlanRepository.save(new MealPlanEntry(date, recipe, 2));
        }

        Clock clock = Clock.fixed(Instant.parse("2026-08-31T10:00:00Z"),
                ZoneId.of("Europe/Berlin"));
        int deletedCount = new MealPlanCleanupService(mealPlanRepository, clock)
                .deleteExpiredEntries();

        assertEquals(2, deletedCount);
        assertTrue(mealPlanRepository.findByDate(LocalDate.of(2026, 8, 1)).isPresent());
        assertTrue(mealPlanRepository.findByDate(LocalDate.of(2026, 8, 30)).isPresent());
        assertTrue(mealPlanRepository.findByDate(LocalDate.of(2026, 8, 31)).isPresent());
        assertTrue(mealPlanRepository.findByDate(LocalDate.of(2026, 9, 5)).isPresent());
        assertTrue(mealPlanRepository.findByDate(LocalDate.of(2026, 7, 31)).isEmpty());
        assertTrue(recipeRepository.findById(recipe.getId()).isPresent());
        assertTrue(ingredientRepository.findById(ingredient.getId()).isPresent());
        assertTrue(tasteRepository.findById(taste.getId()).isPresent());
    }
}
