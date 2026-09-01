package de.mealdeal.service;

import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.MealRole;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Read-only state for one date displayed in the current weekly meal plan. */
public record MealPlanDay(LocalDate date, boolean today, Optional<MealPlanEntry> mainEntry,
                          List<MealPlanEntry> sideEntries,
                          List<MealPlanEntry> dessertEntries) {

    /** Keeps MAIN/SIDE-only callers source-compatible. */
    public MealPlanDay(LocalDate date, boolean today, Optional<MealPlanEntry> mainEntry,
                       List<MealPlanEntry> sideEntries) {
        this(date, today, mainEntry, sideEntries, List.of());
    }

    public MealPlanDay {
        Objects.requireNonNull(date, "Meal plan day date must not be null.");
        Objects.requireNonNull(mainEntry, "Main entry must not be null.");
        sideEntries = List.copyOf(Objects.requireNonNull(sideEntries,
                "Side entries must not be null."));
        dessertEntries = List.copyOf(Objects.requireNonNull(dessertEntries,
                "Dessert entries must not be null."));
        mainEntry.ifPresent(entry -> requireEntryForDay(entry, date, MealRole.MAIN));
        requireOrderedEntries(sideEntries, date, MealRole.SIDE);
        requireOrderedEntries(dessertEntries, date, MealRole.DESSERT);
    }

    private static void requireEntryForDay(MealPlanEntry entry, LocalDate date, MealRole role) {
        Objects.requireNonNull(entry, "Meal plan entry must not be null.");
        if (!entry.getDate().equals(date) || entry.getMealRole() != role) {
            throw new IllegalArgumentException("Meal plan entry does not match its day role.");
        }
    }

    private static void requireOrderedEntries(List<MealPlanEntry> entries,
                                              LocalDate date, MealRole role) {
        for (int index = 0; index < entries.size(); index++) {
            MealPlanEntry entry = entries.get(index);
            requireEntryForDay(entry, date, role);
            if (entry.getPosition() != index) {
                throw new IllegalArgumentException(
                        "Meal plan positions must be contiguous and ordered within each role.");
            }
        }
    }
}
