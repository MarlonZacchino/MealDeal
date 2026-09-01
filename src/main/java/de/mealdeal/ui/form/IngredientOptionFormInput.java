package de.mealdeal.ui.form;

import de.mealdeal.domain.Unit;

import java.util.UUID;

/** One option inside an ingredient group as entered in the recipe form. */
public record IngredientOptionFormInput(
        UUID optionId,
        String ingredientName,
        String quantity,
        Unit unit,
        int position) {
}
