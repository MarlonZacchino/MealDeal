package de.mealdeal.ui.controller;

import de.mealdeal.domain.Unit;
import javafx.util.StringConverter;

/** Displays existing units with short German kitchen labels. */
final class GermanUnitStringConverter extends StringConverter<Unit> {

    @Override
    public String toString(Unit unit) {
        if (unit == null) {
            return "";
        }
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

    @Override
    public Unit fromString(String value) {
        throw new UnsupportedOperationException("Unit selection is not editable.");
    }
}
