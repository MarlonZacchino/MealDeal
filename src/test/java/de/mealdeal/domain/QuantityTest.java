package de.mealdeal.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuantityTest {

    @ParameterizedTest
    @MethodSource("additions")
    void addsCompatibleQuantitiesUsingFirstUnit(
            String firstAmount, Unit firstUnit, String secondAmount, Unit secondUnit,
            String expectedAmount) {
        Quantity first = new Quantity(new BigDecimal(firstAmount), firstUnit);
        Quantity second = new Quantity(new BigDecimal(secondAmount), secondUnit);

        Quantity result = first.add(second);

        assertEquals(new BigDecimal(expectedAmount), result.getAmount());
        assertEquals(firstUnit, result.getUnit());
    }

    static Stream<Arguments> additions() {
        return Stream.of(
                Arguments.of("500", Unit.GRAM, "250", Unit.GRAM, "750"),
                Arguments.of("500", Unit.GRAM, "1", Unit.KILOGRAM, "1500"),
                Arguments.of("1", Unit.KILOGRAM, "500", Unit.GRAM, "1.5"),
                Arguments.of("500", Unit.MILLILITER, "1", Unit.LITER, "1500"),
                Arguments.of("2", Unit.PIECE, "3", Unit.PIECE, "5"),
                Arguments.of("2", Unit.SLICE, "3", Unit.SLICE, "5")
        );
    }

    @ParameterizedTest
    @MethodSource("incompatibleAdditions")
    void rejectsIncompatibleAdditions(Unit firstUnit, Unit secondUnit) {
        Quantity first = new Quantity(BigDecimal.ONE, firstUnit);
        Quantity second = new Quantity(BigDecimal.ONE, secondUnit);

        assertThrows(IllegalArgumentException.class, () -> first.add(second));
    }

    static Stream<Arguments> incompatibleAdditions() {
        return Stream.of(
                Arguments.of(Unit.GRAM, Unit.LITER),
                Arguments.of(Unit.PIECE, Unit.GRAM),
                Arguments.of(Unit.SLICE, Unit.PIECE),
                Arguments.of(Unit.TABLESPOON, Unit.TEASPOON)
        );
    }

    @Test
    void rejectsInvalidConstructionAndNullAddition() {
        assertThrows(IllegalArgumentException.class,
                () -> new Quantity(BigDecimal.ZERO, Unit.GRAM));
        assertThrows(IllegalArgumentException.class,
                () -> new Quantity(new BigDecimal("-1"), Unit.GRAM));
        assertThrows(NullPointerException.class, () -> new Quantity(null, Unit.GRAM));
        assertThrows(NullPointerException.class, () -> new Quantity(BigDecimal.ONE, null));
        assertThrows(NullPointerException.class,
                () -> new Quantity(BigDecimal.ONE, Unit.GRAM).add(null));
    }
}
