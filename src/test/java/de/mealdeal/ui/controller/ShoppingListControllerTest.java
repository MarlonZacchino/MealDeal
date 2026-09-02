package de.mealdeal.ui.controller;

import de.mealdeal.domain.ShoppingList;
import de.mealdeal.domain.Unit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ShoppingListControllerTest {

    @Test
    void todayModeLoadsOnlyTheTodayResult() {
        ShoppingList today = new ShoppingList(List.of());
        AtomicInteger todayCalls = new AtomicInteger();
        AtomicInteger weekCalls = new AtomicInteger();
        ShoppingListController controller = new ShoppingListController(
                () -> {
                    todayCalls.incrementAndGet();
                    return today;
                },
                () -> {
                    weekCalls.incrementAndGet();
                    return new ShoppingList(List.of());
                });

        ShoppingList result = controller.loadForMode(ShoppingListController.ViewMode.TODAY);

        assertSame(today, result);
        assertEquals(1, todayCalls.get());
        assertEquals(0, weekCalls.get());
    }

    @Test
    void weekModeLoadsOnlyTheCurrentWeekResult() {
        ShoppingList week = new ShoppingList(List.of());
        AtomicInteger todayCalls = new AtomicInteger();
        AtomicInteger weekCalls = new AtomicInteger();
        ShoppingListController controller = new ShoppingListController(
                () -> {
                    todayCalls.incrementAndGet();
                    return new ShoppingList(List.of());
                },
                () -> {
                    weekCalls.incrementAndGet();
                    return week;
                });

        ShoppingList result = controller.loadForMode(
                ShoppingListController.ViewMode.CURRENT_WEEK);

        assertSame(week, result);
        assertEquals(0, todayCalls.get());
        assertEquals(1, weekCalls.get());
    }

    @Test
    void amountAndUnitUseGermanDisplay() {
        assertEquals("1250,5", ShoppingListController.displayAmount(
                new BigDecimal("1250.500")));
        assertEquals("Stück", ShoppingListController.displayUnit(BigDecimal.ONE, Unit.PIECE));
        assertEquals("Scheibe", ShoppingListController.displayUnit(BigDecimal.ONE, Unit.SLICE));
        assertEquals("Scheiben", ShoppingListController.displayUnit(
                new BigDecimal("2"), Unit.SLICE));
    }

    @Test
    void inventoryModeLoadsOnlyTheInventoryAwareSupplier() {
        ShoppingList expected = new ShoppingList(List.of());
        AtomicInteger withoutCalls = new AtomicInteger();
        AtomicInteger withCalls = new AtomicInteger();
        ShoppingListController controller = new ShoppingListController(
                () -> { withoutCalls.incrementAndGet(); return new ShoppingList(List.of()); },
                () -> new ShoppingList(List.of()),
                () -> { withCalls.incrementAndGet(); return expected; },
                () -> new ShoppingList(List.of()));

        ShoppingList result = controller.loadForMode(
                ShoppingListController.ViewMode.TODAY,
                ShoppingListController.InventoryMode.WITH_INVENTORY);

        assertSame(expected, result);
        assertEquals(0, withoutCalls.get());
        assertEquals(1, withCalls.get());
    }

    @Test
    void defaultInventoryModeUsesInventoryAwareCalculation() {
        ShoppingList expected = new ShoppingList(List.of());
        AtomicInteger withoutCalls = new AtomicInteger();
        AtomicInteger withCalls = new AtomicInteger();
        ShoppingListController controller = new ShoppingListController(
                () -> { withoutCalls.incrementAndGet(); return new ShoppingList(List.of()); },
                () -> new ShoppingList(List.of()),
                () -> { withCalls.incrementAndGet(); return expected; },
                () -> new ShoppingList(List.of()));

        assertSame(expected, controller.loadForMode(ShoppingListController.ViewMode.TODAY));
        assertEquals(0, withoutCalls.get());
        assertEquals(1, withCalls.get());
    }
}
