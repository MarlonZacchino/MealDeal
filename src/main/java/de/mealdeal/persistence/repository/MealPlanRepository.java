package de.mealdeal.persistence.repository;

import de.mealdeal.domain.MealPlanEntry;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stores and retrieves actual meal-plan entries without exposing JDBC types. */
public interface MealPlanRepository {
    void save(MealPlanEntry entry);

    Optional<MealPlanEntry> findById(UUID id);

    Optional<MealPlanEntry> findByDate(LocalDate date);

    List<MealPlanEntry> findBetween(LocalDate startInclusive, LocalDate endInclusive);

    boolean deleteById(UUID id);

    int deleteBefore(LocalDate cutoffExclusive);
}
