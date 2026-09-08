package de.mealdeal.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable local record that a recipe was actually cooked or eaten.
 *
 * <p>The recipe UUID remains the semantic reference. The name is a deliberately small
 * display snapshot so that history remains understandable after the recipe is deleted;
 * it is not a complete recipe snapshot.</p>
 */
public final class MealHistoryEntry {

    private final UUID id;
    private final UUID recipeId;
    private final String recipeName;
    private final Instant occurredAt;
    private final int servings;
    private final MealHistorySource source;
    private final UUID sourceMealPlanEntryId;
    private final Instant createdAt;

    public MealHistoryEntry(UUID recipeId, String recipeName, Instant occurredAt,
                            int servings, MealHistorySource source,
                            UUID sourceMealPlanEntryId, Instant createdAt) {
        this(UUID.randomUUID(), recipeId, recipeName, occurredAt, servings, source,
                sourceMealPlanEntryId, createdAt);
    }

    /** Recreates an immutable history event with its stable technical identity. */
    public MealHistoryEntry(UUID id, UUID recipeId, String recipeName, Instant occurredAt,
                            int servings, MealHistorySource source,
                            UUID sourceMealPlanEntryId, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "Meal history ID must not be null.");
        this.recipeId = Objects.requireNonNull(recipeId, "Recipe ID must not be null.");
        this.recipeName = requireText(recipeName, "Recipe name must not be blank.");
        this.occurredAt = Objects.requireNonNull(
                occurredAt, "Meal occurrence timestamp must not be null.");
        if (servings <= 0) {
            throw new IllegalArgumentException("Meal history servings must be greater than zero.");
        }
        this.servings = servings;
        this.source = Objects.requireNonNull(source, "Meal history source must not be null.");
        if (source == MealHistorySource.MEAL_PLAN && sourceMealPlanEntryId == null) {
            throw new IllegalArgumentException(
                    "Meal-plan history needs its source meal-plan entry ID.");
        }
        if (source != MealHistorySource.MEAL_PLAN && sourceMealPlanEntryId != null) {
            throw new IllegalArgumentException(
                    "Only meal-plan history may reference a meal-plan entry.");
        }
        this.sourceMealPlanEntryId = sourceMealPlanEntryId;
        this.createdAt = Objects.requireNonNull(
                createdAt, "Meal history creation timestamp must not be null.");
    }

    public UUID getId() { return id; }
    public UUID getRecipeId() { return recipeId; }
    public String getRecipeName() { return recipeName; }
    public Instant getOccurredAt() { return occurredAt; }
    public int getServings() { return servings; }
    public MealHistorySource getSource() { return source; }
    public Optional<UUID> getSourceMealPlanEntryId() {
        return Optional.ofNullable(sourceMealPlanEntryId);
    }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof MealHistoryEntry entry && id.equals(entry.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    private static String requireText(String value, String message) {
        Objects.requireNonNull(value, message);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }
}
