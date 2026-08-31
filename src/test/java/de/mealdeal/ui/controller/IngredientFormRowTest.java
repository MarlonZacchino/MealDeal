package de.mealdeal.ui.controller;

import de.mealdeal.domain.Unit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IngredientFormRowTest {

    @Test
    void offersSliceInTheIngredientUnitSelection() {
        assertTrue(IngredientFormRow.availableUnits().contains(Unit.SLICE));
    }
}
