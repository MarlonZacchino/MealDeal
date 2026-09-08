package de.mealdeal.persistence.repository;

import de.mealdeal.domain.RecommendationInteraction;

import java.util.List;
import java.util.UUID;

/** Stores immutable, explicitly observed recommendation interactions locally. */
public interface RecommendationInteractionRepository {

    void save(RecommendationInteraction interaction);

    /** Returns one session in displayed-rank order, then event order. */
    List<RecommendationInteraction> findBySessionId(UUID sessionId);

    /** Returns interactions for a recipe, newest first. */
    List<RecommendationInteraction> findByRecipeId(UUID recipeId);

    /** Returns newest interactions first. */
    List<RecommendationInteraction> findRecent(int limit);

    boolean deleteById(UUID id);
}
