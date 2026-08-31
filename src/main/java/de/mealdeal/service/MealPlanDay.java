package de.mealdeal.service;

import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.MealRole;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Read-only state for one date displayed in the current weekly meal plan. */
public record MealPlanDay(LocalDate date, boolean today, Optional<MealPlanEntry> mainEntry,
                          List<MealPlanEntry> sideEntries) {

    public MealPlanDay {
        Objects.requireNonNull(date, "Meal plan day date must not be null.");
        Objects.requireNonNull(mainEntry, "Main entry must not be null.");
        sideEntries = List.copyOf(Objects.requireNonNull(sideEntries,
                "Side entries must not be null."));
        mainEntry.ifPresent(entry -> requireEntryForDay(entry, date, MealRole.MAIN));
        for (int index = 0; index < sideEntries.size(); index++) {
            MealPlanEntry entry = sideEntries.get(index);
            requireEntryForDay(entry, date, MealRole.SIDE);
            if (entry.getPosition() != index) {
                throw new IllegalArgumentException(
                        "Side dish positions must be contiguous and ordered.");
            }
        }
    }

    private static void requireEntryForDay(MealPlanEntry entry, LocalDate date, MealRole role) {
        Objects.requireNonNull(entry, "Meal plan entry must not be null.");
        if (!entry.getDate().equals(date) || entry.getMealRole() != role) {
            throw new IllegalArgumentException("Meal plan entry does not match its day role.");
        }
    }
}
