package de.mealdeal.domain;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The aggregate containing a recipe's basic data, ingredients, ordered steps,
 * and tastes.
 *
 * <p>Defensive copies prevent callers from modifying the recipe through list
 * references. The class contains only normal Java types and therefore remains
 * independent of JavaFX, JDBC, and SQLite.</p>
 */
public final class Recipe {

    public static final int DEFAULT_SERVING_COUNT = 2;

    private final String name;
    private final int standardServingCount;
    private final List<RecipeIngredient> ingredients;
    private final List<RecipeStep> steps;
    private final List<Taste> tastes;

    /**
     * Creates a recipe for the default serving count of two.
     *
     * @param name the recipe name
     * @param ingredients ingredients with their quantities and units
     * @param steps individually positioned preparation steps
     * @param tastes one or more tastes assigned to the recipe
     */
    public Recipe(String name, List<RecipeIngredient> ingredients,
                  List<RecipeStep> steps, List<Taste> tastes) {
        this(name, DEFAULT_SERVING_COUNT, ingredients, steps, tastes);
    }

    /**
     * Creates a recipe with an explicit standard serving count.
     *
     * @param name the recipe name
     * @param standardServingCount positive number of standard servings
     * @param ingredients ingredients with their quantities and units
     * @param steps individually positioned preparation steps
     * @param tastes one or more tastes assigned to the recipe
     */
    public Recipe(String name, int standardServingCount,
                  List<RecipeIngredient> ingredients, List<RecipeStep> steps,
                  List<Taste> tastes) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Recipe name must not be blank.");
        }
        if (standardServingCount <= 0) {
            throw new IllegalArgumentException("Standard serving count must be greater than zero.");
        }

        this.name = name.strip();
        this.standardServingCount = standardServingCount;
        this.ingredients = copyWithoutNulls(ingredients, "Recipe ingredients");
        this.steps = copyWithoutNulls(steps, "Recipe steps").stream()
                .sorted(Comparator.comparingInt(RecipeStep::getPosition))
                .toList();
        this.tastes = copyWithoutNulls(tastes, "Recipe tastes");

        if (this.tastes.isEmpty()) {
            throw new IllegalArgumentException("Recipe must have at least one taste.");
        }

        rejectDuplicateIngredientIds(this.ingredients);
        rejectDuplicateStepPositions(this.steps);
        rejectDuplicateTasteIds(this.tastes);
    }

    public String getName() {
        return name;
    }

    public int getStandardServingCount() {
        return standardServingCount;
    }

    public List<RecipeIngredient> getIngredients() {
        return ingredients;
    }

    public List<RecipeStep> getSteps() {
        return steps;
    }

    public List<Taste> getTastes() {
        return tastes;
    }

    private static <T> List<T> copyWithoutNulls(List<T> values, String fieldName) {
        if (values == null) {
            throw new IllegalArgumentException(fieldName + " must not be null.");
        }
        if (values.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException(fieldName + " must not contain null values.");
        }
        return List.copyOf(values);
    }

    private static void rejectDuplicateIngredientIds(List<RecipeIngredient> ingredients) {
        Set<UUID> ingredientIds = new HashSet<>();
        for (RecipeIngredient recipeIngredient : ingredients) {
            if (!ingredientIds.add(recipeIngredient.getIngredient().getId())) {
                throw new IllegalArgumentException(
                        "Recipe must not contain the same ingredient identity more than once.");
            }
        }
    }

    private static void rejectDuplicateStepPositions(List<RecipeStep> steps) {
        Set<Integer> positions = new HashSet<>();
        for (RecipeStep step : steps) {
            if (!positions.add(step.getPosition())) {
                throw new IllegalArgumentException(
                        "Recipe step positions must be unique.");
            }
        }
    }

    private static void rejectDuplicateTasteIds(List<Taste> tastes) {
        Set<UUID> tasteIds = new HashSet<>();
        for (Taste taste : tastes) {
            if (!tasteIds.add(taste.getId())) {
                throw new IllegalArgumentException(
                        "Recipe must not contain the same taste identity more than once.");
            }
        }
    }
}
