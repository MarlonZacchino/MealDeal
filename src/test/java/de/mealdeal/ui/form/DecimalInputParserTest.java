package de.mealdeal.ui.form;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DecimalInputParserTest {

    @ParameterizedTest
    @ValueSource(strings = {"1", "1,5", "0,25", "12.75", " 2,50 "})
    void parsesPositiveGermanAndPlainDecimalValues(String input) {
        BigDecimal result = DecimalInputParser.parsePositive(input);

        assertEquals(new BigDecimal(input.strip().replace(',', '.')), result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "-1", "abc", "1,2.3", ",5", "1,"})
    void rejectsInvalidQuantities(String input) {
        assertThrows(IllegalArgumentException.class,
                () -> DecimalInputParser.parsePositive(input));
    }
}
