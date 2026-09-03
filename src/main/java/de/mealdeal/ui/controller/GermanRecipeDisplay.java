package de.mealdeal.ui.controller;

import de.mealdeal.domain.Unit;
import de.mealdeal.domain.DishType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
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
        return normalizedDecimal(amount.setScale(2, RoundingMode.HALF_UP));
    }

    /** Preserves exact values in editable fields so merely saving cannot round domain data. */
    static String editableDecimal(BigDecimal amount) {
        Objects.requireNonNull(amount, "Amount must not be null.");
        return normalizedDecimal(amount);
    }

    private static String normalizedDecimal(BigDecimal amount) {
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
            case CLOVE -> "Zehe";
            case SPRIG -> "Zweig";
            case TABLESPOON -> "EL";
            case TEASPOON -> "TL";
            case PINCH -> "Prise";
        };
    }

    static String unit(BigDecimal amount, Unit unit) {
        Objects.requireNonNull(amount, "Amount must not be null.");
        BigDecimal displayedAmount = amount.setScale(2, RoundingMode.HALF_UP);
        if (displayedAmount.compareTo(BigDecimal.ONE) != 0) {
            return switch (unit) {
                case SLICE -> "Scheiben";
                case CLOVE -> "Zehen";
                case SPRIG -> "Zweige";
                default -> unit(unit);
            };
        }
        return unit(unit);
    }

    static String duration(Duration duration) {
        Objects.requireNonNull(duration, "Duration must not be null.");
        if (duration.isZero() || duration.isNegative() || duration.getNano() != 0) {
            throw new IllegalArgumentException("Duration must contain positive whole seconds.");
        }
        long seconds = duration.getSeconds();
        long hours = seconds / 3_600;
        long minutes = seconds % 3_600 / 60;
        long remainingSeconds = seconds % 60;
        StringBuilder result = new StringBuilder();
        appendDurationPart(result, hours, "Std.");
        appendDurationPart(result, minutes, "Min.");
        appendDurationPart(result, remainingSeconds, "Sek.");
        return result.toString();
    }

    /** Retains the former minute formatter for compatible callers and tests. */
    static String duration(int minutes) {
        return duration(Duration.ofMinutes(minutes));
    }

    private static void appendDurationPart(StringBuilder target, long value, String unit) {
        if (value == 0) {
            return;
        }
        if (!target.isEmpty()) {
            target.append(' ');
        }
        target.append(value).append(' ').append(unit);
    }

    static String dishType(DishType dishType) {
        Objects.requireNonNull(dishType, "Dish type must not be null.");
        return switch (dishType) {
            case MAIN -> "Hauptgericht";
            case SIDE -> "Beilage";
            case DESSERT -> "Nachtisch";
        };
    }
}
