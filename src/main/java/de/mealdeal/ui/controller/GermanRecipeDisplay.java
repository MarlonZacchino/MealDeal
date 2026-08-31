package de.mealdeal.ui.controller;

import de.mealdeal.domain.Unit;
import de.mealdeal.domain.DishType;

import java.math.BigDecimal;
import java.util.Objects;

/** Formats recipe quantities and units for the German user interface. */
final class GermanRecipeDisplay {

    private GermanRecipeDisplay() {
    }

    static String quantity(BigDecimal amount, Unit unit) {
        Objects.requireNonNull(amount, "Amount must not be null.");
        return decimal(amount) + " " + unit(amount, unit);
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
            case SLICE -> "Scheibe";
            case TABLESPOON -> "EL";
            case TEASPOON -> "TL";
            case PINCH -> "Prise";
        };
    }

    static String unit(BigDecimal amount, Unit unit) {
        Objects.requireNonNull(amount, "Amount must not be null.");
        if (unit == Unit.SLICE && amount.compareTo(BigDecimal.ONE) != 0) {
            return "Scheiben";
        }
        return unit(unit);
    }

    static String duration(int minutes) {
        if (minutes < 60) {
            return minutes + " Min.";
        }
        int hours = minutes / 60;
        int remainingMinutes = minutes % 60;
        return remainingMinutes == 0
                ? hours + " Std."
                : hours + " Std. " + remainingMinutes + " Min.";
    }

    static String dishType(DishType dishType) {
        Objects.requireNonNull(dishType, "Dish type must not be null.");
        return switch (dishType) {
            case MAIN -> "Hauptgericht";
            case SIDE -> "Beilage";
        };
    }
}
