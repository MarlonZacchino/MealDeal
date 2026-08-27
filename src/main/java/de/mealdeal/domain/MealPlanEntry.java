package de.mealdeal.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Assigns one persisted recipe and an individual serving count to a calendar
 * date.
 *
 * <p>The UUID is the stable technical identity. The date remains a business
 * property even though version 1 permits only one entry per date.</p>
 */
public final class MealPlanEntry {

    private final UUID id;
    private final LocalDate date;
    private final Recipe recipe;
    private final int servingCount;

    /** Creates a planning entry with a new technical identity. */
    public MealPlanEntry(LocalDate date, Recipe recipe, int servingCount) {
        this(UUID.randomUUID(), date, recipe, servingCount);
    }

    /** Recreates a planning entry with an existing technical identity. */
    public MealPlanEntry(UUID id, LocalDate date, Recipe recipe, int servingCount) {
        this.id = Objects.requireNonNull(id, "Meal plan entry ID must not be null.");
        this.date = Objects.requireNonNull(date, "Meal plan entry date must not be null.");
        this.recipe = Objects.requireNonNull(recipe, "Meal plan entry recipe must not be null.");
        if (servingCount <= 0) {
            throw new IllegalArgumentException("Meal plan serving count must be greater than zero.");
        }
        this.servingCount = servingCount;
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
