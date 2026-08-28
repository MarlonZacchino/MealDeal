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
        return GermanRecipeDisplay.unit(unit);
    }

    @Override
    public Unit fromString(String value) {
        throw new UnsupportedOperationException("Unit selection is not editable.");
    }
}
