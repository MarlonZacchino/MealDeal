package de.mealdeal.ui.form;

import de.mealdeal.domain.Unit;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.DishType;
import de.mealdeal.persistence.sqlite.SqliteDatabase;
import de.mealdeal.persistence.sqlite.SqliteIngredientRepository;
import de.mealdeal.persistence.sqlite.SqliteRecipeRepository;
import de.mealdeal.persistence.sqlite.SqliteTasteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeFormServiceIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsNewCentralDataAndCompleteRecipeInSqlite() {
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("form.db"));
        var ingredients = new SqliteIngredientRepository(database);
        var tastes = new SqliteTasteRepository(database);
        var recipes = new SqliteRecipeRepository(database);
        RecipeFormService service = new RecipeFormService(recipes, ingredients, tastes);

        service.createAndSave(new RecipeFormInput("Kartoffelpfanne", "3",
                List.of(new IngredientFormInput("Kartoffel", "750", Unit.GRAM)),
                List.of("Herzhaft"), List.of("Schneiden.", "Braten."), "15", "20"));

        assertEquals(List.of("Kartoffel"), ingredients.findAll().stream()
                .map(value -> value.getName()).toList());
        assertEquals(List.of("Herzhaft"), tastes.findAll().stream()
                .map(value -> value.getName()).toList());
        assertEquals("Kartoffelpfanne", recipes.findAll().getFirst().getName());
        assertEquals(2, recipes.findAll().getFirst().getSteps().size());
        assertEquals(15, recipes.findAll().getFirst().getPreparationTimeMinutes().orElseThrow());
        assertEquals(20, recipes.findAll().getFirst().getCookingTimeMinutes().orElseThrow());
        assertEquals(35, recipes.findAll().getFirst().getTotalTimeMinutes().orElseThrow());
    }

    @Test
    void replacesPersistedRecipeDataWithoutChangingUuid() {
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("edit-form.db"));
        var ingredients = new SqliteIngredientRepository(database);
        var tastes = new SqliteTasteRepository(database);
        var recipes = new SqliteRecipeRepository(database);
        RecipeFormService service = new RecipeFormService(recipes, ingredients, tastes);
        Recipe original = service.createAndSave(new RecipeFormInput("Kartoffelpfanne", "2",
                List.of(new IngredientFormInput("Kartoffel", "500", Unit.GRAM)),
                List.of("Herzhaft"), List.of("Braten.")));

        Recipe updated = service.updateAndSave(original.getId(), new RecipeFormInput(
                "Kartoffelauflauf", "4",
                List.of(new IngredientFormInput("Kartoffel", "1,25", Unit.KILOGRAM)),
                List.of("Cremig"), List.of("Schneiden.", "Backen."), "10", "30"));

        Recipe loaded = recipes.findById(original.getId()).orElseThrow();
        assertEquals(original.getId(), updated.getId());
        assertEquals(original.getId(), loaded.getId());
        assertEquals("Kartoffelauflauf", loaded.getName());
        assertEquals(4, loaded.getStandardServingCount());
        assertEquals(new java.math.BigDecimal("1.25"),
                loaded.getIngredients().getFirst().getQuantity());
        assertEquals(Unit.KILOGRAM, loaded.getIngredients().getFirst().getUnit());
        assertEquals(List.of("Cremig"), loaded.getTastes().stream()
                .map(value -> value.getName()).toList());
        assertEquals(List.of("Schneiden.", "Backen."), loaded.getSteps().stream()
                .map(value -> value.getDescription()).toList());
        assertEquals(1, recipes.findAll().size());
        assertEquals(10, loaded.getPreparationTimeMinutes().orElseThrow());
        assertEquals(30, loaded.getCookingTimeMinutes().orElseThrow());
        assertEquals(40, loaded.getTotalTimeMinutes().orElseThrow());
    }

    @Test
    void persistsNutritionInfoThroughCreateAndEdit() {
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("nutrition-form.db"));
        var ingredients = new SqliteIngredientRepository(database);
        var tastes = new SqliteTasteRepository(database);
        var recipes = new SqliteRecipeRepository(database);
        RecipeFormService service = new RecipeFormService(recipes, ingredients, tastes);

        Recipe created = service.createAndSave(new RecipeFormInput("Brotzeit", "2",
                List.of(new IngredientFormInput("Brot", "2", Unit.PIECE)),
                List.of("Herzhaft"), List.of(), "", "", "650", "42", "71,5", "18"));
        Recipe updated = service.updateAndSave(created.getId(), new RecipeFormInput(
                "Brotzeit", "2", List.of(new IngredientFormInput("Brot", "2", Unit.PIECE)),
                List.of("Herzhaft"), List.of(), "", "", "", "", "", "0"));

        assertEquals(650, created.getNutritionInfo().orElseThrow()
                .getCaloriesKcal().orElseThrow());
        var loadedNutrition = recipes.findById(updated.getId()).orElseThrow()
                .getNutritionInfo().orElseThrow();
        assertTrue(loadedNutrition.getCaloriesKcal().isEmpty());
        assertEquals(java.math.BigDecimal.ZERO, loadedNutrition.getFatGrams().orElseThrow());
    }

    @Test
    void persistsRecipeWithoutStepsAndAllowsAddingThemLater() {
        SqliteDatabase database = new SqliteDatabase(
                temporaryDirectory.resolve("optional-steps.db"));
        var ingredients = new SqliteIngredientRepository(database);
        var tastes = new SqliteTasteRepository(database);
        var recipes = new SqliteRecipeRepository(database);
        RecipeFormService service = new RecipeFormService(recipes, ingredients, tastes);
        Recipe created = service.createAndSave(new RecipeFormInput("Brotzeit", "2",
                List.of(new IngredientFormInput("Brot", "2", Unit.PIECE)),
                List.of("Herzhaft"), List.of()));

        Recipe loadedWithoutSteps = recipes.findById(created.getId()).orElseThrow();
        assertTrue(loadedWithoutSteps.getSteps().isEmpty());

        service.updateAndSave(created.getId(), new RecipeFormInput("Brotzeit", "2",
                List.of(new IngredientFormInput("Brot", "2", Unit.PIECE)),
                List.of("Herzhaft"), List.of("Anrichten.", "Servieren.")));

        Recipe loadedWithSteps = recipes.findById(created.getId()).orElseThrow();
        assertEquals(created.getId(), loadedWithSteps.getId());
        assertEquals(List.of(1, 2), loadedWithSteps.getSteps().stream()
                .map(step -> step.getPosition()).toList());
        assertEquals(List.of("Anrichten.", "Servieren."), loadedWithSteps.getSteps().stream()
                .map(step -> step.getDescription()).toList());

        service.updateAndSave(created.getId(), new RecipeFormInput("Brotzeit", "2",
                List.of(new IngredientFormInput("Brot", "2", Unit.PIECE)),
                List.of("Herzhaft"), List.of()));

        assertTrue(recipes.findById(created.getId()).orElseThrow().getSteps().isEmpty());
    }

    @Test
    void persistsSelectedSideDishTypeThroughCreateAndEdit() {
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("side-form.db"));
        var ingredients = new SqliteIngredientRepository(database);
        var tastes = new SqliteTasteRepository(database);
        var recipes = new SqliteRecipeRepository(database);
        RecipeFormService service = new RecipeFormService(recipes, ingredients, tastes);
        RecipeFormInput sideInput = new RecipeFormInput("Knoblauchbrot", "2",
                List.of(new IngredientFormInput("Brot", "2", Unit.SLICE)),
                List.of("Herzhaft"), List.of(), "", "", "", "", "", "",
                DishType.SIDE);

        Recipe created = service.createAndSave(sideInput);
        Recipe updated = service.updateAndSave(created.getId(), sideInput);
        Recipe loaded = recipes.findById(created.getId()).orElseThrow();

        assertEquals(DishType.SIDE, created.getDishType());
        assertEquals(DishType.SIDE, updated.getDishType());
        assertEquals(DishType.SIDE, loaded.getDishType());
    }
}
