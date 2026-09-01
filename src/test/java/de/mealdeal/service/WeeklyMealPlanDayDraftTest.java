package de.mealdeal.service;

import de.mealdeal.domain.DishType;
import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.MealRole;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredientGroup;
import de.mealdeal.domain.RecipeIngredientOption;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeeklyMealPlanDayDraftTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 25);
    private final Recipe mainRecipe = recipe("Pasta", DishType.MAIN, 4);
    private final Recipe firstSide = recipe("Salat", DishType.SIDE, 2);
    private final Recipe secondSide = recipe("Brot", DishType.SIDE, 2);
    private final Recipe firstDessert = recipe("Pudding", DishType.DESSERT, 2);
    private final Recipe secondDessert = recipe("Eis", DishType.DESSERT, 1);

    @Test
    void supportsSideOnlyPlanningAndUsesTheStandardDefaultWithoutAMain() {
        WeeklyMealPlanDayDraft draft = emptyDraft();

        draft.addSide(firstSide);

        MealPlanEntry side = draft.getSideEntries().getFirst();
        assertTrue(draft.getMainEntry().isEmpty());
        assertEquals(Recipe.DEFAULT_SERVING_COUNT, side.getServingCount());
        assertEquals(MealRole.SIDE, side.getMealRole());
        assertEquals(0, side.getPosition());
    }

    @Test
    void newSideUsesCurrentMainServingCountButRemainsIndependent() {
        WeeklyMealPlanDayDraft draft = emptyDraft();
        draft.setMainRecipe(mainRecipe);
        draft.setMainServingCount(7);

        draft.addSide(firstSide);
        draft.setSideServingCount(0, 3);
        draft.setMainServingCount(8);

        assertEquals(8, draft.getMainEntry().orElseThrow().getServingCount());
        assertEquals(3, draft.getSideEntries().getFirst().getServingCount());
    }

    @Test
    void reordersSidesWithContiguousPositionsAndStableEntryIds() {
        WeeklyMealPlanDayDraft draft = emptyDraft();
        draft.addSide(firstSide);
        draft.addSide(secondSide);
        MealPlanEntry first = draft.getSideEntries().get(0);
        MealPlanEntry second = draft.getSideEntries().get(1);

        draft.moveSideDown(0);

        assertEquals(List.of(second.getId(), first.getId()), draft.getSideEntries().stream()
                .map(MealPlanEntry::getId).toList());
        assertEquals(List.of(0, 1), draft.getSideEntries().stream()
                .map(MealPlanEntry::getPosition).toList());
        assertTrue(draft.isChanged());
    }

    @Test
    void supportsDessertOnlyPlanningWithIndependentServingsAndStableOrdering() {
        WeeklyMealPlanDayDraft draft = emptyDraft();

        draft.addDessert(firstDessert);
        draft.addDessert(secondDessert);
        MealPlanEntry first = draft.getDessertEntries().get(0);
        MealPlanEntry second = draft.getDessertEntries().get(1);
        draft.setDessertServingCount(0, 5);
        draft.moveDessertDown(0);

        assertTrue(draft.getMainEntry().isEmpty());
        assertTrue(draft.getSideEntries().isEmpty());
        assertEquals(List.of(second.getId(), first.getId()), draft.getDessertEntries().stream()
                .map(MealPlanEntry::getId).toList());
        assertEquals(List.of(0, 1), draft.getDessertEntries().stream()
                .map(MealPlanEntry::getPosition).toList());
        assertEquals(5, draft.getDessertEntries().get(1).getServingCount());
        assertTrue(draft.isChanged());
    }

    @Test
    void keepsExistingIdsForMainAndSideUpdatesAndRemovesOnlyTheDeletedSide() {
        MealPlanEntry main = new MealPlanEntry(DATE, mainRecipe, 2);
        MealPlanEntry side = new MealPlanEntry(DATE, firstSide, 2, MealRole.SIDE, 0);
        WeeklyMealPlanDayDraft draft = new WeeklyMealPlanDayDraft(
                new MealPlanDay(DATE, false, Optional.of(main), List.of(side)));

        draft.setMainServingCount(5);
        draft.setSideRecipe(0, secondSide);

        assertEquals(main.getId(), draft.getMainEntry().orElseThrow().getId());
        assertEquals(side.getId(), draft.getSideEntries().getFirst().getId());
        assertEquals(secondSide, draft.getSideEntries().getFirst().getRecipe());
        draft.removeSide(0);
        assertTrue(draft.getSideEntries().isEmpty());
    }

    @Test
    void emptyDayIsNotDirty() {
        assertFalse(emptyDraft().isChanged());
    }

    @Test
    void alternativeSelectionKeepsEntryIdAndCreatesDirtyState() {
        RecipeIngredientOption pasta = new RecipeIngredientOption(
                new Ingredient("Pasta"), new BigDecimal("500"), Unit.GRAM, 0);
        RecipeIngredientOption rice = new RecipeIngredientOption(
                new Ingredient("Rice"), new BigDecimal("350"), Unit.GRAM, 1);
        RecipeIngredientGroup group = new RecipeIngredientGroup(
                List.of(pasta, rice), pasta);
        Recipe flexible = Recipe.withIngredientGroups("Flexible", 2, List.of(group),
                List.of(), List.of(new Taste("Herzhaft")), DishType.MAIN);
        MealPlanEntry original = new MealPlanEntry(DATE, flexible, 2);
        WeeklyMealPlanDayDraft draft = new WeeklyMealPlanDayDraft(
                new MealPlanDay(DATE, false, Optional.of(original), List.of()));

        draft.setMainIngredientOption(group.getId(), rice.getId());

        MealPlanEntry changed = draft.getMainEntry().orElseThrow();
        assertEquals(original.getId(), changed.getId());
        assertEquals(rice, changed.getSelectedOption(group));
        assertTrue(draft.isChanged());

        draft.setMainIngredientOption(group.getId(), pasta.getId());
        assertFalse(draft.isChanged());
    }

    private WeeklyMealPlanDayDraft emptyDraft() {
        return new WeeklyMealPlanDayDraft(
                new MealPlanDay(DATE, false, Optional.empty(), List.of()));
    }

    private static Recipe recipe(String name, DishType dishType, int servings) {
        return new Recipe(name, servings, List.of(), List.of(),
                List.of(new Taste("Herzhaft")), dishType);
    }
}
