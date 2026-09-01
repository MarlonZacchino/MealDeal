package de.mealdeal.domain;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * The aggregate containing a recipe's basic data, ingredient groups, ordered steps,
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
    private final List<RecipeIngredientGroup> ingredientGroups;
    private final List<RecipeStep> steps;
    private final List<Taste> tastes;
    private final OptionalInt preparationTimeMinutes;
    private final OptionalInt cookingTimeMinutes;
    private final OptionalInt bakingTimeMinutes;
    private final OptionalInt totalTimeMinutes;
    private final Optional<NutritionInfo> nutritionInfo;
    private final DishType dishType;

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
                null, null, null, DishType.MAIN);
    }

    /** Creates a recipe for the default serving count with its mandatory dish type. */
    public Recipe(String name, List<RecipeIngredient> ingredients,
                  List<RecipeStep> steps, List<Taste> tastes, DishType dishType) {
        this(UUID.randomUUID(), name, DEFAULT_SERVING_COUNT, ingredients, steps, tastes,
                null, null, null, dishType);
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
                null, null, null, DishType.MAIN);
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
                preparationTimeMinutes, cookingTimeMinutes, null, DishType.MAIN);
    }

    /** Creates a recipe with optional times and per-serving nutrition information. */
    public Recipe(String name, int standardServingCount,
                  List<RecipeIngredient> ingredients, List<RecipeStep> steps,
                  List<Taste> tastes, Integer preparationTimeMinutes,
                  Integer cookingTimeMinutes, NutritionInfo nutritionInfo) {
        this(UUID.randomUUID(), name, standardServingCount, ingredients, steps, tastes,
                preparationTimeMinutes, cookingTimeMinutes, nutritionInfo, DishType.MAIN);
    }

    /** Creates a recipe with optional data and its mandatory dish type. */
    public Recipe(String name, int standardServingCount,
                  List<RecipeIngredient> ingredients, List<RecipeStep> steps,
                  List<Taste> tastes, Integer preparationTimeMinutes,
                  Integer cookingTimeMinutes, NutritionInfo nutritionInfo, DishType dishType) {
        this(name, standardServingCount, ingredients, steps, tastes,
                preparationTimeMinutes, cookingTimeMinutes, null, nutritionInfo, dishType);
    }

    /** Creates a recipe with all optional times, nutrition and its mandatory dish type. */
    public Recipe(String name, int standardServingCount,
                  List<RecipeIngredient> ingredients, List<RecipeStep> steps,
                  List<Taste> tastes, Integer preparationTimeMinutes,
                  Integer cookingTimeMinutes, Integer bakingTimeMinutes,
                  NutritionInfo nutritionInfo, DishType dishType) {
        this(UUID.randomUUID(), name, standardServingCount, ingredients, steps, tastes,
                preparationTimeMinutes, cookingTimeMinutes, bakingTimeMinutes,
                nutritionInfo, dishType);
    }

    /** Creates a recipe with its mandatory dish type. */
    public Recipe(String name, int standardServingCount,
                  List<RecipeIngredient> ingredients, List<RecipeStep> steps,
                  List<Taste> tastes, DishType dishType) {
        this(UUID.randomUUID(), name, standardServingCount, ingredients, steps, tastes,
                null, null, null, dishType);
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
        this(id, name, standardServingCount, ingredients, steps, tastes,
                preparationTimeMinutes, cookingTimeMinutes, null, DishType.MAIN);
    }

    /** Recreates a recipe with optional times and per-serving nutrition information. */
    public Recipe(UUID id, String name, int standardServingCount,
                  List<RecipeIngredient> ingredients, List<RecipeStep> steps,
                  List<Taste> tastes, Integer preparationTimeMinutes,
                  Integer cookingTimeMinutes, NutritionInfo nutritionInfo) {
        this(id, name, standardServingCount, ingredients, steps, tastes,
                preparationTimeMinutes, cookingTimeMinutes, nutritionInfo, DishType.MAIN);
    }

    /** Recreates a recipe with optional data and its mandatory dish type. */
    public Recipe(UUID id, String name, int standardServingCount,
                  List<RecipeIngredient> ingredients, List<RecipeStep> steps,
                  List<Taste> tastes, Integer preparationTimeMinutes,
                  Integer cookingTimeMinutes, NutritionInfo nutritionInfo, DishType dishType) {
        this(id, name, standardServingCount, ingredients, steps, tastes,
                preparationTimeMinutes, cookingTimeMinutes, null, nutritionInfo, dishType);
    }

    /** Recreates a recipe with all optional times, nutrition and its mandatory dish type. */
    public Recipe(UUID id, String name, int standardServingCount,
                  List<RecipeIngredient> ingredients, List<RecipeStep> steps,
                  List<Taste> tastes, Integer preparationTimeMinutes,
                  Integer cookingTimeMinutes, Integer bakingTimeMinutes,
                  NutritionInfo nutritionInfo, DishType dishType) {
        this(id, name, standardServingCount, singleOptionGroups(ingredients), steps, tastes,
                preparationTimeMinutes, cookingTimeMinutes, bakingTimeMinutes,
                nutritionInfo, dishType, IngredientGroupInput.INSTANCE);
    }

    /**
     * Creates a new recipe whose ingredient groups are already available.
     *
     * <p>This named factory avoids ambiguous {@code List}-constructor overloads
     * while the older {@link RecipeIngredient} constructors remain source-compatible.</p>
     */
    public static Recipe withIngredientGroups(String name, int standardServingCount,
                                              List<RecipeIngredientGroup> ingredientGroups,
                                              List<RecipeStep> steps, List<Taste> tastes,
                                              DishType dishType) {
        return withIngredientGroups(UUID.randomUUID(), name, standardServingCount,
                ingredientGroups, steps, tastes, null, null, null, dishType);
    }

    /** Creates a new default-main recipe from ingredient groups. */
    public static Recipe withIngredientGroups(String name,
                                              List<RecipeIngredientGroup> ingredientGroups,
                                              List<RecipeStep> steps, List<Taste> tastes) {
        return withIngredientGroups(name, DEFAULT_SERVING_COUNT, ingredientGroups, steps, tastes,
                DishType.MAIN);
    }

    /** Recreates a recipe with its stable identity and complete ingredient-group state. */
    public static Recipe withIngredientGroups(UUID id, String name, int standardServingCount,
                                              List<RecipeIngredientGroup> ingredientGroups,
                                              List<RecipeStep> steps, List<Taste> tastes,
                                              Integer preparationTimeMinutes,
                                              Integer cookingTimeMinutes,
                                              NutritionInfo nutritionInfo, DishType dishType) {
        return withIngredientGroups(id, name, standardServingCount, ingredientGroups, steps, tastes,
                preparationTimeMinutes, cookingTimeMinutes, null, nutritionInfo, dishType);
    }

    /** Recreates a recipe with complete ingredient-group, time and nutrition state. */
    public static Recipe withIngredientGroups(UUID id, String name, int standardServingCount,
                                              List<RecipeIngredientGroup> ingredientGroups,
                                              List<RecipeStep> steps, List<Taste> tastes,
                                              Integer preparationTimeMinutes,
                                              Integer cookingTimeMinutes,
                                              Integer bakingTimeMinutes,
                                              NutritionInfo nutritionInfo, DishType dishType) {
        return new Recipe(id, name, standardServingCount, ingredientGroups, steps, tastes,
                preparationTimeMinutes, cookingTimeMinutes, bakingTimeMinutes,
                nutritionInfo, dishType, IngredientGroupInput.INSTANCE);
    }

    private Recipe(UUID id, String name, int standardServingCount,
                   List<RecipeIngredientGroup> ingredientGroups, List<RecipeStep> steps,
                   List<Taste> tastes, Integer preparationTimeMinutes,
                   Integer cookingTimeMinutes, Integer bakingTimeMinutes,
                   NutritionInfo nutritionInfo, DishType dishType,
                   IngredientGroupInput ignored) {
        this.id = java.util.Objects.requireNonNull(id, "Recipe ID must not be null.");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Recipe name must not be blank.");
        }
        if (standardServingCount <= 0) {
            throw new IllegalArgumentException("Standard serving count must be greater than zero.");
        }

        this.name = name.strip();
        this.standardServingCount = standardServingCount;
        this.ingredientGroups = copyWithoutNulls(ingredientGroups, "Recipe ingredient groups");
        this.steps = copyWithoutNulls(steps, "Recipe steps").stream()
                .sorted(Comparator.comparingInt(RecipeStep::getPosition))
                .toList();
        this.tastes = copyWithoutNulls(tastes, "Recipe tastes");
        this.preparationTimeMinutes = optionalTime(preparationTimeMinutes, "Preparation time");
        this.cookingTimeMinutes = optionalTime(cookingTimeMinutes, "Cooking time");
        this.bakingTimeMinutes = optionalTime(bakingTimeMinutes, "Baking time");
        this.totalTimeMinutes = deriveTotalTime(
                this.preparationTimeMinutes, this.cookingTimeMinutes, this.bakingTimeMinutes);
        this.nutritionInfo = Optional.ofNullable(nutritionInfo)
                .filter(NutritionInfo::hasAnyValue);
        this.dishType = java.util.Objects.requireNonNull(dishType, "Dish type must not be null.");

        if (this.tastes.isEmpty()) {
            throw new IllegalArgumentException("Recipe must have at least one taste.");
        }

        rejectDuplicateGroupIds(this.ingredientGroups);
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

    /** Returns the ordered immutable groups that define this recipe's ingredient needs. */
    public List<RecipeIngredientGroup> getIngredientGroups() {
        return ingredientGroups;
    }

    /**
     * Returns the standard option from every group as the legacy ingredient view.
     *
     * <p>No second ingredient collection is stored. This projection keeps the
     * current persistence, scaling, search and UI code compatible until their
     * dedicated alternative-ingredient phases adopt groups directly.</p>
     */
    public List<RecipeIngredient> getIngredients() {
        return ingredientGroups.stream().map(RecipeIngredientGroup::getStandardOption)
                .map(option -> new RecipeIngredient(option.getIngredient(), option.getQuantity(),
                        option.getUnit()))
                .toList();
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

    /** Returns the optional baking time in minutes. */
    public OptionalInt getBakingTimeMinutes() {
        return bakingTimeMinutes;
    }

    /** Returns the optional total time derived from all available individual times. */
    public OptionalInt getTotalTimeMinutes() {
        return totalTimeMinutes;
    }

    /** Returns optional nutrition values that always apply to one serving. */
    public Optional<NutritionInfo> getNutritionInfo() {
        return nutritionInfo;
    }

    /** Returns the mandatory classification of this recipe. */
    public DishType getDishType() {
        return dishType;
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

    private static OptionalInt deriveTotalTime(OptionalInt... individualTimes) {
        int total = 0;
        boolean hasTime = false;
        try {
            for (OptionalInt time : individualTimes) {
                if (time.isPresent()) {
                    total = Math.addExact(total, time.getAsInt());
                    hasTime = true;
                }
            }
            return hasTime ? OptionalInt.of(total) : OptionalInt.empty();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Recipe times must fit into a positive total minute value.",
                    exception);
        }
    }

    private static List<RecipeIngredientGroup> singleOptionGroups(
            List<RecipeIngredient> ingredients) {
        List<RecipeIngredient> copiedIngredients = copyWithoutNulls(ingredients,
                "Recipe ingredients");
        rejectDuplicateIngredientIds(copiedIngredients);
        return copiedIngredients.stream().map(ingredient -> {
            RecipeIngredientOption option = new RecipeIngredientOption(
                    ingredient.getIngredient(), ingredient.getQuantity(), ingredient.getUnit(), 0);
            return new RecipeIngredientGroup(List.of(option), option);
        }).toList();
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

    private static void rejectDuplicateGroupIds(List<RecipeIngredientGroup> groups) {
        Set<UUID> groupIds = new HashSet<>();
        for (RecipeIngredientGroup group : groups) {
            if (!groupIds.add(group.getId())) {
                throw new IllegalArgumentException(
                        "Recipe must not contain the same ingredient group identity more than once.");
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

    private enum IngredientGroupInput {
        INSTANCE
    }
}
