package de.mealdeal.ui.form;

import java.util.List;

/** JavaFX-independent snapshot of all values in the create-recipe form. */
public record RecipeFormInput(
        String name,
        String standardServingCount,
        List<IngredientFormInput> ingredients,
        List<String> tasteNames,
        List<String> stepDescriptions) {

    public RecipeFormInput {
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        tasteNames = tasteNames == null ? List.of() : List.copyOf(tasteNames);
        stepDescriptions = stepDescriptions == null ? List.of() : List.copyOf(stepDescriptions);
    }
}
