package de.mealdeal.ui.controller;

import de.mealdeal.domain.DishType;
import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.MealRole;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.Taste;
import de.mealdeal.service.MealPlanDay;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeMealPlanViewModelTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 31);

    @Test
    void emptyTodayRemainsEmpty() {
        HomeMealPlanViewModel display = HomeMealPlanViewModel.from(
                new MealPlanDay(TODAY, true, Optional.empty(), List.of()));

        assertTrue(display.isEmpty());
        assertTrue(display.mainEntry().isEmpty());
        assertTrue(display.sideEntries().isEmpty());
        assertTrue(display.dessertEntries().isEmpty());
    }

    @Test
    void mainOnlyTodayKeepsItsIndependentServingCount() {
        MealPlanEntry main = new MealPlanEntry(TODAY, recipe("Schnitzel", DishType.MAIN), 2);

        HomeMealPlanViewModel display = HomeMealPlanViewModel.from(
                new MealPlanDay(TODAY, true, Optional.of(main), List.of()));

        assertFalse(display.isEmpty());
        assertEquals("Schnitzel", display.mainEntry().orElseThrow().recipeName());
        assertEquals(2, display.mainEntry().orElseThrow().servingCount());
        assertTrue(display.sideEntries().isEmpty());
    }

    @Test
    void sideOnlyTodayIsNotTreatedAsEmpty() {
        MealPlanEntry side = side("Kartoffelpüree", 4, 0);

        HomeMealPlanViewModel display = HomeMealPlanViewModel.from(
                new MealPlanDay(TODAY, true, Optional.empty(), List.of(side)));

        assertFalse(display.isEmpty());
        assertTrue(display.mainEntry().isEmpty());
        assertEquals(List.of("Kartoffelpüree"), display.sideEntries().stream()
                .map(HomeMealPlanViewModel.RecipeEntry::recipeName).toList());
        assertEquals(4, display.sideEntries().getFirst().servingCount());
    }

    @Test
    void mainAndSidesKeepStoredSideOrderAndIndependentServings() {
        MealPlanEntry main = new MealPlanEntry(TODAY, recipe("Schnitzel", DishType.MAIN), 2);
        MealPlanEntry firstSide = side("Kartoffelpüree", 2, 0);
        MealPlanEntry secondSide = side("Salat", 4, 1);

        HomeMealPlanViewModel display = HomeMealPlanViewModel.from(new MealPlanDay(
                TODAY, true, Optional.of(main), List.of(firstSide, secondSide)));

        assertEquals(2, display.mainEntry().orElseThrow().servingCount());
        assertEquals(List.of("Kartoffelpüree", "Salat"), display.sideEntries().stream()
                .map(HomeMealPlanViewModel.RecipeEntry::recipeName).toList());
        assertEquals(List.of(2, 4), display.sideEntries().stream()
                .map(HomeMealPlanViewModel.RecipeEntry::servingCount).toList());
    }

    @Test
    void dessertOnlyTodayIsVisibleAndKeepsOrderAndIndependentServings() {
        MealPlanEntry first = dessert("Pudding", 2, 0);
        MealPlanEntry second = dessert("Eis", 5, 1);

        HomeMealPlanViewModel display = HomeMealPlanViewModel.from(new MealPlanDay(
                TODAY, true, Optional.empty(), List.of(), List.of(first, second)));

        assertFalse(display.isEmpty());
        assertTrue(display.mainEntry().isEmpty());
        assertTrue(display.sideEntries().isEmpty());
        assertEquals(List.of("Pudding", "Eis"), display.dessertEntries().stream()
                .map(HomeMealPlanViewModel.RecipeEntry::recipeName).toList());
        assertEquals(List.of(2, 5), display.dessertEntries().stream()
                .map(HomeMealPlanViewModel.RecipeEntry::servingCount).toList());
    }

    @Test
    void weeklyOverviewDataKeepsMixedSideOnlyAndEmptyDaysDistinct() {
        MealPlanEntry main = new MealPlanEntry(TODAY, recipe("Schnitzel", DishType.MAIN), 2);
        MealPlanEntry side = side("Kartoffelpüree", 2, 0);
        MealPlanEntry sideOnly = new MealPlanEntry(TODAY.plusDays(1),
                recipe("Salat", DishType.SIDE), 4, MealRole.SIDE, 0);
        List<MealPlanDay> week = List.of(
                new MealPlanDay(TODAY, true, Optional.of(main), List.of(side)),
                new MealPlanDay(TODAY.plusDays(1), false, Optional.empty(), List.of(sideOnly)),
                new MealPlanDay(TODAY.plusDays(2), false, Optional.empty(), List.of()));

        List<HomeMealPlanViewModel> overview = week.stream()
                .map(HomeMealPlanViewModel::from).toList();

        assertEquals("Schnitzel", overview.get(0).mainEntry().orElseThrow().recipeName());
        assertEquals(List.of("Kartoffelpüree"), overview.get(0).sideEntries().stream()
                .map(HomeMealPlanViewModel.RecipeEntry::recipeName).toList());
        assertFalse(overview.get(1).isEmpty());
        assertTrue(overview.get(1).mainEntry().isEmpty());
        assertEquals(4, overview.get(1).sideEntries().getFirst().servingCount());
        assertTrue(overview.get(2).isEmpty());
    }

    private static MealPlanEntry side(String name, int servings, int position) {
        return new MealPlanEntry(TODAY, recipe(name, DishType.SIDE), servings,
                MealRole.SIDE, position);
    }

    private static MealPlanEntry dessert(String name, int servings, int position) {
        return new MealPlanEntry(TODAY, recipe(name, DishType.DESSERT), servings,
                MealRole.DESSERT, position);
    }

    private static Recipe recipe(String name, DishType dishType) {
        return new Recipe(name, List.of(), List.of(), List.of(new Taste("Herzhaft")), dishType);
    }
}
