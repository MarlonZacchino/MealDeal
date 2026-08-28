package de.mealdeal.ui.controller;

import de.mealdeal.domain.Unit;
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
}
