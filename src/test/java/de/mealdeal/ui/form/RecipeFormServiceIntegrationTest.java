package de.mealdeal.ui.form;

import de.mealdeal.domain.Unit;
import de.mealdeal.domain.IngredientCategories;
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
import java.util.UUID;

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
                List.of("Herzhaft"), List.of("Schneiden.", "Braten."), "15", "20", "25",
                "", "", "", "", DishType.MAIN));

        assertEquals(List.of("Kartoffel"), ingredients.findAll().stream()
                .map(value -> value.getName()).toList());
        assertEquals(List.of("Herzhaft"), tastes.findAll().stream()
                .map(value -> value.getName()).toList());
        assertEquals("Kartoffelpfanne", recipes.findAll().getFirst().getName());
        assertEquals(2, recipes.findAll().getFirst().getSteps().size());
        assertEquals(15, recipes.findAll().getFirst().getPreparationTimeMinutes().orElseThrow());
        assertEquals(20, recipes.findAll().getFirst().getCookingTimeMinutes().orElseThrow());
        assertEquals(25, recipes.findAll().getFirst().getBakingTimeMinutes().orElseThrow());
        assertEquals(60, recipes.findAll().getFirst().getTotalTimeMinutes().orElseThrow());
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
                List.of("Cremig"), List.of("Schneiden.", "Backen."), "10", "30", "20",
                "", "", "", "", DishType.MAIN));

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
        assertEquals(20, loaded.getBakingTimeMinutes().orElseThrow());
        assertEquals(60, loaded.getTotalTimeMinutes().orElseThrow());
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

    @Test
    void createsAndEditsAlternativeGroupsWithStableExistingIds() {
        SqliteDatabase database = new SqliteDatabase(
                temporaryDirectory.resolve("alternative-form.db"));
        var ingredients = new SqliteIngredientRepository(database);
        var tastes = new SqliteTasteRepository(database);
        var recipes = new SqliteRecipeRepository(database);
        RecipeFormService service = new RecipeFormService(recipes, ingredients, tastes);
        UUID groupId = UUID.randomUUID();
        UUID vealId = UUID.randomUUID();
        UUID chickenId = UUID.randomUUID();
        Recipe created = service.createAndSave(groupInput(groupId, List.of(
                option(vealId, "Kalb", "400", Unit.GRAM, 0),
                option(chickenId, "Hähnchen", "350", Unit.GRAM, 1)), vealId));

        Recipe initiallyLoaded = recipes.findById(created.getId()).orElseThrow();
        assertEquals(List.of(vealId, chickenId), initiallyLoaded.getIngredientGroups().getFirst()
                .getOptions().stream().map(value -> value.getId()).toList());

        UUID porkId = UUID.randomUUID();
        service.updateAndSave(created.getId(), groupInput(groupId, List.of(
                option(chickenId, "Hähnchen", "2", Unit.PIECE, 0),
                option(porkId, "Schwein", "425", Unit.GRAM, 1)), porkId));

        Recipe edited = recipes.findById(created.getId()).orElseThrow();
        var editedGroup = edited.getIngredientGroups().getFirst();
        assertEquals(created.getId(), edited.getId());
        assertEquals(groupId, editedGroup.getId());
        assertEquals(List.of(chickenId, porkId), editedGroup.getOptions().stream()
                .map(value -> value.getId()).toList());
        assertEquals(porkId, editedGroup.getStandardOptionId());
        assertEquals(List.of(Unit.PIECE, Unit.GRAM), editedGroup.getOptions().stream()
                .map(value -> value.getUnit()).toList());
        assertEquals(List.of("Hähnchen", "Kalb", "Schwein"), ingredients.findAll().stream()
                .map(value -> value.getName()).sorted().toList());
    }

    @Test
    void assignsCategoryToNewIngredientAndPreservesItWhenRecipeIsEdited() {
        SqliteDatabase database = new SqliteDatabase(
                temporaryDirectory.resolve("ingredient-category-form.db"));
        var ingredients = new SqliteIngredientRepository(database);
        var tastes = new SqliteTasteRepository(database);
        var recipes = new SqliteRecipeRepository(database);
        RecipeFormService service = new RecipeFormService(recipes, ingredients, tastes);
        Recipe created = service.createAndSave(new RecipeFormInput("Gemüsepfanne", "2",
                List.of(new IngredientFormInput("Paprika", "2", Unit.PIECE,
                        IngredientCategories.VEGETABLES)),
                List.of("Herzhaft"), List.of()));

        service.updateAndSave(created.getId(), new RecipeFormInput("Gemüsepfanne", "4",
                List.of(new IngredientFormInput("Paprika", "3", Unit.PIECE,
                        IngredientCategories.OTHER)),
                List.of("Herzhaft"), List.of()));

        assertEquals(IngredientCategories.VEGETABLES,
                ingredients.findAll().getFirst().getCategory());
        assertEquals(IngredientCategories.VEGETABLES,
                recipes.findById(created.getId()).orElseThrow().getIngredients().getFirst()
                        .getIngredient().getCategory());
    }

    private static RecipeFormInput groupInput(UUID groupId,
                                               List<IngredientOptionFormInput> options,
                                               UUID standardId) {
        return RecipeFormInput.withIngredientGroups("Schnitzel", "2",
                List.of(new IngredientGroupFormInput(groupId, options, standardId)),
                List.of("Herzhaft"), List.of(), "", "", "", "", "", "", "",
                DishType.MAIN);
    }

    private static IngredientOptionFormInput option(UUID id, String name, String quantity,
                                                    Unit unit, int position) {
        return new IngredientOptionFormInput(id, name, quantity, unit, position);
    }
}
