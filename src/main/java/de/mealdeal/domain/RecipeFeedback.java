package de.mealdeal.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Current explicit preference state for one local recipe.
 *
 * <p>LIKE/DISLIKE and a 1..5 rating are independent signals, but contradictory
 * combinations are rejected. Rating 3 is neutral and therefore valid only without a
 * binary preference. Absence of a persisted object represents completely neutral state.</p>
 */
public final class RecipeFeedback {

    private final UUID id;
    private final UUID recipeId;
    private final RecipeFeedbackValue value;
    private final Integer rating;
    private final Instant updatedAt;

    public RecipeFeedback(UUID recipeId, RecipeFeedbackValue value, Integer rating,
                          Instant updatedAt) {
        this(UUID.randomUUID(), recipeId, value, rating, updatedAt);
    }

    /** Recreates feedback with its stable technical identity. */
    public RecipeFeedback(UUID id, UUID recipeId, RecipeFeedbackValue value, Integer rating,
                          Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "Recipe feedback ID must not be null.");
        this.recipeId = Objects.requireNonNull(recipeId, "Recipe ID must not be null.");
        if (value == null && rating == null) {
            throw new IllegalArgumentException(
                    "Recipe feedback needs a preference or a rating.");
        }
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new IllegalArgumentException("Recipe rating must be between 1 and 5.");
        }
        if (value == RecipeFeedbackValue.LIKE && rating != null && rating < 4) {
            throw new IllegalArgumentException("LIKE cannot be combined with a rating below 4.");
        }
        if (value == RecipeFeedbackValue.DISLIKE && rating != null && rating > 2) {
            throw new IllegalArgumentException("DISLIKE cannot be combined with a rating above 2.");
        }
        this.value = value;
        this.rating = rating;
        this.updatedAt = Objects.requireNonNull(
                updatedAt, "Recipe feedback timestamp must not be null.");
    }

    public UUID getId() { return id; }
    public UUID getRecipeId() { return recipeId; }
    public Optional<RecipeFeedbackValue> getValue() { return Optional.ofNullable(value); }
    public OptionalInt getRating() {
        return rating == null ? OptionalInt.empty() : OptionalInt.of(rating);
    }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof RecipeFeedback feedback && id.equals(feedback.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
