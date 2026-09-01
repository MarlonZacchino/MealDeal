package de.mealdeal.service;

import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.MealRole;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The locally selected state of one day before a weekly plan is saved.
 *
 * <p>The draft stays independent from JavaFX so other callers can use the same
 * atomic batch-save use case.</p>
 */
public record MealPlanDraft(LocalDate date, Optional<MealPlanEntry> mainEntry,
                            List<MealPlanEntry> sideEntries,
                            List<MealPlanEntry> dessertEntries) {

    /** Keeps MAIN/SIDE-only draft callers source-compatible. */
    public MealPlanDraft(LocalDate date, Optional<MealPlanEntry> mainEntry,
                         List<MealPlanEntry> sideEntries) {
        this(date, mainEntry, sideEntries, List.of());
    }

    public MealPlanDraft {
        Objects.requireNonNull(date, "Meal plan date must not be null.");
        Objects.requireNonNull(mainEntry, "Main draft entry must not be null.");
        sideEntries = List.copyOf(Objects.requireNonNull(sideEntries,
                "Side draft entries must not be null."));
        dessertEntries = List.copyOf(Objects.requireNonNull(dessertEntries,
                "Dessert draft entries must not be null."));
        mainEntry.ifPresent(entry -> requireEntry(entry, date, MealRole.MAIN, 0));
        for (int index = 0; index < sideEntries.size(); index++) {
            requireEntry(sideEntries.get(index), date, MealRole.SIDE, index);
        }
        for (int index = 0; index < dessertEntries.size(); index++) {
            requireEntry(dessertEntries.get(index), date, MealRole.DESSERT, index);
        }
    }

    private static void requireEntry(MealPlanEntry entry, LocalDate date, MealRole role,
                                     int position) {
        Objects.requireNonNull(entry, "Draft entry must not be null.");
        if (!entry.getDate().equals(date) || entry.getMealRole() != role
                || entry.getPosition() != position) {
            throw new IllegalArgumentException("Meal plan draft entry has an invalid role or position.");
        }
    }

}
