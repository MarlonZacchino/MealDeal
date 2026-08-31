package de.mealdeal.ui.form;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.repository.IngredientRepository;
import de.mealdeal.persistence.repository.RecipeRepository;
import de.mealdeal.persistence.repository.TasteRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeFormServiceTest {

    @Test
    void exposesDefaultServingCountOfTwo() {
        assertEquals("2", RecipeFormService.DEFAULT_SERVING_COUNT);
    }

    @Test
    void createsValidRecipeAndReusesCentralData() {
        MemoryIngredientRepository ingredients = new MemoryIngredientRepository();
        MemoryTasteRepository tastes = new MemoryTasteRepository();
        MemoryRecipeRepository recipes = new MemoryRecipeRepository();
        Ingredient pasta = new Ingredient("Pasta");
        Taste savory = new Taste("Herzhaft");
        ingredients.save(pasta);
        tastes.save(savory);
        RecipeFormService service = new RecipeFormService(recipes, ingredients, tastes);

        Recipe recipe = service.createAndSave(validInput("pasta", "herzhaft"));

        assertEquals("Pasta mit Sauce", recipe.getName());
        assertEquals(2, recipe.getStandardServingCount());
        assertEquals(pasta, recipe.getIngredients().getFirst().getIngredient());
        assertEquals(new BigDecimal("1.5"), recipe.getIngredients().getFirst().getQuantity());
        assertEquals(Unit.KILOGRAM, recipe.getIngredients().getFirst().getUnit());
        assertEquals(List.of(1, 2), recipe.getSteps().stream()
                .map(step -> step.getPosition()).toList());
        assertEquals(savory, recipe.getTastes().getFirst());
        assertEquals(recipe, recipes.savedRecipes.getFirst());
        assertEquals(1, ingredients.values.size());
        assertEquals(1, tastes.values.size());
    }

    @Test
    void savesNewCentralDataBeforeRecipe() {
        List<String> events = new ArrayList<>();
        MemoryIngredientRepository ingredients = new MemoryIngredientRepository(events);
        MemoryTasteRepository tastes = new MemoryTasteRepository(events);
        MemoryRecipeRepository recipes = new MemoryRecipeRepository(events, false);
        RecipeFormService service = new RecipeFormService(recipes, ingredients, tastes);

        service.createAndSave(validInput("Pasta", "Cremig"));

        assertEquals(List.of("ingredient:Pasta", "taste:Cremig", "recipe:Pasta mit Sauce"),
                events);
    }

    @Test
    void createsRecipeWithoutPreparationSteps() {
        RecipeFormService service = new RecipeFormService(
                new MemoryRecipeRepository(), new MemoryIngredientRepository(),
                new MemoryTasteRepository());
        RecipeFormInput input = new RecipeFormInput("Brotzeit", "2",
                List.of(new IngredientFormInput("Brot", "2", Unit.PIECE)),
                List.of("Herzhaft"), List.of("  "));

        Recipe recipe = service.createAndSave(input);

        assertTrue(recipe.getSteps().isEmpty());
    }

    @Test
    void createsRecipeWithOnlyPreparationTime() {
        RecipeFormService service = new RecipeFormService(
                new MemoryRecipeRepository(), new MemoryIngredientRepository(),
                new MemoryTasteRepository());
        RecipeFormInput input = new RecipeFormInput("Brotzeit", "2",
                List.of(new IngredientFormInput("Brot", "2", Unit.PIECE)),
                List.of("Herzhaft"), List.of(), "15", "");

        Recipe recipe = service.createAndSave(input);

        assertEquals(15, recipe.getPreparationTimeMinutes().orElseThrow());
        assertTrue(recipe.getCookingTimeMinutes().isEmpty());
        assertEquals(15, recipe.getTotalTimeMinutes().orElseThrow());
    }

    @Test
    void createsRecipeWithOnlyCookingTime() {
        RecipeFormService service = new RecipeFormService(
                new MemoryRecipeRepository(), new MemoryIngredientRepository(),
                new MemoryTasteRepository());

        Recipe recipe = service.createAndSave(new RecipeFormInput("Brotzeit", "2",
                List.of(new IngredientFormInput("Brot", "2", Unit.PIECE)),
                List.of("Herzhaft"), List.of(), "", "45"));

        assertTrue(recipe.getPreparationTimeMinutes().isEmpty());
        assertEquals(45, recipe.getCookingTimeMinutes().orElseThrow());
        assertEquals(45, recipe.getTotalTimeMinutes().orElseThrow());
    }

    @Test
    void updatesAllRecipeValuesAndKeepsUuid() {
        MemoryIngredientRepository ingredients = new MemoryIngredientRepository();
        MemoryTasteRepository tastes = new MemoryTasteRepository();
        MemoryRecipeRepository recipes = new MemoryRecipeRepository();
        Ingredient pasta = new Ingredient("Pasta");
        Taste savory = new Taste("Herzhaft");
        ingredients.save(pasta);
        tastes.save(savory);
        RecipeFormService service = new RecipeFormService(recipes, ingredients, tastes);
        UUID recipeId = UUID.fromString("00000000-0000-0000-0000-000000000123");

        Recipe updated = service.updateAndSave(recipeId, new RecipeFormInput(
                "Pasta al Limone", "4",
                List.of(new IngredientFormInput("pasta", "750,25", Unit.GRAM)),
                List.of("herzhaft", "Frisch"),
                List.of("Kochen.", "Mit Zitrone abschmecken.", "Servieren.")));

        assertEquals(recipeId, updated.getId());
        assertEquals("Pasta al Limone", updated.getName());
        assertEquals(4, updated.getStandardServingCount());
        assertEquals(pasta, updated.getIngredients().getFirst().getIngredient());
        assertEquals(new BigDecimal("750.25"),
                updated.getIngredients().getFirst().getQuantity());
        assertEquals(Unit.GRAM, updated.getIngredients().getFirst().getUnit());
        assertEquals(List.of("Herzhaft", "Frisch"), updated.getTastes().stream()
                .map(Taste::getName).toList());
        assertEquals(List.of("Kochen.", "Mit Zitrone abschmecken.", "Servieren."),
                updated.getSteps().stream().map(step -> step.getDescription()).toList());
        assertEquals(updated, recipes.savedRecipes.getFirst());
    }

    @Test
    void updatesTimesAndDerivesTotalTime() {
        RecipeFormService service = new RecipeFormService(
                new MemoryRecipeRepository(), new MemoryIngredientRepository(),
                new MemoryTasteRepository());
        UUID recipeId = UUID.randomUUID();

        Recipe updated = service.updateAndSave(recipeId, new RecipeFormInput(
                "Toast", "2", List.of(new IngredientFormInput("Brot", "2", Unit.SLICE)),
                List.of("Herzhaft"), List.of(), "10", "20"));

        assertEquals(10, updated.getPreparationTimeMinutes().orElseThrow());
        assertEquals(20, updated.getCookingTimeMinutes().orElseThrow());
        assertEquals(30, updated.getTotalTimeMinutes().orElseThrow());
    }

    @Test
    void createsAndUpdatesNutritionValuesPerServing() {
        RecipeFormService service = new RecipeFormService(
                new MemoryRecipeRepository(), new MemoryIngredientRepository(),
                new MemoryTasteRepository());
        Recipe created = service.createAndSave(new RecipeFormInput(
                "Toast", "2", List.of(new IngredientFormInput("Brot", "2", Unit.SLICE)),
                List.of("Herzhaft"), List.of(), "", "", "650", "42", "71,5", "18"));

        var nutrition = created.getNutritionInfo().orElseThrow();
        assertEquals(650, nutrition.getCaloriesKcal().orElseThrow());
        assertEquals(new BigDecimal("71.5"), nutrition.getCarbohydrateGrams().orElseThrow());

        Recipe updated = service.updateAndSave(created.getId(), new RecipeFormInput(
                "Toast", "2", List.of(new IngredientFormInput("Brot", "2", Unit.SLICE)),
                List.of("Herzhaft"), List.of(), "", "", "0", "", "", "0"));
        assertEquals(0, updated.getNutritionInfo().orElseThrow()
                .getCaloriesKcal().orElseThrow());
        assertEquals(BigDecimal.ZERO, updated.getNutritionInfo().orElseThrow()
                .getFatGrams().orElseThrow());
    }

    @Test
    void rejectsNegativeNutritionValues() {
        RecipeFormService service = new RecipeFormService(
                new MemoryRecipeRepository(), new MemoryIngredientRepository(),
                new MemoryTasteRepository());
        RecipeFormInput input = new RecipeFormInput("Toast", "2",
                List.of(new IngredientFormInput("Brot", "2", Unit.SLICE)),
                List.of("Herzhaft"), List.of(), "", "", "-1", "-0,5", "", "");

        RecipeFormValidationException exception = assertThrows(
                RecipeFormValidationException.class, () -> service.createAndSave(input));

        assertEquals(2, exception.getErrors().stream()
                .filter(error -> error.contains("nichtnegative")).count());
    }

    @Test
    void rejectsInvalidOptionalTimeValues() {
        RecipeFormService service = new RecipeFormService(
                new MemoryRecipeRepository(), new MemoryIngredientRepository(),
                new MemoryTasteRepository());
        RecipeFormInput input = new RecipeFormInput("Toast", "2",
                List.of(new IngredientFormInput("Brot", "2", Unit.SLICE)),
                List.of("Herzhaft"), List.of(), "0", "-2");

        RecipeFormValidationException exception = assertThrows(
                RecipeFormValidationException.class, () -> service.createAndSave(input));

        assertEquals(2, exception.getErrors().stream()
                .filter(error -> error.contains("zeit")).count());
    }

    @Test
    void reportsAllImportantValidationErrorsBeforePersistence() {
        List<String> events = new ArrayList<>();
        RecipeFormService service = new RecipeFormService(
                new MemoryRecipeRepository(events, false),
                new MemoryIngredientRepository(events),
                new MemoryTasteRepository(events));
        RecipeFormInput invalid = new RecipeFormInput(" ", "0",
                List.of(new IngredientFormInput("", "-2", null)),
                List.of(), List.of(" "));

        RecipeFormValidationException exception = assertThrows(
                RecipeFormValidationException.class, () -> service.createAndSave(invalid));

        assertTrue(exception.getErrors().stream().anyMatch(error -> error.contains("Namen")));
        assertTrue(exception.getErrors().stream().anyMatch(error -> error.contains("Personenanzahl")));
        assertTrue(exception.getErrors().stream().anyMatch(error -> error.contains("Menge")));
        assertTrue(exception.getErrors().stream().anyMatch(error -> error.contains("Einheit")));
        assertTrue(exception.getErrors().stream().anyMatch(error -> error.contains("Geschmacksrichtung")));
        assertFalse(exception.getErrors().stream().anyMatch(error -> error.contains("Schritt")));
        assertTrue(events.isEmpty());
    }

    @Test
    void rejectsDuplicateIngredientNamesIgnoringCase() {
        RecipeFormService service = new RecipeFormService(
                new MemoryRecipeRepository(), new MemoryIngredientRepository(),
                new MemoryTasteRepository());
        RecipeFormInput input = new RecipeFormInput("Doppelt", "2",
                List.of(
                        new IngredientFormInput("Salz", "1", Unit.PINCH),
                        new IngredientFormInput(" salz ", "2", Unit.PINCH)),
                List.of("Herzhaft"), List.of("Würzen."));

        RecipeFormValidationException exception = assertThrows(
                RecipeFormValidationException.class, () -> service.createAndSave(input));

        assertTrue(exception.getErrors().stream().anyMatch(error -> error.contains("bereits")));
    }

    @Test
    void keepsNewCentralDataWhenRecipeSaveFails() {
        MemoryIngredientRepository ingredients = new MemoryIngredientRepository();
        MemoryTasteRepository tastes = new MemoryTasteRepository();
        RecipeFormService service = new RecipeFormService(
                new MemoryRecipeRepository(List.of(), true), ingredients, tastes);

        assertThrows(PersistenceException.class,
                () -> service.createAndSave(validInput("Pasta", "Cremig")));

        assertEquals(List.of("Pasta"), ingredients.values.stream()
                .map(Ingredient::getName).toList());
        assertEquals(List.of("Cremig"), tastes.values.stream().map(Taste::getName).toList());
    }

    private static RecipeFormInput validInput(String ingredientName, String tasteName) {
        return new RecipeFormInput("Pasta mit Sauce", "2",
                List.of(new IngredientFormInput(ingredientName, "1,5", Unit.KILOGRAM)),
                List.of(tasteName), List.of("Kochen.", "Servieren."));
    }

    private static final class MemoryIngredientRepository implements IngredientRepository {
        private final List<Ingredient> values = new ArrayList<>();
        private final List<String> events;

        private MemoryIngredientRepository() {
            events = null;
        }

        private MemoryIngredientRepository(List<String> events) {
            this.events = events;
        }

        @Override
        public void save(Ingredient ingredient) {
            values.remove(ingredient);
            values.add(ingredient);
            if (events != null) {
                events.add("ingredient:" + ingredient.getName());
            }
        }

        @Override
        public Optional<Ingredient> findById(UUID id) {
            return values.stream().filter(value -> value.getId().equals(id)).findFirst();
        }

        @Override
        public List<Ingredient> findAll() {
            return List.copyOf(values);
        }

        @Override
        public boolean deleteById(UUID id) {
            return values.removeIf(value -> value.getId().equals(id));
        }
    }

    private static final class MemoryTasteRepository implements TasteRepository {
        private final List<Taste> values = new ArrayList<>();
        private final List<String> events;

        private MemoryTasteRepository() {
            events = null;
        }

        private MemoryTasteRepository(List<String> events) {
            this.events = events;
        }

        @Override
        public void save(Taste taste) {
            values.remove(taste);
            values.add(taste);
            if (events != null) {
                events.add("taste:" + taste.getName());
            }
        }

        @Override
        public Optional<Taste> findById(UUID id) {
            return values.stream().filter(value -> value.getId().equals(id)).findFirst();
        }

        @Override
        public List<Taste> findAll() {
            return List.copyOf(values);
        }

        @Override
        public boolean deleteById(UUID id) {
            return values.removeIf(value -> value.getId().equals(id));
        }
    }

    private static final class MemoryRecipeRepository implements RecipeRepository {
        private final List<Recipe> savedRecipes = new ArrayList<>();
        private final List<String> events;
        private final boolean failOnSave;

        private MemoryRecipeRepository() {
            this(null, false);
        }

        private MemoryRecipeRepository(List<String> events, boolean failOnSave) {
            this.events = events;
            this.failOnSave = failOnSave;
        }

        @Override
        public void save(Recipe recipe) {
            if (failOnSave) {
                throw new PersistenceException("Recipe save failed.");
            }
            savedRecipes.add(recipe);
            if (events != null) {
                events.add("recipe:" + recipe.getName());
            }
        }

        @Override
        public Optional<Recipe> findById(UUID id) {
            return savedRecipes.stream().filter(value -> value.getId().equals(id)).findFirst();
        }

        @Override
        public List<Recipe> findAll() {
            return List.copyOf(savedRecipes);
        }

        @Override
        public boolean deleteById(UUID id) {
            return savedRecipes.removeIf(value -> value.getId().equals(id));
        }
    }
}
