package de.mealdeal.persistence.repository;

import de.mealdeal.domain.MealHistoryEntry;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Stores immutable confirmations of meals that were actually cooked or eaten. */
public interface MealHistoryRepository {

    /**
     * Stores an event. A repeated MEAL_PLAN source entry must remain idempotent and must
     * not create another event.
     */
    void save(MealHistoryEntry entry);

    Optional<MealHistoryEntry> findById(UUID id);

    Optional<MealHistoryEntry> findBySourceMealPlanEntryId(UUID mealPlanEntryId);

    /** Returns newest events first, with stable UUID fallback. */
    List<MealHistoryEntry> findRecent(int limit);

    /** Returns all events for the recipe, newest first. */
    List<MealHistoryEntry> findByRecipeId(UUID recipeId);

    /** Returns all events in the half-open period, newest first. */
    List<MealHistoryEntry> findBetween(Instant fromInclusive, Instant toExclusive);

    Optional<MealHistoryEntry> findLatestByRecipeId(UUID recipeId);

    /** Loads at most the latest event for each requested Recipe in one repository call. */
    Map<UUID, MealHistoryEntry> findLatestByRecipeIds(Collection<UUID> recipeIds);

    long countByRecipeIdBetween(UUID recipeId, Instant fromInclusive, Instant toExclusive);

    boolean existsByRecipeId(UUID recipeId);

    /** Deletes a wrong history event without changing inventory-consumption records. */
    boolean deleteById(UUID id);
}
