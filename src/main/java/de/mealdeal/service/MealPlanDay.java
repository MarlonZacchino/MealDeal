package de.mealdeal.service;

import de.mealdeal.domain.MealPlanEntry;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Read-only state for one date displayed in the current weekly meal plan. */
public record MealPlanDay(LocalDate date, boolean today, Optional<MealPlanEntry> entry) {

    public MealPlanDay {
        Objects.requireNonNull(date, "Meal plan day date must not be null.");
        Objects.requireNonNull(entry, "Meal plan day entry must not be null.");
        entry.ifPresent(plannedEntry -> {
            if (!plannedEntry.getDate().equals(date)) {
                throw new IllegalArgumentException(
                        "Meal plan day and entry must use the same date.");
            }
        });
    }
}
