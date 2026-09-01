package de.mealdeal.ui.controller;

import de.mealdeal.domain.Unit;
import de.mealdeal.domain.DishType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GermanRecipeDisplayTest {

    @ParameterizedTest
    @CsvSource({
            "1, 1",
            "1.50, '1,5'",
            "0.125, '0,125'",
            "1000.000, 1000"
    })
    void formatsBigDecimalWithGermanDecimalSeparator(String amount, String expected) {
        assertEquals(expected, GermanRecipeDisplay.decimal(new BigDecimal(amount)));
    }

    @Test
    void combinesAmountAndGermanUnit() {
        assertEquals("2,5 EL", GermanRecipeDisplay.quantity(
                new BigDecimal("2.500"), Unit.TABLESPOON));
    }

    @Test
    void displaysSlicesWithGermanSingularAndPlural() {
        assertEquals("1 Scheibe", GermanRecipeDisplay.quantity(BigDecimal.ONE, Unit.SLICE));
        assertEquals("2 Scheiben", GermanRecipeDisplay.quantity(
                new BigDecimal("2"), Unit.SLICE));
    }

    @Test
    void displaysClovesAndSprigsWithGermanSingularAndPlural() {
        assertEquals("1 Zehe", GermanRecipeDisplay.quantity(BigDecimal.ONE, Unit.CLOVE));
        assertEquals("2 Zehen", GermanRecipeDisplay.quantity(new BigDecimal("2"), Unit.CLOVE));
        assertEquals("1 Zweig", GermanRecipeDisplay.quantity(BigDecimal.ONE, Unit.SPRIG));
        assertEquals("2 Zweige", GermanRecipeDisplay.quantity(new BigDecimal("2"), Unit.SPRIG));
    }

    @Test
    void formatsMinutesAsGermanDurations() {
        assertEquals("25 Min.", GermanRecipeDisplay.duration(25));
        assertEquals("1 Std. 20 Min.", GermanRecipeDisplay.duration(80));
        assertEquals("2 Std.", GermanRecipeDisplay.duration(120));
        assertEquals("71,5", GermanRecipeDisplay.decimal(new BigDecimal("71.5")));
    }

    @Test
    void displaysAllDishTypesInGermanForListAndDetailViews() {
        assertEquals("Hauptgericht", GermanRecipeDisplay.dishType(DishType.MAIN));
        assertEquals("Beilage", GermanRecipeDisplay.dishType(DishType.SIDE));
        assertEquals("Nachtisch", GermanRecipeDisplay.dishType(DishType.DESSERT));
    }
}
