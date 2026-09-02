package de.mealdeal.ui.controller;

import de.mealdeal.domain.DishType;
import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.MealRole;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.Taste;
import de.mealdeal.service.MealPlanDay;
import de.mealdeal.service.WeeklyMealPlanDayDraft;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeekPlanDayViewStateTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 2);

    @Test
    void expansionCanChangeWithoutTouchingTheDraft() {
        WeeklyMealPlanDayDraft draft = emptyDraft();
        WeekPlanDayViewState state = new WeekPlanDayViewState();

        assertTrue(state.isExpanded());
        state.setExpanded(false);

        assertFalse(state.isExpanded());
        assertFalse(draft.isChanged());
    }

    @Test
    void summaryUsesCurrentUnsavedMainSideAndDessertDraftOrder() {
        WeeklyMealPlanDayDraft draft = emptyDraft();
        WeekPlanDayViewState state = new WeekPlanDayViewState();
        assertEquals("Noch nichts geplant", state.summary(draft));

        draft.setMainRecipe(recipe("Schnitzel", DishType.MAIN));
        draft.addSide(recipe("Kartoffeln", DishType.SIDE));
        draft.addSide(recipe("Salat", DishType.SIDE));
        draft.addDessert(recipe("Eis", DishType.DESSERT));

        assertEquals("Schnitzel · Kartoffeln · Salat · Eis", state.summary(draft));
        assertTrue(draft.isChanged());
    }

    @Test
    void summaryKeepsUnsavedDraftDataAcrossCollapseAndExpand() {
        WeeklyMealPlanDayDraft draft = emptyDraft();
        WeekPlanDayViewState state = new WeekPlanDayViewState();

        state.setExpanded(false);
        draft.setMainRecipe(recipe("Schnitzel", DishType.MAIN));
        draft.addSide(recipe("Kartoffeln", DishType.SIDE));
        draft.addDessert(recipe("Eis", DishType.DESSERT));
        state.setExpanded(true);

        assertEquals("Schnitzel · Kartoffeln · Eis", state.summary(draft));
        assertTrue(draft.isChanged());
    }

    private static WeeklyMealPlanDayDraft emptyDraft() {
        return new WeeklyMealPlanDayDraft(new MealPlanDay(
                DATE, false, Optional.empty(), List.of(), List.of()));
    }

    private static Recipe recipe(String name, DishType type) {
        return new Recipe(name, 2, List.of(), List.of(),
                List.of(new Taste("Herzhaft")), type);
    }
}
