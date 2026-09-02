package de.mealdeal.ui.controller;

import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.service.WeeklyMealPlanDayDraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** JavaFX-independent expansion and summary state for one weekly-plan card. */
final class WeekPlanDayViewState {

    private boolean expanded = true;

    boolean isExpanded() {
        return expanded;
    }

    void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    String summary(WeeklyMealPlanDayDraft draft) {
        Objects.requireNonNull(draft, "Meal plan draft must not be null.");
        List<String> names = new ArrayList<>();
        draft.getMainEntry().map(MealPlanEntry::getRecipe)
                .map(recipe -> recipe.getName()).ifPresent(names::add);
        draft.getSideEntries().stream().map(MealPlanEntry::getRecipe)
                .map(recipe -> recipe.getName()).forEach(names::add);
        draft.getDessertEntries().stream().map(MealPlanEntry::getRecipe)
                .map(recipe -> recipe.getName()).forEach(names::add);
        return names.isEmpty() ? "Noch nichts geplant" : String.join(" · ", names);
    }
}
