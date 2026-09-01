package de.mealdeal.ui.form;

import de.mealdeal.domain.DishType;

import java.util.List;

/** JavaFX-independent snapshot of all values in the create-recipe form. */
public record RecipeFormInput(
        String name,
        String standardServingCount,
        List<IngredientFormInput> ingredients,
        List<String> tasteNames,
        List<String> stepDescriptions,
        String preparationTimeMinutes,
        String cookingTimeMinutes,
        String bakingTimeMinutes,
        String caloriesKcal,
        String proteinGrams,
        String carbohydrateGrams,
        String fatGrams,
        DishType dishType) {

    public RecipeFormInput {
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        tasteNames = tasteNames == null ? List.of() : List.copyOf(tasteNames);
        stepDescriptions = stepDescriptions == null ? List.of() : List.copyOf(stepDescriptions);
    }

    /** Creates an input without optional time values for existing callers. */
    public RecipeFormInput(String name, String standardServingCount,
                           List<IngredientFormInput> ingredients, List<String> tasteNames,
                           List<String> stepDescriptions) {
        this(name, standardServingCount, ingredients, tasteNames, stepDescriptions,
                null, null, null, null, null, null, null, DishType.MAIN);
    }

    /** Creates an input with optional time values but without nutrition values. */
    public RecipeFormInput(String name, String standardServingCount,
                           List<IngredientFormInput> ingredients, List<String> tasteNames,
                           List<String> stepDescriptions, String preparationTimeMinutes,
                           String cookingTimeMinutes) {
        this(name, standardServingCount, ingredients, tasteNames, stepDescriptions,
                preparationTimeMinutes, cookingTimeMinutes, null, null, null, null, null,
                DishType.MAIN);
    }

    /** Creates an input with optional times and nutrition values, defaulting to MAIN. */
    public RecipeFormInput(String name, String standardServingCount,
                           List<IngredientFormInput> ingredients, List<String> tasteNames,
                           List<String> stepDescriptions, String preparationTimeMinutes,
                           String cookingTimeMinutes, String caloriesKcal, String proteinGrams,
                           String carbohydrateGrams, String fatGrams) {
        this(name, standardServingCount, ingredients, tasteNames, stepDescriptions,
                preparationTimeMinutes, cookingTimeMinutes, null, caloriesKcal, proteinGrams,
                carbohydrateGrams, fatGrams, DishType.MAIN);
    }

    /** Keeps the pre-baking-time complete form call source-compatible. */
    public RecipeFormInput(String name, String standardServingCount,
                           List<IngredientFormInput> ingredients, List<String> tasteNames,
                           List<String> stepDescriptions, String preparationTimeMinutes,
                           String cookingTimeMinutes, String caloriesKcal, String proteinGrams,
                           String carbohydrateGrams, String fatGrams, DishType dishType) {
        this(name, standardServingCount, ingredients, tasteNames, stepDescriptions,
                preparationTimeMinutes, cookingTimeMinutes, null, caloriesKcal, proteinGrams,
                carbohydrateGrams, fatGrams, dishType);
    }
}
