package de.mealdeal.ui.controller;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IngredientsControllerTest {

    @Test
    void categoryGridUsesTwoToSixColumnsFromExistingViewportClasses() {
        assertEquals(2, IngredientsController.ingredientCategoryColumnsFor(
                List.of("viewport-compact")));
        assertEquals(4, IngredientsController.ingredientCategoryColumnsFor(List.of()));
        assertEquals(5, IngredientsController.ingredientCategoryColumnsFor(
                List.of("viewport-wide")));
        assertEquals(6, IngredientsController.ingredientCategoryColumnsFor(
                List.of("viewport-wide", "viewport-extra-wide")));
    }
}
