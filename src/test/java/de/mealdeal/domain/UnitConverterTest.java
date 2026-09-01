package de.mealdeal.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnitConverterTest {

    @ParameterizedTest
    @MethodSource("conversions")
    void convertsCompatibleUnitsExactly(
            String amount, Unit source, Unit target, String expected) {
        assertEquals(new BigDecimal(expected),
                UnitConverter.convert(new BigDecimal(amount), source, target));
    }

    static Stream<Arguments> conversions() {
        return Stream.of(
                Arguments.of("1000", Unit.GRAM, Unit.KILOGRAM, "1"),
                Arguments.of("1", Unit.KILOGRAM, Unit.GRAM, "1000"),
                Arguments.of("500", Unit.GRAM, Unit.KILOGRAM, "0.5"),
                Arguments.of("1.5", Unit.KILOGRAM, Unit.GRAM, "1500.0"),
                Arguments.of("1000", Unit.MILLILITER, Unit.LITER, "1"),
                Arguments.of("1", Unit.LITER, Unit.MILLILITER, "1000"),
                Arguments.of("750", Unit.MILLILITER, Unit.LITER, "0.75"),
                Arguments.of("2.5", Unit.LITER, Unit.MILLILITER, "2500.0"),
                Arguments.of("12.50", Unit.GRAM, Unit.GRAM, "12.50"),
                Arguments.of("2", Unit.PIECE, Unit.PIECE, "2"),
                Arguments.of("2", Unit.SLICE, Unit.SLICE, "2"),
                Arguments.of("2", Unit.CLOVE, Unit.CLOVE, "2"),
                Arguments.of("2", Unit.SPRIG, Unit.SPRIG, "2"),
                Arguments.of("3", Unit.TABLESPOON, Unit.TABLESPOON, "3")
        );
    }

    @ParameterizedTest
    @MethodSource("incompatibleConversions")
    void rejectsIncompatibleUnits(Unit source, Unit target) {
        assertThrows(IllegalArgumentException.class,
                () -> UnitConverter.convert(BigDecimal.ONE, source, target));
    }

    static Stream<Arguments> incompatibleConversions() {
        return Stream.of(
                Arguments.of(Unit.GRAM, Unit.LITER),
                Arguments.of(Unit.GRAM, Unit.PIECE),
                Arguments.of(Unit.PIECE, Unit.GRAM),
                Arguments.of(Unit.SLICE, Unit.PIECE),
                Arguments.of(Unit.PIECE, Unit.SLICE),
                Arguments.of(Unit.CLOVE, Unit.PIECE),
                Arguments.of(Unit.CLOVE, Unit.SLICE),
                Arguments.of(Unit.SPRIG, Unit.PIECE),
                Arguments.of(Unit.SPRIG, Unit.SLICE),
                Arguments.of(Unit.CLOVE, Unit.SPRIG),
                Arguments.of(Unit.TABLESPOON, Unit.TEASPOON),
                Arguments.of(Unit.TABLESPOON, Unit.MILLILITER),
                Arguments.of(Unit.PINCH, Unit.GRAM)
        );
    }

    @Test
    void rejectsNullArgumentsWithClearErrors() {
        assertThrows(NullPointerException.class,
                () -> UnitConverter.convert(null, Unit.GRAM, Unit.KILOGRAM));
        assertThrows(NullPointerException.class,
                () -> UnitConverter.convert(BigDecimal.ONE, null, Unit.KILOGRAM));
        assertThrows(NullPointerException.class,
                () -> UnitConverter.convert(BigDecimal.ONE, Unit.GRAM, null));
    }

    @Test
    void preservesPrecisionBeyondDecimal128ForFiniteConversions() {
        BigDecimal amount = new BigDecimal("12345678901234567890123456789012345.6789");

        BigDecimal converted = UnitConverter.convert(amount, Unit.KILOGRAM, Unit.GRAM);

        assertEquals(new BigDecimal("12345678901234567890123456789012345678.9000"), converted);
    }
}
