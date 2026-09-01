package de.mealdeal.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Converts exact decimal amounts between explicitly compatible units.
 *
 * <p>Sharing a {@link UnitDimension} alone does not imply convertibility.
 * Version 1 converts only grams and kilograms, milliliters and liters, and an
 * identical unit to itself. Kitchen measures therefore remain distinct.</p>
 */
public final class UnitConverter {

    private static final BigDecimal THOUSAND = new BigDecimal("1000");
    private UnitConverter() {
    }

    /**
     * Converts an amount to the requested unit.
     *
     * @param amount exact decimal amount to convert
     * @param sourceUnit unit currently used by the amount
     * @param targetUnit requested result unit
     * @return converted amount
     * @throws IllegalArgumentException if the units are not convertible
     */
    public static BigDecimal convert(BigDecimal amount, Unit sourceUnit, Unit targetUnit) {
        Objects.requireNonNull(amount, "Amount must not be null.");
        Objects.requireNonNull(sourceUnit, "Source unit must not be null.");
        Objects.requireNonNull(targetUnit, "Target unit must not be null.");

        if (sourceUnit == targetUnit) {
            return amount;
        }

        if (isPair(sourceUnit, targetUnit, Unit.GRAM, Unit.KILOGRAM)
                || isPair(sourceUnit, targetUnit, Unit.MILLILITER, Unit.LITER)) {
            return convertByThousand(amount, sourceUnit, targetUnit);
        }

        throw new IllegalArgumentException(
                "Cannot convert from " + sourceUnit + " to " + targetUnit + ".");
    }

    /** Returns whether the two units can be converted by the supported exact rules. */
    public static boolean canConvert(Unit sourceUnit, Unit targetUnit) {
        Objects.requireNonNull(sourceUnit, "Source unit must not be null.");
        Objects.requireNonNull(targetUnit, "Target unit must not be null.");
        return sourceUnit == targetUnit
                || isPair(sourceUnit, targetUnit, Unit.GRAM, Unit.KILOGRAM)
                || isPair(sourceUnit, targetUnit, Unit.MILLILITER, Unit.LITER);
    }

    private static boolean isPair(Unit source, Unit target, Unit smaller, Unit larger) {
        return source == smaller && target == larger
                || source == larger && target == smaller;
    }

    private static BigDecimal convertByThousand(
            BigDecimal amount, Unit sourceUnit, Unit targetUnit) {
        boolean convertingToLargerUnit = sourceUnit == Unit.GRAM
                || sourceUnit == Unit.MILLILITER;
        if (convertingToLargerUnit) {
            return amount.divide(THOUSAND);
        }
        return amount.multiply(THOUSAND);
    }
}
