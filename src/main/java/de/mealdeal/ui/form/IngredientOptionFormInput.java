package de.mealdeal.ui.form;

import de.mealdeal.domain.IngredientCategories;
import de.mealdeal.domain.IngredientCategory;
import de.mealdeal.domain.Unit;

import java.util.UUID;

/** One option inside an ingredient group as entered in the recipe form. */
public record IngredientOptionFormInput(
        UUID optionId,
        String ingredientName,
        String quantity,
        Unit unit,
        int position,
        IngredientCategory category) {

    /** Keeps form callers without category input compatible with the fallback category. */
    public IngredientOptionFormInput(UUID optionId, String ingredientName, String quantity,
                                     Unit unit, int position) {
        this(optionId, ingredientName, quantity, unit, position, IngredientCategories.OTHER);
    }
}
