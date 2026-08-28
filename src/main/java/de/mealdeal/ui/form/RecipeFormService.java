package de.mealdeal.ui.form;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.RecipeStep;
import de.mealdeal.domain.Taste;
import de.mealdeal.persistence.repository.IngredientRepository;
import de.mealdeal.persistence.repository.RecipeRepository;
import de.mealdeal.persistence.repository.TasteRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Validates form values, resolves central data and saves a recipe. */
public final class RecipeFormService {

    public static final String DEFAULT_SERVING_COUNT = "2";

    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;
    private final TasteRepository tasteRepository;

    /** Creates the form service with the repositories participating in form persistence. */
    public RecipeFormService(RecipeRepository recipeRepository,
                             IngredientRepository ingredientRepository,
                             TasteRepository tasteRepository) {
        this.recipeRepository = Objects.requireNonNull(
                recipeRepository, "Recipe repository must not be null.");
        this.ingredientRepository = Objects.requireNonNull(
                ingredientRepository, "Ingredient repository must not be null.");
        this.tasteRepository = Objects.requireNonNull(
                tasteRepository, "Taste repository must not be null.");
    }

    /** Validates and persists a complete new recipe, returning the saved aggregate. */
    public Recipe createAndSave(RecipeFormInput input) {
        return validateResolveAndSave(null, input);
    }

    /** Validates and persists an edited recipe while retaining its UUID. */
    public Recipe updateAndSave(UUID recipeId, RecipeFormInput input) {
        return validateResolveAndSave(Objects.requireNonNull(
                recipeId, "Recipe ID must not be null."), input);
    }

    private Recipe validateResolveAndSave(UUID recipeId, RecipeFormInput input) {
        ValidatedForm validated = validate(Objects.requireNonNull(
                input, "Recipe form input must not be null."));

        List<Ingredient> existingIngredients = ingredientRepository.findAll();
        List<Taste> existingTastes = tasteRepository.findAll();
        List<RecipeIngredient> recipeIngredients = new ArrayList<>();

        for (ValidatedIngredient ingredientInput : validated.ingredients()) {
            Ingredient ingredient = findIngredient(existingIngredients, ingredientInput.name());
            if (ingredient == null) {
                ingredient = new Ingredient(ingredientInput.name());
                ingredientRepository.save(ingredient);
                existingIngredients = append(existingIngredients, ingredient);
            }
            recipeIngredients.add(new RecipeIngredient(
                    ingredient, ingredientInput.quantity(), ingredientInput.unit()));
        }

        List<Taste> recipeTastes = new ArrayList<>();
        for (String tasteName : validated.tasteNames()) {
            Taste taste = findTaste(existingTastes, tasteName);
            if (taste == null) {
                taste = new Taste(tasteName);
                tasteRepository.save(taste);
                existingTastes = append(existingTastes, taste);
            }
            recipeTastes.add(taste);
        }

        List<RecipeStep> steps = new ArrayList<>();
        for (int index = 0; index < validated.stepDescriptions().size(); index++) {
            steps.add(new RecipeStep(index + 1, validated.stepDescriptions().get(index)));
        }

        Recipe recipe = recipeId == null
                ? new Recipe(validated.name(), validated.servingCount(),
                        recipeIngredients, steps, recipeTastes)
                : new Recipe(recipeId, validated.name(), validated.servingCount(),
                        recipeIngredients, steps, recipeTastes);
        recipeRepository.save(recipe);
        return recipe;
    }

    private static ValidatedForm validate(RecipeFormInput input) {
        List<String> errors = new ArrayList<>();
        String name = stripped(input.name());
        if (name.isEmpty()) {
            errors.add("Bitte gib einen Namen für das Gericht ein.");
        }

        int servingCount = parseServingCount(input.standardServingCount(), errors);
        List<ValidatedIngredient> ingredients = validateIngredients(input.ingredients(), errors);
        List<String> tasteNames = validateTastes(input.tasteNames(), errors);
        List<String> steps = validateSteps(input.stepDescriptions(), errors);

        if (!errors.isEmpty()) {
            throw new RecipeFormValidationException(errors);
        }
        return new ValidatedForm(name, servingCount, ingredients, tasteNames, steps);
    }

    private static int parseServingCount(String input, List<String> errors) {
        try {
            int value = Integer.parseInt(stripped(input));
            if (value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            errors.add("Die Personenanzahl muss eine positive ganze Zahl sein.");
            return 1;
        }
    }

    private static List<ValidatedIngredient> validateIngredients(
            List<IngredientFormInput> inputs, List<String> errors) {
        if (inputs.isEmpty()) {
            errors.add("Füge mindestens eine Zutat hinzu.");
            return List.of();
        }

        List<ValidatedIngredient> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int index = 0; index < inputs.size(); index++) {
            IngredientFormInput input = inputs.get(index);
            int row = index + 1;
            String name = input == null ? "" : stripped(input.ingredientName());
            if (name.isEmpty()) {
                errors.add("Zutat " + row + ": Bitte wähle oder benenne eine Zutat.");
            } else if (!names.add(normalized(name))) {
                errors.add("Zutat " + row + ": Diese Zutat wurde bereits hinzugefügt.");
            }

            BigDecimal quantity = null;
            try {
                quantity = DecimalInputParser.parsePositive(input == null ? null : input.quantity());
            } catch (IllegalArgumentException exception) {
                errors.add("Zutat " + row + ": Die Menge muss eine positive Zahl sein.");
            }

            if (input == null || input.unit() == null) {
                errors.add("Zutat " + row + ": Bitte wähle eine Einheit.");
            }
            if (!name.isEmpty() && quantity != null && input != null && input.unit() != null) {
                result.add(new ValidatedIngredient(name, quantity, input.unit()));
            }
        }
        return List.copyOf(result);
    }

    private static List<String> validateTastes(List<String> inputs, List<String> errors) {
        List<String> names = inputs.stream().map(RecipeFormService::stripped)
                .filter(name -> !name.isEmpty()).toList();
        if (names.isEmpty()) {
            errors.add("Wähle mindestens eine Geschmacksrichtung aus.");
            return List.of();
        }
        return distinctNames(names);
    }

    private static List<String> validateSteps(List<String> inputs, List<String> errors) {
        if (inputs.isEmpty()) {
            errors.add("Füge mindestens einen Zubereitungsschritt hinzu.");
            return List.of();
        }
        List<String> steps = new ArrayList<>();
        for (int index = 0; index < inputs.size(); index++) {
            String description = stripped(inputs.get(index));
            if (description.isEmpty()) {
                errors.add("Schritt " + (index + 1) + ": Bitte gib eine Beschreibung ein.");
            } else {
                steps.add(description);
            }
        }
        return List.copyOf(steps);
    }

    private static Ingredient findIngredient(List<Ingredient> ingredients, String name) {
        return ingredients.stream().filter(value -> sameName(value.getName(), name))
                .findFirst().orElse(null);
    }

    private static Taste findTaste(List<Taste> tastes, String name) {
        return tastes.stream().filter(value -> sameName(value.getName(), name))
                .findFirst().orElse(null);
    }

    private static boolean sameName(String first, String second) {
        return normalized(first).equals(normalized(second));
    }

    private static List<String> distinctNames(List<String> names) {
        Set<String> normalizedNames = new HashSet<>();
        return names.stream().filter(name -> normalizedNames.add(normalized(name))).toList();
    }

    private static String normalized(String value) {
        return stripped(value).toLowerCase(Locale.ROOT);
    }

    private static String stripped(String value) {
        return value == null ? "" : value.strip();
    }

    private static <T> List<T> append(List<T> values, T value) {
        List<T> result = new ArrayList<>(values);
        result.add(value);
        return result;
    }

    private record ValidatedIngredient(String name, BigDecimal quantity,
                                       de.mealdeal.domain.Unit unit) {
    }

    private record ValidatedForm(String name, int servingCount,
                                 List<ValidatedIngredient> ingredients,
                                 List<String> tasteNames,
                                 List<String> stepDescriptions) {
    }
}
