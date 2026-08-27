package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import de.mealdeal.service.RecipeScaler;
import de.mealdeal.service.ShoppingListService;
import de.mealdeal.service.WeekService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShoppingListServiceIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void buildsAggregatedShoppingListFromPersistedMealPlan() {
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("shopping.db"));
        var ingredientRepository = new SqliteIngredientRepository(database);
        var tasteRepository = new SqliteTasteRepository(database);
        var recipeRepository = new SqliteRecipeRepository(database);
        var mealPlanRepository = new SqliteMealPlanRepository(database);

        Ingredient pasta = new Ingredient("Pasta");
        Taste savory = new Taste("Savory");
        ingredientRepository.save(pasta);
        tasteRepository.save(savory);

        Recipe gramRecipe = new Recipe("Gram recipe", 2,
                List.of(new RecipeIngredient(pasta, new BigDecimal("500"), Unit.GRAM)),
                List.of(), List.of(savory));
        Recipe kilogramRecipe = new Recipe("Kilogram recipe", 2,
                List.of(new RecipeIngredient(pasta, BigDecimal.ONE, Unit.KILOGRAM)),
                List.of(), List.of(savory));
        recipeRepository.save(gramRecipe);
        recipeRepository.save(kilogramRecipe);

        LocalDate today = LocalDate.of(2026, 9, 1);
        mealPlanRepository.save(new MealPlanEntry(today, gramRecipe, 4));
        mealPlanRepository.save(new MealPlanEntry(today.plusDays(1), kilogramRecipe, 1));

        Clock clock = Clock.fixed(today.atStartOfDay(ZoneId.of("Europe/Berlin")).toInstant(),
                ZoneId.of("Europe/Berlin"));
        var service = new ShoppingListService(
                mealPlanRepository, new RecipeScaler(), new WeekService(), clock);

        var shoppingList = service.buildForCurrentWeek();

        assertEquals(1, shoppingList.getItems().size());
        assertEquals(pasta, shoppingList.getItems().getFirst().getIngredient());
        assertEquals(new BigDecimal("1500.0"),
                shoppingList.getItems().getFirst().getQuantity().getAmount());
        assertEquals(Unit.GRAM, shoppingList.getItems().getFirst().getQuantity().getUnit());
    }
}
