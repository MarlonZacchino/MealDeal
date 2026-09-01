package de.mealdeal.ui.form;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.DishType;
import de.mealdeal.domain.NutritionInfo;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredientGroup;
import de.mealdeal.domain.RecipeIngredientOption;
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
        List<RecipeIngredientGroup> ingredientGroups = new ArrayList<>();
        for (ValidatedIngredientGroup groupInput : validated.ingredientGroups()) {
            List<RecipeIngredientOption> options = new ArrayList<>();
            for (ValidatedIngredientOption optionInput : groupInput.options()) {
                Ingredient ingredient = findIngredient(existingIngredients, optionInput.name());
                if (ingredient == null) {
                    ingredient = new Ingredient(optionInput.name());
                    ingredientRepository.save(ingredient);
                    existingIngredients = append(existingIngredients, ingredient);
                }
                options.add(new RecipeIngredientOption(optionInput.id(), ingredient,
                        optionInput.quantity(), optionInput.unit(), optionInput.position()));
            }
            ingredientGroups.add(new RecipeIngredientGroup(groupInput.id(), options,
                    groupInput.standardOptionId()));
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

        UUID stableRecipeId = recipeId == null ? UUID.randomUUID() : recipeId;
        Recipe recipe = Recipe.withIngredientGroups(stableRecipeId, validated.name(),
                validated.servingCount(), ingredientGroups, steps, recipeTastes,
                validated.preparationTimeMinutes(), validated.cookingTimeMinutes(),
                validated.bakingTimeMinutes(), validated.nutritionInfo(), validated.dishType());
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
        List<ValidatedIngredientGroup> ingredientGroups = validateIngredientGroups(input, errors);
        List<String> tasteNames = validateTastes(input.tasteNames(), errors);
        List<String> steps = validateSteps(input.stepDescriptions());
        Integer preparationTime = parseOptionalMinutes(
                input.preparationTimeMinutes(), "Die Vorbereitungszeit", errors);
        Integer cookingTime = parseOptionalMinutes(
                input.cookingTimeMinutes(), "Die Kochzeit", errors);
        Integer bakingTime = parseOptionalMinutes(
                input.bakingTimeMinutes(), "Die Backzeit", errors);
        Integer calories = parseOptionalNonNegativeInteger(
                input.caloriesKcal(), "Die Kalorien", errors);
        BigDecimal protein = parseOptionalNonNegativeDecimal(
                input.proteinGrams(), "Protein", errors);
        BigDecimal carbohydrates = parseOptionalNonNegativeDecimal(
                input.carbohydrateGrams(), "Kohlenhydrate", errors);
        BigDecimal fat = parseOptionalNonNegativeDecimal(input.fatGrams(), "Fett", errors);
        DishType dishType = input.dishType();
        if (dishType == null) {
            errors.add("Bitte wähle einen Gerichtstyp aus.");
        }

        if (!errors.isEmpty()) {
            throw new RecipeFormValidationException(errors);
        }
        return new ValidatedForm(name, servingCount, ingredientGroups, tasteNames, steps,
                preparationTime, cookingTime, bakingTime,
                new NutritionInfo(calories, protein, carbohydrates, fat), dishType);
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

    private static Integer parseOptionalMinutes(String input, String fieldName,
                                                List<String> errors) {
        String value = stripped(input);
        if (value.isEmpty()) {
            return null;
        }
        try {
            int minutes = Integer.parseInt(value);
            if (minutes <= 0) {
                throw new NumberFormatException();
            }
            return minutes;
        } catch (NumberFormatException exception) {
            errors.add(fieldName + " muss eine positive ganze Minutenzahl sein.");
            return null;
        }
    }

    private static Integer parseOptionalNonNegativeInteger(String input, String fieldName,
                                                            List<String> errors) {
        String value = stripped(input);
        if (value.isEmpty()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            errors.add(fieldName + " muss eine nichtnegative ganze Zahl sein.");
            return null;
        }
    }

    private static BigDecimal parseOptionalNonNegativeDecimal(String input, String fieldName,
                                                               List<String> errors) {
        String value = stripped(input);
        if (value.isEmpty()) {
            return null;
        }
        try {
            return DecimalInputParser.parseNonNegative(value);
        } catch (IllegalArgumentException exception) {
            errors.add(fieldName + " muss eine nichtnegative Zahl sein.");
            return null;
        }
    }

    private static List<ValidatedIngredientGroup> validateIngredientGroups(
            RecipeFormInput form, List<String> errors) {
        List<IngredientGroupFormInput> inputs = form.ingredientGroups().isEmpty()
                ? legacyIngredientGroups(form.ingredients()) : form.ingredientGroups();
        if (inputs.isEmpty()) {
            errors.add("Füge mindestens eine Zutat hinzu.");
            return List.of();
        }

        List<ValidatedIngredientGroup> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        Set<UUID> groupIds = new HashSet<>();
        Set<UUID> optionIds = new HashSet<>();
        for (int groupIndex = 0; groupIndex < inputs.size(); groupIndex++) {
            IngredientGroupFormInput group = inputs.get(groupIndex);
            int displayedGroup = groupIndex + 1;
            if (group == null || group.options().isEmpty()) {
                errors.add("Zutatengruppe " + displayedGroup
                        + ": Füge mindestens eine Option hinzu.");
                continue;
            }
            UUID groupId = group.groupId() == null ? UUID.randomUUID() : group.groupId();
            if (!groupIds.add(groupId)) {
                errors.add("Zutatengruppe " + displayedGroup + ": Die Gruppen-ID ist doppelt.");
            }
            List<ValidatedIngredientOption> options = new ArrayList<>();
            for (int optionIndex = 0; optionIndex < group.options().size(); optionIndex++) {
                IngredientOptionFormInput option = group.options().get(optionIndex);
                int displayedOption = optionIndex + 1;
                String prefix = "Zutatengruppe " + displayedGroup + ", Option "
                        + displayedOption + ": ";
                String optionName = option == null ? "" : stripped(option.ingredientName());
                if (optionName.isEmpty()) {
                    errors.add(prefix + "Bitte wähle oder benenne eine Zutat.");
                } else if (!names.add(normalized(optionName))) {
                    errors.add(prefix + "Diese Zutat wurde bereits hinzugefügt.");
                }
                BigDecimal quantity = null;
                try {
                    quantity = DecimalInputParser.parsePositive(
                            option == null ? null : option.quantity());
                } catch (IllegalArgumentException exception) {
                    errors.add(prefix + "Die Menge muss eine positive Zahl sein.");
                }
                if (option == null || option.unit() == null) {
                    errors.add(prefix + "Bitte wähle eine Einheit.");
                }
                UUID optionId = option == null || option.optionId() == null
                        ? UUID.randomUUID() : option.optionId();
                if (!optionIds.add(optionId)) {
                    errors.add(prefix + "Die Options-ID ist doppelt.");
                }
                if (!optionName.isEmpty() && quantity != null
                        && option != null && option.unit() != null) {
                    options.add(new ValidatedIngredientOption(optionId, optionName, quantity,
                            option.unit(), optionIndex));
                }
            }
            UUID standardId = group.standardOptionId();
            if (standardId == null || group.options().stream()
                    .filter(Objects::nonNull)
                    .noneMatch(option -> standardId.equals(option.optionId()))) {
                errors.add("Zutatengruppe " + displayedGroup
                        + ": Bitte wähle genau eine Standardoption.");
            }
            if (options.size() == group.options().size() && standardId != null) {
                result.add(new ValidatedIngredientGroup(groupId, List.copyOf(options), standardId));
            }
        }
        return List.copyOf(result);
    }

    private static List<IngredientGroupFormInput> legacyIngredientGroups(
            List<IngredientFormInput> ingredients) {
        return ingredients.stream().map(ingredient -> {
            UUID optionId = UUID.randomUUID();
            return new IngredientGroupFormInput(UUID.randomUUID(), List.of(
                    new IngredientOptionFormInput(optionId,
                            ingredient == null ? null : ingredient.ingredientName(),
                            ingredient == null ? null : ingredient.quantity(),
                            ingredient == null ? null : ingredient.unit(), 0)), optionId);
        }).toList();
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

    private static List<String> validateSteps(List<String> inputs) {
        return inputs.stream()
                .map(RecipeFormService::stripped)
                .filter(description -> !description.isEmpty())
                .toList();
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

    private record ValidatedIngredientOption(UUID id, String name, BigDecimal quantity,
                                             de.mealdeal.domain.Unit unit, int position) {
    }

    private record ValidatedIngredientGroup(UUID id,
                                            List<ValidatedIngredientOption> options,
                                            UUID standardOptionId) {
    }

    private record ValidatedForm(String name, int servingCount,
                                 List<ValidatedIngredientGroup> ingredientGroups,
                                 List<String> tasteNames,
                                 List<String> stepDescriptions,
                                 Integer preparationTimeMinutes,
                                 Integer cookingTimeMinutes,
                                 Integer bakingTimeMinutes,
                                 NutritionInfo nutritionInfo,
                                 DishType dishType) {
    }
}
