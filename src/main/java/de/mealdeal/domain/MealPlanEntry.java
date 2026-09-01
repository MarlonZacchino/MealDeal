package de.mealdeal.domain;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Assigns one persisted recipe and an individual serving count to a calendar
 * date.
 *
 * <p>The UUID is the stable technical identity. A date can have one main dish
 * and multiple ordered side dishes; the entry's role must match its recipe.</p>
 */
public final class MealPlanEntry {

    private final UUID id;
    private final LocalDate date;
    private final Recipe recipe;
    private final int servingCount;
    private final MealRole mealRole;
    private final int position;
    private final Map<UUID, UUID> ingredientOptionSelections;

    /** Creates a planning entry with a new technical identity. */
    public MealPlanEntry(LocalDate date, Recipe recipe, int servingCount) {
        this(UUID.randomUUID(), date, recipe, servingCount,
                MealRole.forDishType(recipe.getDishType()), 0, Map.of());
    }

    /** Creates an entry with its required role and its side-dish position. */
    public MealPlanEntry(LocalDate date, Recipe recipe, int servingCount,
                         MealRole mealRole, int position) {
        this(UUID.randomUUID(), date, recipe, servingCount, mealRole, position, Map.of());
    }

    /** Recreates a planning entry with an existing technical identity. */
    public MealPlanEntry(UUID id, LocalDate date, Recipe recipe, int servingCount) {
        this(id, date, recipe, servingCount, MealRole.forDishType(recipe.getDishType()), 0,
                Map.of());
    }

    /** Recreates an entry with a stable identity, role and persisted position. */
    public MealPlanEntry(UUID id, LocalDate date, Recipe recipe, int servingCount,
                         MealRole mealRole, int position) {
        this(id, date, recipe, servingCount, mealRole, position, Map.of());
    }

    /** Creates an entry with its per-group alternative ingredient selections. */
    public MealPlanEntry(LocalDate date, Recipe recipe, int servingCount,
                         MealRole mealRole, int position,
                         Map<UUID, UUID> ingredientOptionSelections) {
        this(UUID.randomUUID(), date, recipe, servingCount, mealRole, position,
                ingredientOptionSelections);
    }

    /** Recreates an entry including stable per-group alternative selections. */
    public MealPlanEntry(UUID id, LocalDate date, Recipe recipe, int servingCount,
                         MealRole mealRole, int position,
                         Map<UUID, UUID> ingredientOptionSelections) {
        this.id = Objects.requireNonNull(id, "Meal plan entry ID must not be null.");
        this.date = Objects.requireNonNull(date, "Meal plan entry date must not be null.");
        this.recipe = Objects.requireNonNull(recipe, "Meal plan entry recipe must not be null.");
        if (servingCount <= 0) {
            throw new IllegalArgumentException("Meal plan serving count must be greater than zero.");
        }
        this.servingCount = servingCount;
        this.mealRole = Objects.requireNonNull(mealRole, "Meal role must not be null.");
        if (MealRole.forDishType(recipe.getDishType()) != mealRole) {
            throw new IllegalArgumentException("Meal role must match the recipe dish type.");
        }
        if (position < 0 || (mealRole == MealRole.MAIN && position != 0)) {
            throw new IllegalArgumentException("Only side dishes may have a positive position.");
        }
        this.position = position;
        this.ingredientOptionSelections = validateSelections(
                recipe, ingredientOptionSelections);
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public int getServingCount() {
        return servingCount;
    }

    public MealRole getMealRole() {
        return mealRole;
    }

    /** Returns the persisted order among side dishes; MAIN always has position zero. */
    public int getPosition() {
        return position;
    }

    /** Returns explicitly selected option IDs keyed by their ingredient-group IDs. */
    public Map<UUID, UUID> getIngredientOptionSelections() {
        return ingredientOptionSelections;
    }

    /** Resolves an explicit selection or the group's standard option as fallback. */
    public RecipeIngredientOption getSelectedOption(RecipeIngredientGroup group) {
        Objects.requireNonNull(group, "Ingredient group must not be null.");
        RecipeIngredientGroup recipeGroup = recipe.getIngredientGroups().stream()
                .filter(candidate -> candidate.getId().equals(group.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Ingredient group must belong to the planned recipe."));
        UUID selectedId = ingredientOptionSelections.getOrDefault(
                recipeGroup.getId(), recipeGroup.getStandardOptionId());
        return recipeGroup.getOptions().stream()
                .filter(option -> option.getId().equals(selectedId))
                .findFirst()
                .orElseThrow();
    }

    /** Returns one resolved option per recipe group in the recipe's stable order. */
    public List<RecipeIngredientOption> getSelectedIngredientOptions() {
        return recipe.getIngredientGroups().stream().map(this::getSelectedOption).toList();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MealPlanEntry entry)) {
            return false;
        }
        return id.equals(entry.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    private static Map<UUID, UUID> validateSelections(
            Recipe recipe, Map<UUID, UUID> selections) {
        Objects.requireNonNull(selections, "Ingredient option selections must not be null.");
        if (selections.entrySet().stream()
                .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
            throw new IllegalArgumentException(
                    "Ingredient option selections must not contain null values.");
        }
        Map<UUID, UUID> ordered = new LinkedHashMap<>();
        for (RecipeIngredientGroup group : recipe.getIngredientGroups()) {
            UUID optionId = selections.get(group.getId());
            if (optionId == null) {
                continue;
            }
            if (group.getOptions().size() == 1) {
                throw new IllegalArgumentException(
                        "Single-option ingredient groups do not need a selection.");
            }
            if (group.getOptions().stream().noneMatch(option -> option.getId().equals(optionId))) {
                throw new IllegalArgumentException(
                        "Selected ingredient option must belong to its recipe group.");
            }
            ordered.put(group.getId(), optionId);
        }
        if (ordered.size() != selections.size()) {
            throw new IllegalArgumentException(
                    "Selected ingredient group must belong to the planned recipe.");
        }
        return Collections.unmodifiableMap(ordered);
    }
}
