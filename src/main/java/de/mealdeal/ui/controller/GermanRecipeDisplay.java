package de.mealdeal.ui.controller;

import de.mealdeal.domain.Unit;

import java.math.BigDecimal;
import java.util.Objects;

/** Formats recipe quantities and units for the German user interface. */
final class GermanRecipeDisplay {

    private GermanRecipeDisplay() {
    }

    static String quantity(BigDecimal amount, Unit unit) {
        Objects.requireNonNull(amount, "Amount must not be null.");
        return decimal(amount) + " " + unit(unit);
    }

    static String decimal(BigDecimal amount) {
        Objects.requireNonNull(amount, "Amount must not be null.");
        BigDecimal normalized = amount.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        return normalized.toPlainString().replace('.', ',');
    }

    static String unit(Unit unit) {
        Objects.requireNonNull(unit, "Unit must not be null.");
        return switch (unit) {
            case GRAM -> "g";
            case KILOGRAM -> "kg";
            case MILLILITER -> "ml";
            case LITER -> "l";
            case PIECE -> "Stück";
            case TABLESPOON -> "EL";
            case TEASPOON -> "TL";
            case PINCH -> "Prise";
        };
    }
}
