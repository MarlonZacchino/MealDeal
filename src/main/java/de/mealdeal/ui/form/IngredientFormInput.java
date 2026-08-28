package de.mealdeal.ui.form;

import de.mealdeal.domain.Unit;

/** One ingredient row as entered by the user. */
public record IngredientFormInput(String ingredientName, String quantity, Unit unit) {
}
