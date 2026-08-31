package de.mealdeal.domain;

import java.time.LocalDate;
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

    /** Creates a planning entry with a new technical identity. */
    public MealPlanEntry(LocalDate date, Recipe recipe, int servingCount) {
        this(UUID.randomUUID(), date, recipe, servingCount,
                MealRole.forDishType(recipe.getDishType()), 0);
    }

    /** Creates an entry with its required role and its side-dish position. */
    public MealPlanEntry(LocalDate date, Recipe recipe, int servingCount,
                         MealRole mealRole, int position) {
        this(UUID.randomUUID(), date, recipe, servingCount, mealRole, position);
    }

    /** Recreates a planning entry with an existing technical identity. */
    public MealPlanEntry(UUID id, LocalDate date, Recipe recipe, int servingCount) {
        this(id, date, recipe, servingCount, MealRole.forDishType(recipe.getDishType()), 0);
    }

    /** Recreates an entry with a stable identity, role and persisted position. */
    public MealPlanEntry(UUID id, LocalDate date, Recipe recipe, int servingCount,
                         MealRole mealRole, int position) {
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
}
