package de.mealdeal.ui.form;

import de.mealdeal.domain.DishType;

import java.util.List;

/** JavaFX-independent snapshot of all values in the create-recipe form. */
public record RecipeFormInput(
        String name,
        String standardServingCount,
        List<IngredientFormInput> ingredients,
        List<IngredientGroupFormInput> ingredientGroups,
        List<String> tasteNames,
        List<String> stepDescriptions,
        String preparationTimeValue,
        RecipeTimeUnit preparationTimeUnit,
        String cookingTimeValue,
        RecipeTimeUnit cookingTimeUnit,
        String bakingTimeValue,
        RecipeTimeUnit bakingTimeUnit,
        String restingTimeValue,
        RecipeTimeUnit restingTimeUnit,
        String caloriesKcal,
        String proteinGrams,
        String carbohydrateGrams,
        String fatGrams,
        DishType dishType) {

    public RecipeFormInput {
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        ingredientGroups = ingredientGroups == null ? List.of() : List.copyOf(ingredientGroups);
        tasteNames = tasteNames == null ? List.of() : List.copyOf(tasteNames);
        stepDescriptions = stepDescriptions == null ? List.of() : List.copyOf(stepDescriptions);
    }

    /** Creates an input without optional time values for existing callers. */
    public RecipeFormInput(String name, String standardServingCount,
                           List<IngredientFormInput> ingredients, List<String> tasteNames,
                           List<String> stepDescriptions) {
        this(name, standardServingCount, ingredients, List.of(), tasteNames, stepDescriptions,
                null, RecipeTimeUnit.MINUTES, null, RecipeTimeUnit.MINUTES,
                null, RecipeTimeUnit.MINUTES, null, RecipeTimeUnit.MINUTES,
                null, null, null, null, DishType.MAIN);
    }

    /** Creates an input with optional time values but without nutrition values. */
    public RecipeFormInput(String name, String standardServingCount,
                           List<IngredientFormInput> ingredients, List<String> tasteNames,
                           List<String> stepDescriptions, String preparationTimeMinutes,
                           String cookingTimeMinutes) {
        this(name, standardServingCount, ingredients, List.of(), tasteNames, stepDescriptions,
                preparationTimeMinutes, RecipeTimeUnit.MINUTES,
                cookingTimeMinutes, RecipeTimeUnit.MINUTES,
                null, RecipeTimeUnit.MINUTES, null, RecipeTimeUnit.MINUTES,
                null, null, null, null, DishType.MAIN);
    }

    /** Creates an input with optional times and nutrition values, defaulting to MAIN. */
    public RecipeFormInput(String name, String standardServingCount,
                           List<IngredientFormInput> ingredients, List<String> tasteNames,
                           List<String> stepDescriptions, String preparationTimeMinutes,
                           String cookingTimeMinutes, String caloriesKcal, String proteinGrams,
                           String carbohydrateGrams, String fatGrams) {
        this(name, standardServingCount, ingredients, List.of(), tasteNames, stepDescriptions,
                preparationTimeMinutes, RecipeTimeUnit.MINUTES,
                cookingTimeMinutes, RecipeTimeUnit.MINUTES,
                null, RecipeTimeUnit.MINUTES, null, RecipeTimeUnit.MINUTES,
                caloriesKcal, proteinGrams, carbohydrateGrams, fatGrams, DishType.MAIN);
    }

    /** Keeps the pre-baking-time complete form call source-compatible. */
    public RecipeFormInput(String name, String standardServingCount,
                           List<IngredientFormInput> ingredients, List<String> tasteNames,
                           List<String> stepDescriptions, String preparationTimeMinutes,
                           String cookingTimeMinutes, String caloriesKcal, String proteinGrams,
                           String carbohydrateGrams, String fatGrams, DishType dishType) {
        this(name, standardServingCount, ingredients, List.of(), tasteNames, stepDescriptions,
                preparationTimeMinutes, RecipeTimeUnit.MINUTES,
                cookingTimeMinutes, RecipeTimeUnit.MINUTES,
                null, RecipeTimeUnit.MINUTES, null, RecipeTimeUnit.MINUTES,
                caloriesKcal, proteinGrams, carbohydrateGrams, fatGrams, dishType);
    }

    /** Keeps the complete pre-group form call source-compatible. */
    public RecipeFormInput(String name, String standardServingCount,
                           List<IngredientFormInput> ingredients, List<String> tasteNames,
                           List<String> stepDescriptions, String preparationTimeMinutes,
                           String cookingTimeMinutes, String bakingTimeMinutes,
                           String caloriesKcal, String proteinGrams, String carbohydrateGrams,
                           String fatGrams, DishType dishType) {
        this(name, standardServingCount, ingredients, List.of(), tasteNames, stepDescriptions,
                preparationTimeMinutes, RecipeTimeUnit.MINUTES,
                cookingTimeMinutes, RecipeTimeUnit.MINUTES,
                bakingTimeMinutes, RecipeTimeUnit.MINUTES,
                null, RecipeTimeUnit.MINUTES, caloriesKcal,
                proteinGrams, carbohydrateGrams, fatGrams, dishType);
    }

    /** Creates the complete form snapshot from ordered ingredient groups. */
    public RecipeFormInput(String name, String standardServingCount,
                           List<IngredientFormInput> ingredients,
                           List<IngredientGroupFormInput> ingredientGroups,
                           List<String> tasteNames, List<String> stepDescriptions,
                           String preparationTimeMinutes, String cookingTimeMinutes,
                           String bakingTimeMinutes, String restingTimeMinutes,
                           String caloriesKcal, String proteinGrams,
                           String carbohydrateGrams, String fatGrams, DishType dishType) {
        this(name, standardServingCount, ingredients, ingredientGroups, tasteNames,
                stepDescriptions, preparationTimeMinutes, RecipeTimeUnit.MINUTES,
                cookingTimeMinutes, RecipeTimeUnit.MINUTES,
                bakingTimeMinutes, RecipeTimeUnit.MINUTES,
                restingTimeMinutes, RecipeTimeUnit.MINUTES,
                caloriesKcal, proteinGrams, carbohydrateGrams, fatGrams, dishType);
    }

    /** Creates the complete form snapshot from ordered ingredient groups. */
    public static RecipeFormInput withIngredientGroups(
            String name, String standardServingCount,
            List<IngredientGroupFormInput> ingredientGroups,
            List<String> tasteNames, List<String> stepDescriptions,
            String preparationTimeMinutes, String cookingTimeMinutes,
            String bakingTimeMinutes, String caloriesKcal, String proteinGrams,
            String carbohydrateGrams, String fatGrams, DishType dishType) {
        return withIngredientGroups(name, standardServingCount, ingredientGroups, tasteNames,
                stepDescriptions, preparationTimeMinutes, cookingTimeMinutes,
                bakingTimeMinutes, null, caloriesKcal, proteinGrams, carbohydrateGrams,
                fatGrams, dishType);
    }

    /** Creates the complete form snapshot including optional resting time. */
    public static RecipeFormInput withIngredientGroups(
            String name, String standardServingCount,
            List<IngredientGroupFormInput> ingredientGroups,
            List<String> tasteNames, List<String> stepDescriptions,
            String preparationTimeMinutes, String cookingTimeMinutes,
            String bakingTimeMinutes, String restingTimeMinutes,
            String caloriesKcal, String proteinGrams,
            String carbohydrateGrams, String fatGrams, DishType dishType) {
        return withIngredientGroupDurations(name, standardServingCount, ingredientGroups,
                tasteNames, stepDescriptions,
                preparationTimeMinutes, RecipeTimeUnit.MINUTES,
                cookingTimeMinutes, RecipeTimeUnit.MINUTES,
                bakingTimeMinutes, RecipeTimeUnit.MINUTES,
                restingTimeMinutes, RecipeTimeUnit.MINUTES,
                caloriesKcal, proteinGrams, carbohydrateGrams, fatGrams, dishType);
    }

    /** Creates the complete form snapshot with an explicit UI unit for every duration. */
    public static RecipeFormInput withIngredientGroupDurations(
            String name, String standardServingCount,
            List<IngredientGroupFormInput> ingredientGroups,
            List<String> tasteNames, List<String> stepDescriptions,
            String preparationTimeValue, RecipeTimeUnit preparationTimeUnit,
            String cookingTimeValue, RecipeTimeUnit cookingTimeUnit,
            String bakingTimeValue, RecipeTimeUnit bakingTimeUnit,
            String restingTimeValue, RecipeTimeUnit restingTimeUnit,
            String caloriesKcal, String proteinGrams,
            String carbohydrateGrams, String fatGrams, DishType dishType) {
        return new RecipeFormInput(name, standardServingCount, List.of(), ingredientGroups,
                tasteNames, stepDescriptions,
                preparationTimeValue, preparationTimeUnit,
                cookingTimeValue, cookingTimeUnit,
                bakingTimeValue, bakingTimeUnit,
                restingTimeValue, restingTimeUnit,
                caloriesKcal, proteinGrams, carbohydrateGrams, fatGrams, dishType);
    }
}
