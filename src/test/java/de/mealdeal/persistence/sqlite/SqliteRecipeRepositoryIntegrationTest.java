package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.DishType;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.RecipeIngredientGroup;
import de.mealdeal.domain.RecipeIngredientOption;
import de.mealdeal.domain.RecipeStep;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteRecipeRepositoryIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    private SqliteIngredientRepository ingredientRepository;
    private SqliteTasteRepository tasteRepository;
    private SqliteRecipeRepository recipeRepository;
    private Ingredient pasta;
    private Ingredient salt;
    private Taste savory;
    private Taste mild;
    private SqliteDatabase database;

    @BeforeEach
    void setUp() {
        database = new SqliteDatabase(temporaryDirectory.resolve("recipes.db"));
        ingredientRepository = new SqliteIngredientRepository(database);
        tasteRepository = new SqliteTasteRepository(database);
        recipeRepository = new SqliteRecipeRepository(database);

        pasta = new Ingredient("Pasta");
        salt = new Ingredient("Salt");
        savory = new Taste("Savory");
        mild = new Taste("Mild");
        ingredientRepository.save(pasta);
        ingredientRepository.save(salt);
        tasteRepository.save(savory);
        tasteRepository.save(mild);
    }

    @Test
    void savesAndLoadsCompleteRecipeExactly() {
        BigDecimal exactQuantity = new BigDecimal("500.1250");
        Recipe recipe = new Recipe("Pasta", 3,
                List.of(new RecipeIngredient(pasta, exactQuantity, Unit.GRAM)),
                List.of(new RecipeStep(2, "Serve."), new RecipeStep(1, "Cook.")),
                List.of(savory, mild));

        recipeRepository.save(recipe);
        Recipe loaded = recipeRepository.findById(recipe.getId()).orElseThrow();

        assertEquals(recipe.getId(), loaded.getId());
        assertEquals("Pasta", loaded.getName());
        assertEquals(3, loaded.getStandardServingCount());
        assertEquals(exactQuantity, loaded.getIngredients().getFirst().getQuantity());
        assertEquals(Unit.GRAM, loaded.getIngredients().getFirst().getUnit());
        assertEquals(pasta, loaded.getIngredients().getFirst().getIngredient());
        assertEquals(List.of(1, 2), loaded.getSteps().stream().map(RecipeStep::getPosition).toList());
        assertEquals(2, loaded.getTastes().size());
    }

    @Test
    void savesAndLoadsSliceUnitByItsEnumName() {
        Recipe recipe = new Recipe("Toast",
                List.of(new RecipeIngredient(pasta, new BigDecimal("2"), Unit.SLICE)),
                List.of(new RecipeStep(1, "Toast.")), List.of(savory));

        recipeRepository.save(recipe);

        assertEquals(Unit.SLICE, recipeRepository.findById(recipe.getId()).orElseThrow()
                .getIngredients().getFirst().getUnit());
    }

    @Test
    void savesAndLoadsCloveAndSprigUnitsByTheirEnumNames() {
        Recipe recipe = new Recipe("Seasoning", List.of(
                new RecipeIngredient(pasta, new BigDecimal("2"), Unit.CLOVE),
                new RecipeIngredient(new Ingredient("Thyme"), BigDecimal.ONE, Unit.SPRIG)),
                List.of(), List.of(savory));
        ingredientRepository.save(recipe.getIngredients().get(1).getIngredient());

        recipeRepository.save(recipe);

        List<Unit> loadedUnits = recipeRepository.findById(recipe.getId()).orElseThrow()
                .getIngredients().stream().map(RecipeIngredient::getUnit).toList();
        assertEquals(List.of(Unit.CLOVE, Unit.SPRIG), loadedUnits);
    }

    @Test
    void savesAndLoadsMandatoryDishType() {
        Recipe sideRecipe = new Recipe("Garlic bread", 2,
                List.of(new RecipeIngredient(pasta, BigDecimal.ONE, Unit.SLICE)),
                List.of(), List.of(savory), DishType.SIDE);
        Recipe mainRecipe = recipeNamed("Pasta");

        recipeRepository.save(sideRecipe);
        recipeRepository.save(mainRecipe);

        assertEquals(DishType.SIDE, recipeRepository.findById(sideRecipe.getId())
                .orElseThrow().getDishType());
        assertEquals(DishType.MAIN, recipeRepository.findById(mainRecipe.getId())
                .orElseThrow().getDishType());
    }

    @Test
    void savesAndLoadsOptionalTimesAndDerivesTotalTime() {
        Recipe withTimes = new Recipe("Pasta", 2,
                List.of(new RecipeIngredient(pasta, BigDecimal.ONE, Unit.PIECE)),
                List.of(), List.of(savory), 15, 25, 30, null, DishType.MAIN);
        Recipe withoutTimes = new Recipe("Plain pasta", 2,
                List.of(new RecipeIngredient(pasta, BigDecimal.ONE, Unit.PIECE)),
                List.of(), List.of(savory));

        recipeRepository.save(withTimes);
        recipeRepository.save(withoutTimes);

        Recipe loadedWithTimes = recipeRepository.findById(withTimes.getId()).orElseThrow();
        Recipe loadedWithoutTimes = recipeRepository.findById(withoutTimes.getId()).orElseThrow();
        assertEquals(15, loadedWithTimes.getPreparationTimeMinutes().orElseThrow());
        assertEquals(25, loadedWithTimes.getCookingTimeMinutes().orElseThrow());
        assertEquals(30, loadedWithTimes.getBakingTimeMinutes().orElseThrow());
        assertEquals(70, loadedWithTimes.getTotalTimeMinutes().orElseThrow());
        assertTrue(loadedWithoutTimes.getPreparationTimeMinutes().isEmpty());
        assertTrue(loadedWithoutTimes.getCookingTimeMinutes().isEmpty());
        assertTrue(loadedWithoutTimes.getBakingTimeMinutes().isEmpty());
        assertTrue(loadedWithoutTimes.getTotalTimeMinutes().isEmpty());
        assertTrue(loadedWithoutTimes.getNutritionInfo().isEmpty());
    }

    @Test
    void roundTripsOrderedGroupsOptionsDefaultsAndStableUuids() {
        UUID firstGroupId = UUID.randomUUID();
        UUID secondGroupId = UUID.randomUUID();
        RecipeIngredientOption pastaOption = new RecipeIngredientOption(UUID.randomUUID(), pasta,
                new BigDecimal("0.75"), Unit.KILOGRAM, 1);
        RecipeIngredientOption saltOption = new RecipeIngredientOption(UUID.randomUUID(), salt,
                new BigDecimal("1.5"), Unit.TEASPOON, 0);
        RecipeIngredientOption secondSaltOption = new RecipeIngredientOption(
                UUID.randomUUID(), salt, new BigDecimal("2"), Unit.PINCH, 0);
        RecipeIngredientGroup alternatives = new RecipeIngredientGroup(firstGroupId,
                List.of(pastaOption, saltOption), pastaOption);
        RecipeIngredientGroup seasoning = new RecipeIngredientGroup(secondGroupId,
                List.of(secondSaltOption), secondSaltOption);
        Recipe recipe = Recipe.withIngredientGroups("Flexible pasta", 2,
                List.of(seasoning, alternatives), List.of(), List.of(savory), DishType.MAIN);

        recipeRepository.save(recipe);
        Recipe loaded = recipeRepository.findById(recipe.getId()).orElseThrow();

        assertEquals(List.of(secondGroupId, firstGroupId), loaded.getIngredientGroups().stream()
                .map(RecipeIngredientGroup::getId).toList());
        RecipeIngredientGroup loadedAlternatives = loaded.getIngredientGroups().get(1);
        assertEquals(List.of(saltOption.getId(), pastaOption.getId()),
                loadedAlternatives.getOptions().stream()
                        .map(RecipeIngredientOption::getId).toList());
        assertEquals(pastaOption.getId(), loadedAlternatives.getStandardOptionId());
        assertEquals(new BigDecimal("0.75"),
                loadedAlternatives.getStandardOption().getQuantity());
        assertEquals(Unit.KILOGRAM, loadedAlternatives.getStandardOption().getUnit());
        assertEquals(new BigDecimal("1.5"),
                loadedAlternatives.getOptions().getFirst().getQuantity());
        assertEquals(Unit.TEASPOON, loadedAlternatives.getOptions().getFirst().getUnit());
    }

    @Test
    void updateAddsRemovesReordersGroupsAndOptionsAndChangesDefaultWithoutOrphans()
            throws Exception {
        RecipeIngredientOption first = option(pasta, "500", Unit.GRAM, 0);
        RecipeIngredientOption retained = option(salt, "1", Unit.TEASPOON, 1);
        RecipeIngredientGroup retainedGroup = new RecipeIngredientGroup(
                List.of(first, retained), first);
        RecipeIngredientOption removedGroupOption = option(pasta, "1", Unit.PIECE, 0);
        RecipeIngredientGroup removedGroup = new RecipeIngredientGroup(
                List.of(removedGroupOption), removedGroupOption);
        Recipe original = Recipe.withIngredientGroups("Original", 2,
                List.of(retainedGroup, removedGroup), List.of(), List.of(savory), DishType.MAIN);
        recipeRepository.save(original);

        RecipeIngredientOption reorderedRetained = new RecipeIngredientOption(retained.getId(),
                salt, new BigDecimal("2"), Unit.PINCH, 0);
        RecipeIngredientOption addedOption = option(pasta, "0.25", Unit.KILOGRAM, 1);
        RecipeIngredientGroup changedGroup = new RecipeIngredientGroup(retainedGroup.getId(),
                List.of(addedOption, reorderedRetained), addedOption);
        RecipeIngredientOption addedGroupOption = option(salt, "3", Unit.CLOVE, 0);
        RecipeIngredientGroup addedGroup = new RecipeIngredientGroup(
                List.of(addedGroupOption), addedGroupOption);
        Recipe updated = Recipe.withIngredientGroups(original.getId(), "Updated", 2,
                List.of(addedGroup, changedGroup), List.of(), List.of(savory),
                null, null, null, null, DishType.MAIN);

        recipeRepository.save(updated);
        Recipe loaded = recipeRepository.findById(original.getId()).orElseThrow();

        assertEquals(List.of(addedGroup.getId(), retainedGroup.getId()),
                loaded.getIngredientGroups().stream().map(RecipeIngredientGroup::getId).toList());
        RecipeIngredientGroup loadedChanged = loaded.getIngredientGroups().get(1);
        assertEquals(List.of(retained.getId(), addedOption.getId()),
                loadedChanged.getOptions().stream().map(RecipeIngredientOption::getId).toList());
        assertEquals(addedOption.getId(), loadedChanged.getStandardOptionId());
        assertFalse(loaded.getIngredientGroups().stream()
                .anyMatch(group -> group.getId().equals(removedGroup.getId())));
        assertEquals(2, rowCount("recipe_ingredient_groups"));
        assertEquals(3, rowCount("recipe_ingredient_options"));
        assertEquals(0, orphanGroupCount());
        assertEquals(0, orphanOptionCount());
    }

    @Test
    void failedGroupUpdateRollsBackRecipeAndAllRelationshipChanges() throws Exception {
        Recipe original = recipeNamed("Original");
        recipeRepository.save(original);
        UUID originalGroupId = original.getIngredientGroups().getFirst().getId();
        Ingredient missing = new Ingredient("Missing");
        RecipeIngredientOption invalidOption = option(missing, "1", Unit.PIECE, 0);
        RecipeIngredientGroup invalidGroup = new RecipeIngredientGroup(
                List.of(invalidOption), invalidOption);
        Recipe invalidUpdate = Recipe.withIngredientGroups(original.getId(), "Changed", 2,
                List.of(invalidGroup), List.of(), List.of(savory),
                null, null, null, null, DishType.MAIN);

        assertThrows(PersistenceException.class, () -> recipeRepository.save(invalidUpdate));

        Recipe loaded = recipeRepository.findById(original.getId()).orElseThrow();
        assertEquals("Original", loaded.getName());
        assertEquals(originalGroupId, loaded.getIngredientGroups().getFirst().getId());
        assertEquals(1, rowCount("recipe_ingredient_groups"));
        assertEquals(1, rowCount("recipe_ingredient_options"));
        assertEquals(0, orphanGroupCount());
        assertEquals(0, orphanOptionCount());
    }

    @Test
    void savesAndLoadsPartialAndCompleteNutritionInfo() {
        Recipe partial = new Recipe("Partial", 2,
                List.of(new RecipeIngredient(pasta, BigDecimal.ONE, Unit.PIECE)),
                List.of(), List.of(savory), null, null,
                new de.mealdeal.domain.NutritionInfo(0, null, new BigDecimal("71.5"), null));
        Recipe complete = new Recipe("Complete", 2,
                List.of(new RecipeIngredient(pasta, BigDecimal.ONE, Unit.PIECE)),
                List.of(), List.of(savory), null, null,
                new de.mealdeal.domain.NutritionInfo(650, new BigDecimal("42"),
                        new BigDecimal("71.5"), new BigDecimal("18")));

        recipeRepository.save(partial);
        recipeRepository.save(complete);

        var loadedPartial = recipeRepository.findById(partial.getId()).orElseThrow()
                .getNutritionInfo().orElseThrow();
        var loadedComplete = recipeRepository.findById(complete.getId()).orElseThrow()
                .getNutritionInfo().orElseThrow();
        assertEquals(0, loadedPartial.getCaloriesKcal().orElseThrow());
        assertTrue(loadedPartial.getProteinGrams().isEmpty());
        assertEquals(new BigDecimal("71.5"), loadedPartial.getCarbohydrateGrams().orElseThrow());
        assertEquals(650, loadedComplete.getCaloriesKcal().orElseThrow());
        assertEquals(new BigDecimal("18"), loadedComplete.getFatGrams().orElseThrow());
    }

    @Test
    void updatesRecipeAndReplacesAllDependentRows() {
        UUID recipeId = UUID.randomUUID();
        Recipe original = new Recipe(recipeId, "Pasta", 2,
                List.of(new RecipeIngredient(pasta, new BigDecimal("500"), Unit.GRAM)),
                List.of(new RecipeStep(1, "Cook.")), List.of(savory));
        recipeRepository.save(original);

        Recipe updated = new Recipe(recipeId, "Salt pasta", 4,
                List.of(new RecipeIngredient(salt, new BigDecimal("1.5"), Unit.TEASPOON)),
                List.of(new RecipeStep(1, "Boil."), new RecipeStep(2, "Season.")),
                List.of(mild));
        recipeRepository.save(updated);

        Recipe loaded = recipeRepository.findById(recipeId).orElseThrow();
        assertEquals("Salt pasta", loaded.getName());
        assertEquals(4, loaded.getStandardServingCount());
        assertEquals(List.of(salt), loaded.getIngredients().stream()
                .map(RecipeIngredient::getIngredient).toList());
        assertEquals(new BigDecimal("1.5"), loaded.getIngredients().getFirst().getQuantity());
        assertEquals(List.of("Boil.", "Season."), loaded.getSteps().stream()
                .map(RecipeStep::getDescription).toList());
        assertEquals(List.of(mild), loaded.getTastes());
    }

    @Test
    void loadsAllRecipesAndReportsUnknownId() {
        recipeRepository.save(recipeNamed("Ziti"));
        recipeRepository.save(recipeNamed("Alfredo"));

        assertEquals(List.of("Alfredo", "Ziti"), recipeRepository.findAll().stream()
                .map(Recipe::getName).toList());
        assertTrue(recipeRepository.findById(UUID.randomUUID()).isEmpty());
    }

    @Test
    void deletesRecipeRelationshipsButKeepsCentralData() {
        Recipe recipe = recipeNamed("Pasta");
        recipeRepository.save(recipe);

        assertTrue(recipeRepository.deleteById(recipe.getId()));
        assertFalse(recipeRepository.findById(recipe.getId()).isPresent());
        assertTrue(ingredientRepository.findById(pasta.getId()).isPresent());
        assertTrue(tasteRepository.findById(savory.getId()).isPresent());
        assertFalse(recipeRepository.deleteById(UUID.randomUUID()));
    }

    @Test
    void rollsBackEntireSaveWhenIngredientDoesNotExist() {
        Ingredient missingIngredient = new Ingredient("Missing");
        Recipe recipe = new Recipe("Invalid reference",
                List.of(new RecipeIngredient(missingIngredient, BigDecimal.ONE, Unit.PIECE)),
                List.of(new RecipeStep(1, "Fail.")), List.of(savory));

        assertThrows(PersistenceException.class, () -> recipeRepository.save(recipe));

        assertTrue(recipeRepository.findById(recipe.getId()).isEmpty());
    }

    private Recipe recipeNamed(String name) {
        return new Recipe(name,
                List.of(new RecipeIngredient(pasta, new BigDecimal("500"), Unit.GRAM)),
                List.of(new RecipeStep(1, "Cook.")), List.of(savory));
    }

    private static RecipeIngredientOption option(Ingredient ingredient, String quantity,
                                                 Unit unit, int position) {
        return new RecipeIngredientOption(ingredient, new BigDecimal(quantity), unit, position);
    }

    private int rowCount(String table) throws Exception {
        try (var connection = database.openConnection();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT count(*) FROM " + table)) {
            return resultSet.getInt(1);
        }
    }

    private int orphanOptionCount() throws Exception {
        try (var connection = database.openConnection();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("""
                     SELECT count(*)
                     FROM recipe_ingredient_options option
                     LEFT JOIN recipe_ingredient_groups ingredient_group
                         ON ingredient_group.id = option.group_id
                     WHERE ingredient_group.id IS NULL
                     """)) {
            return resultSet.getInt(1);
        }
    }

    private int orphanGroupCount() throws Exception {
        try (var connection = database.openConnection();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("""
                     SELECT count(*)
                     FROM recipe_ingredient_groups ingredient_group
                     LEFT JOIN recipes recipe ON recipe.id = ingredient_group.recipe_id
                     WHERE recipe.id IS NULL
                     """)) {
            return resultSet.getInt(1);
        }
    }
}
