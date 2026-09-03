package de.mealdeal.ui.controller;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.IngredientCategory;
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

    @Test
    void inventoryPickerSortsCategoriesAndTheirIngredientsAlphabetically() {
        IngredientCategory fruit = new IngredientCategory("Obst", 0);
        IngredientCategory meat = new IngredientCategory("Fleisch", 1);
        List<Ingredient> unordered = List.of(
                new Ingredient("Pfirsiche", fruit),
                new Ingredient("Kalbsschnitzel", meat),
                new Ingredient("Erdbeeren", fruit),
                new Ingredient("Hähnchenbrust", meat));

        assertEquals(List.of("Hähnchenbrust", "Kalbsschnitzel", "Erdbeeren", "Pfirsiche"),
                InventoryController.inventoryPickerOrder(unordered).stream()
                        .map(Ingredient::getName)
                        .toList());
    }
}
