package de.mealdeal.ui.controller;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryControllerTest {

    @Test
    void inventoryGridUsesOneToFourColumnsFromExistingViewportClasses() {
        assertEquals(1, InventoryController.inventoryColumnsFor(
                List.of("viewport-compact")));
        assertEquals(2, InventoryController.inventoryColumnsFor(List.of()));
        assertEquals(3, InventoryController.inventoryColumnsFor(
                List.of("viewport-wide")));
        assertEquals(4, InventoryController.inventoryColumnsFor(
                List.of("viewport-wide", "viewport-extra-wide")));
    }
}
