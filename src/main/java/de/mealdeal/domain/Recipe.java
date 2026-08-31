package de.mealdeal.domain;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
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

    private final UUID id;
    private final String name;
    private final int standardServingCount;
    private final List<RecipeIngredient> ingredients;
    private final List<RecipeStep> steps;
    private final List<Taste> tastes;
    private final OptionalInt preparationTimeMinutes;
    private final OptionalInt cookingTimeMinutes;
    private final OptionalInt totalTimeMinutes;

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
        this(UUID.randomUUID(), name, DEFAULT_SERVING_COUNT, ingredients, steps, tastes,
                null, null);
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
        this(UUID.randomUUID(), name, standardServingCount, ingredients, steps, tastes,
                null, null);
    }

    /**
     * Creates a new recipe with optional preparation and cooking times.
     *
     * @param preparationTimeMinutes optional positive preparation time in minutes
     * @param cookingTimeMinutes optional positive cooking time in minutes
     */
    public Recipe(String name, int standardServingCount,
                  List<RecipeIngredient> ingredients, List<RecipeStep> steps,
                  List<Taste> tastes, Integer preparationTimeMinutes,
                  Integer cookingTimeMinutes) {
        this(UUID.randomUUID(), name, standardServingCount, ingredients, steps, tastes,
                preparationTimeMinutes, cookingTimeMinutes);
    }

    /**
     * Recreates a recipe with an existing technical identity.
     *
     * @param id the stable, persistence-independent identity
     * @param name the recipe name
     * @param standardServingCount positive number of standard servings
     * @param ingredients ingredients with their quantities and units
     * @param steps individually positioned preparation steps
     * @param tastes one or more tastes assigned to the recipe
     */
    public Recipe(UUID id, String name, int standardServingCount,
                  List<RecipeIngredient> ingredients, List<RecipeStep> steps,
                  List<Taste> tastes) {
        this(id, name, standardServingCount, ingredients, steps, tastes, null, null);
    }

    /**
     * Creates or recreates a recipe with optional preparation and cooking times.
     *
     * @param preparationTimeMinutes optional positive preparation time in minutes
     * @param cookingTimeMinutes optional positive cooking time in minutes
     */
    public Recipe(UUID id, String name, int standardServingCount,
                  List<RecipeIngredient> ingredients, List<RecipeStep> steps,
                  List<Taste> tastes, Integer preparationTimeMinutes,
                  Integer cookingTimeMinutes) {
        this.id = java.util.Objects.requireNonNull(id, "Recipe ID must not be null.");
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
        this.preparationTimeMinutes = optionalTime(preparationTimeMinutes, "Preparation time");
        this.cookingTimeMinutes = optionalTime(cookingTimeMinutes, "Cooking time");
        this.totalTimeMinutes = deriveTotalTime(
                this.preparationTimeMinutes, this.cookingTimeMinutes);

        if (this.tastes.isEmpty()) {
            throw new IllegalArgumentException("Recipe must have at least one taste.");
        }

        rejectDuplicateIngredientIds(this.ingredients);
        rejectDuplicateStepPositions(this.steps);
        rejectDuplicateTasteIds(this.tastes);
    }

    public UUID getId() {
        return id;
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

    /** Returns the optional preparation time in minutes. */
    public OptionalInt getPreparationTimeMinutes() {
        return preparationTimeMinutes;
    }

    /** Returns the optional cooking time in minutes. */
    public OptionalInt getCookingTimeMinutes() {
        return cookingTimeMinutes;
    }

    /** Returns the optional total time derived from preparation and cooking time. */
    public OptionalInt getTotalTimeMinutes() {
        return totalTimeMinutes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Recipe recipe)) {
            return false;
        }
        return id.equals(recipe.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
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

    private static OptionalInt optionalTime(Integer minutes, String fieldName) {
        if (minutes == null) {
            return OptionalInt.empty();
        }
        if (minutes <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero.");
        }
        return OptionalInt.of(minutes);
    }

    private static OptionalInt deriveTotalTime(OptionalInt preparationTime,
                                               OptionalInt cookingTime) {
        if (preparationTime.isEmpty()) {
            return cookingTime;
        }
        if (cookingTime.isEmpty()) {
            return preparationTime;
        }
        try {
            return OptionalInt.of(Math.addExact(
                    preparationTime.getAsInt(), cookingTime.getAsInt()));
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Preparation and cooking time must fit into a positive minute value.",
                    exception);
        }
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
