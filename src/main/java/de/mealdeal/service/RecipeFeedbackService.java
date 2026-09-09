package de.mealdeal.service;

import de.mealdeal.domain.RecipeFeedback;
import de.mealdeal.domain.RecipeFeedbackValue;
import de.mealdeal.persistence.repository.RecipeFeedbackRepository;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/** Maintains the single current explicit feedback state for each local recipe. */
public final class RecipeFeedbackService {

    private final RecipeFeedbackRepository repository;
    private final Clock clock;

    public RecipeFeedbackService(RecipeFeedbackRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    public RecipeFeedbackService(RecipeFeedbackRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "Repository must not be null.");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null.");
    }

    /**
     * Replaces the current feedback state. Supplying neither preference nor rating clears it.
     */
    public Optional<RecipeFeedback> update(UUID recipeId,
                                           Optional<RecipeFeedbackValue> value,
                                           OptionalInt rating) {
        Objects.requireNonNull(recipeId, "Recipe ID must not be null.");
        Objects.requireNonNull(value, "Recipe feedback value must not be null.");
        Objects.requireNonNull(rating, "Recipe rating must not be null.");
        if (value.isEmpty() && rating.isEmpty()) {
            repository.deleteByRecipeId(recipeId);
            return Optional.empty();
        }
        UUID feedbackId = repository.findByRecipeId(recipeId)
                .map(RecipeFeedback::getId)
                .orElseGet(UUID::randomUUID);
        RecipeFeedback feedback = new RecipeFeedback(feedbackId, recipeId,
                value.orElse(null), rating.isPresent() ? rating.getAsInt() : null,
                clock.instant());
        repository.save(feedback);
        return Optional.of(feedback);
    }

    public Optional<RecipeFeedback> findByRecipeId(UUID recipeId) {
        return repository.findByRecipeId(Objects.requireNonNull(
                recipeId, "Recipe ID must not be null."));
    }

    /** Loads current feedback for several Recipes in one repository call. */
    public Map<UUID, RecipeFeedback> findByRecipeIds(Collection<UUID> recipeIds) {
        return repository.findByRecipeIds(Objects.requireNonNull(
                recipeIds, "Recipe IDs must not be null."));
    }

    public boolean clear(UUID recipeId) {
        return repository.deleteByRecipeId(Objects.requireNonNull(
                recipeId, "Recipe ID must not be null."));
    }
}
