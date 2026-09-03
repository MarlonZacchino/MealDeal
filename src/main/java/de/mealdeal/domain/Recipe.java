package de.mealdeal.domain;

import java.time.Duration;
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
    private final Optional<Duration> preparationTime;
    private final Optional<Duration> cookingTime;
    private final Optional<Duration> bakingTime;
    private final Optional<Duration> restingTime;
    private final Optional<Duration> totalTime;
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
        this(name, standardServingCount, ingredients, steps, tastes,
                preparationTimeMinutes, cookingTimeMinutes, bakingTimeMinutes, null,
                nutritionInfo, dishType);
    }

    /** Creates a recipe with every optional individual time and its mandatory dish type. */
    public Recipe(String name, int standardServingCount,
                  List<RecipeIngredient> ingredients, List<RecipeStep> steps,
                  List<Taste> tastes, Integer preparationTimeMinutes,
                  Integer cookingTimeMinutes, Integer bakingTimeMinutes,
                  Integer restingTimeMinutes, NutritionInfo nutritionInfo, DishType dishType) {
        this(UUID.randomUUID(), name, standardServingCount, ingredients, steps, tastes,
                preparationTimeMinutes, cookingTimeMinutes, bakingTimeMinutes, restingTimeMinutes,
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
        this(id, name, standardServingCount, ingredients, steps, tastes,
                preparationTimeMinutes, cookingTimeMinutes, bakingTimeMinutes, null,
                nutritionInfo, dishType);
    }

    /** Recreates a recipe with every optional individual time and its stable identity. */
    public Recipe(UUID id, String name, int standardServingCount,
                  List<RecipeIngredient> ingredients, List<RecipeStep> steps,
                  List<Taste> tastes, Integer preparationTimeMinutes,
                  Integer cookingTimeMinutes, Integer bakingTimeMinutes,
                  Integer restingTimeMinutes, NutritionInfo nutritionInfo, DishType dishType) {
        this(id, name, standardServingCount, singleOptionGroups(ingredients), steps, tastes,
                preparationTimeMinutes, cookingTimeMinutes, bakingTimeMinutes, restingTimeMinutes,
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
        return withIngredientGroups(id, name, standardServingCount, ingredientGroups, steps, tastes,
                preparationTimeMinutes, cookingTimeMinutes, bakingTimeMinutes, null,
                nutritionInfo, dishType);
    }

    /** Recreates a recipe with ingredient groups and every optional individual time. */
    public static Recipe withIngredientGroups(UUID id, String name, int standardServingCount,
                                              List<RecipeIngredientGroup> ingredientGroups,
                                              List<RecipeStep> steps, List<Taste> tastes,
                                              Integer preparationTimeMinutes,
                                              Integer cookingTimeMinutes,
                                              Integer bakingTimeMinutes,
                                              Integer restingTimeMinutes,
                                              NutritionInfo nutritionInfo, DishType dishType) {
        return new Recipe(id, name, standardServingCount, ingredientGroups, steps, tastes,
                preparationTimeMinutes, cookingTimeMinutes, bakingTimeMinutes, restingTimeMinutes,
                nutritionInfo, dishType, IngredientGroupInput.INSTANCE);
    }

    /** Recreates a recipe from canonical, whole-second durations. */
    public static Recipe withIngredientGroupDurations(
            UUID id, String name, int standardServingCount,
            List<RecipeIngredientGroup> ingredientGroups,
            List<RecipeStep> steps, List<Taste> tastes,
            Duration preparationTime, Duration cookingTime,
            Duration bakingTime, Duration restingTime,
            NutritionInfo nutritionInfo, DishType dishType) {
        return new Recipe(id, name, standardServingCount, ingredientGroups, steps, tastes,
                preparationTime, cookingTime, bakingTime, restingTime,
                nutritionInfo, dishType, DurationInput.INSTANCE);
    }

    private Recipe(UUID id, String name, int standardServingCount,
                   List<RecipeIngredientGroup> ingredientGroups, List<RecipeStep> steps,
                   List<Taste> tastes, Integer preparationTimeMinutes,
                   Integer cookingTimeMinutes, Integer bakingTimeMinutes,
                   Integer restingTimeMinutes,
                   NutritionInfo nutritionInfo, DishType dishType,
                   IngredientGroupInput ignored) {
        this(id, name, standardServingCount, ingredientGroups, steps, tastes,
                durationFromMinutes(preparationTimeMinutes),
                durationFromMinutes(cookingTimeMinutes),
                durationFromMinutes(bakingTimeMinutes),
                durationFromMinutes(restingTimeMinutes), nutritionInfo, dishType,
                DurationInput.INSTANCE);
    }

    private Recipe(UUID id, String name, int standardServingCount,
                   List<RecipeIngredientGroup> ingredientGroups, List<RecipeStep> steps,
                   List<Taste> tastes, Duration preparationTime,
                   Duration cookingTime, Duration bakingTime, Duration restingTime,
                   NutritionInfo nutritionInfo, DishType dishType,
                   DurationInput ignored) {
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
        this.preparationTime = optionalTime(preparationTime, "Preparation time");
        this.cookingTime = optionalTime(cookingTime, "Cooking time");
        this.bakingTime = optionalTime(bakingTime, "Baking time");
        this.restingTime = optionalTime(restingTime, "Resting time");
        this.totalTime = deriveTotalTime(
                this.preparationTime, this.cookingTime, this.bakingTime, this.restingTime);
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

    /** Returns the optional preparation time in its canonical whole-second form. */
    public Optional<Duration> getPreparationTime() {
        return preparationTime;
    }

    /** Returns the optional cooking time in its canonical whole-second form. */
    public Optional<Duration> getCookingTime() {
        return cookingTime;
    }

    /** Returns the optional baking time in its canonical whole-second form. */
    public Optional<Duration> getBakingTime() {
        return bakingTime;
    }

    /** Returns the optional resting time in its canonical whole-second form. */
    public Optional<Duration> getRestingTime() {
        return restingTime;
    }

    /** Returns the optional total derived from all available individual durations. */
    public Optional<Duration> getTotalTime() {
        return totalTime;
    }

    /**
     * Returns an exact legacy minute projection.
     *
     * @deprecated use {@link #getPreparationTime()}; sub-minute values have no minute projection
     */
    @Deprecated
    public OptionalInt getPreparationTimeMinutes() {
        return exactMinutes(preparationTime);
    }

    /** @deprecated use {@link #getCookingTime()} */
    @Deprecated
    public OptionalInt getCookingTimeMinutes() {
        return exactMinutes(cookingTime);
    }

    /** @deprecated use {@link #getBakingTime()} */
    @Deprecated
    public OptionalInt getBakingTimeMinutes() {
        return exactMinutes(bakingTime);
    }

    /** @deprecated use {@link #getRestingTime()} */
    @Deprecated
    public OptionalInt getRestingTimeMinutes() {
        return exactMinutes(restingTime);
    }

    /** @deprecated use {@link #getTotalTime()} */
    @Deprecated
    public OptionalInt getTotalTimeMinutes() {
        return exactMinutes(totalTime);
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

    private static Duration durationFromMinutes(Integer minutes) {
        return minutes == null ? null : Duration.ofMinutes(minutes);
    }

    private static Optional<Duration> optionalTime(Duration duration, String fieldName) {
        if (duration == null) {
            return Optional.empty();
        }
        if (duration.isZero() || duration.isNegative() || duration.getNano() != 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero.");
        }
        return Optional.of(duration);
    }

    @SafeVarargs
    private static Optional<Duration> deriveTotalTime(Optional<Duration>... individualTimes) {
        Duration total = Duration.ZERO;
        boolean hasTime = false;
        try {
            for (Optional<Duration> time : individualTimes) {
                if (time.isPresent()) {
                    total = total.plus(time.orElseThrow());
                    hasTime = true;
                }
            }
            return hasTime ? Optional.of(total) : Optional.empty();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Recipe times must fit into a positive total duration.",
                    exception);
        }
    }

    private static OptionalInt exactMinutes(Optional<Duration> duration) {
        if (duration.isEmpty()) {
            return OptionalInt.empty();
        }
        long seconds = duration.orElseThrow().getSeconds();
        if (seconds % 60 != 0) {
            throw new IllegalStateException("Duration cannot be represented as exact minutes.");
        }
        return OptionalInt.of(Math.toIntExact(seconds / 60));
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

    private enum DurationInput {
        INSTANCE
    }
}
