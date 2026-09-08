package de.mealdeal.persistence.repository;

import de.mealdeal.domain.RecipeFeedback;

import java.util.Optional;
import java.util.UUID;

/** Stores at most one current explicit feedback state per local recipe. */
public interface RecipeFeedbackRepository {

    /** Saves new feedback or updates the state while preserving its stable identity. */
    void save(RecipeFeedback feedback);

    Optional<RecipeFeedback> findByRecipeId(UUID recipeId);

    boolean deleteByRecipeId(UUID recipeId);
}
