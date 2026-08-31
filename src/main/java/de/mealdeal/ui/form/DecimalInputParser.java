package de.mealdeal.ui.form;

import java.math.BigDecimal;

/** Parses decimal quantities entered in the German user interface. */
public final class DecimalInputParser {

    private static final String POSITIVE_DECIMAL_PATTERN = "\\d+(?:[,.]\\d+)?";

    private DecimalInputParser() {
    }

    /**
     * Parses a positive decimal number with either a comma or a point separator.
     * Grouping separators, signs and exponential notation are deliberately rejected.
     */
    public static BigDecimal parsePositive(String input) {
        if (input == null || !input.strip().matches(POSITIVE_DECIMAL_PATTERN)) {
            throw new IllegalArgumentException("Menge muss eine positive Zahl sein.");
        }

        BigDecimal value = new BigDecimal(input.strip().replace(',', '.'));
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Menge muss größer als 0 sein.");
        }
        return value;
    }

    /** Parses a nonnegative decimal number with a comma or point separator. */
    public static BigDecimal parseNonNegative(String input) {
        if (input == null || !input.strip().matches(POSITIVE_DECIMAL_PATTERN)) {
            throw new IllegalArgumentException("Wert muss eine nichtnegative Zahl sein.");
        }
        BigDecimal value = new BigDecimal(input.strip().replace(',', '.'));
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Wert darf nicht negativ sein.");
        }
        return value;
    }
}
