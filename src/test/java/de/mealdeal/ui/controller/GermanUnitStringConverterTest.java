package de.mealdeal.ui.controller;

import de.mealdeal.domain.Unit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GermanUnitStringConverterTest {

    private final GermanUnitStringConverter converter = new GermanUnitStringConverter();

    @ParameterizedTest
    @CsvSource({
            "GRAM, g",
            "KILOGRAM, kg",
            "MILLILITER, ml",
            "LITER, l",
            "PIECE, Stück",
            "TABLESPOON, EL",
            "TEASPOON, TL",
            "PINCH, Prise"
    })
    void displaysUnitWithGermanKitchenLabel(Unit unit, String expected) {
        assertEquals(expected, converter.toString(unit));
    }
}
