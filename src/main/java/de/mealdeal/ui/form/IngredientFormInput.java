package de.mealdeal.ui.form;

import de.mealdeal.domain.IngredientCategories;
import de.mealdeal.domain.IngredientCategory;
import de.mealdeal.domain.Unit;

/** One ingredient row as entered by the user. */
public record IngredientFormInput(String ingredientName, String quantity, Unit unit,
                                  IngredientCategory category) {

    /** Keeps older form inputs compatible by assigning new ingredients to Sonstiges. */
    public IngredientFormInput(String ingredientName, String quantity, Unit unit) {
        this(ingredientName, quantity, unit, IngredientCategories.OTHER);
    }
}
