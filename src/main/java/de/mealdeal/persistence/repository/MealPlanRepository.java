package de.mealdeal.persistence.repository;

import de.mealdeal.domain.MealPlanEntry;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stores and retrieves actual meal-plan entries without exposing JDBC types. */
public interface MealPlanRepository {
    void save(MealPlanEntry entry);

    /**
     * Applies replacements or additions and removals as one atomic change set.
     *
     * <p>Entries are matched by their stable IDs. Implementations must either
     * persist the complete change set or leave all affected entries unchanged.</p>
     */
    void applyChanges(List<MealPlanEntry> entriesToSave, List<UUID> entryIdsToDelete);

    Optional<MealPlanEntry> findById(UUID id);

    /**
     * Returns the optional main dish for a date.
     *
     * <p>The existing one-recipe-per-day UI uses this compatibility query. Side
     * dishes are available through {@link #findBetween(LocalDate, LocalDate)}.</p>
     */
    Optional<MealPlanEntry> findByDate(LocalDate date);

    List<MealPlanEntry> findBetween(LocalDate startInclusive, LocalDate endInclusive);

    /** Returns entries strictly before the cutoff in deterministic plan order. */
    default List<MealPlanEntry> findBefore(LocalDate cutoffExclusive) {
        java.util.Objects.requireNonNull(cutoffExclusive, "Cutoff date must not be null.");
        if (cutoffExclusive.equals(LocalDate.MIN)) {
            return List.of();
        }
        return findBetween(LocalDate.MIN, cutoffExclusive.minusDays(1));
    }

    boolean deleteById(UUID id);

    int deleteBefore(LocalDate cutoffExclusive);
}
