package de.mealdeal.ui.controller;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.IngredientCategories;
import de.mealdeal.domain.IngredientCategory;
import de.mealdeal.domain.DishType;
import de.mealdeal.domain.NutritionInfo;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.Taste;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.repository.IngredientRepository;
import de.mealdeal.persistence.repository.IngredientCategoryRepository;
import de.mealdeal.persistence.repository.RecipeRepository;
import de.mealdeal.persistence.repository.TasteRepository;
import de.mealdeal.ui.form.RecipeFormInput;
import de.mealdeal.ui.form.RecipeFormService;
import de.mealdeal.ui.form.RecipeFormValidationException;
import de.mealdeal.ui.form.RecipeTimeUnit;
import de.mealdeal.ui.navigation.NavigationAware;
import de.mealdeal.ui.navigation.ViewNavigator;
import de.mealdeal.ui.navigation.ViewType;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Optional;
import java.time.Duration;
import java.util.stream.Collectors;

/** Controls the shared form for creating or editing a recipe. */
public final class CreateRecipeController implements NavigationAware {

    private static final System.Logger LOGGER =
            System.getLogger(CreateRecipeController.class.getName());

    private final IngredientRepository ingredientRepository;
    private final TasteRepository tasteRepository;
    private final RecipeFormService formService;
    private final java.util.function.Supplier<List<IngredientCategory>> categoryLoader;

    private ViewNavigator navigator;
    private Recipe editingRecipe;
    private RecipeIngredientEditor ingredientEditor;
    private RecipeTasteEditor tasteEditor;
    private RecipeStepEditor stepEditor;

    @FXML
    private Label titleLabel;
    @FXML
    private Label subtitleLabel;
    @FXML
    private TextField nameField;
    @FXML
    private TextField servingCountField;
    @FXML
    private ComboBox<DishType> dishTypeBox;
    @FXML
    private TextField preparationTimeField;
    @FXML
    private ComboBox<RecipeTimeUnit> preparationTimeUnitBox;
    @FXML
    private TextField cookingTimeField;
    @FXML
    private ComboBox<RecipeTimeUnit> cookingTimeUnitBox;
    @FXML
    private TextField bakingTimeField;
    @FXML
    private ComboBox<RecipeTimeUnit> bakingTimeUnitBox;
    @FXML
    private TextField restingTimeField;
    @FXML
    private ComboBox<RecipeTimeUnit> restingTimeUnitBox;
    @FXML
    private TextField caloriesField;
    @FXML
    private TextField proteinField;
    @FXML
    private TextField carbohydratesField;
    @FXML
    private TextField fatField;
    @FXML
    private VBox ingredientRowsContainer;
    @FXML
    private FlowPane tasteOptionsContainer;
    @FXML
    private TextField newTasteField;
    @FXML
    private VBox stepRowsContainer;
    @FXML
    private Label formMessage;
    @FXML
    private Button saveButton;

    /** Creates the controller with all repositories needed by the form workflow. */
    public CreateRecipeController(RecipeRepository recipeRepository,
                                  IngredientRepository ingredientRepository,
                                  TasteRepository tasteRepository) {
        this(recipeRepository, ingredientRepository, tasteRepository, IngredientCategories::all);
    }

    /** Creates the production form controller with persisted category reference data. */
    public CreateRecipeController(RecipeRepository recipeRepository,
                                  IngredientRepository ingredientRepository,
                                  TasteRepository tasteRepository,
                                  IngredientCategoryRepository categoryRepository) {
        this(recipeRepository, ingredientRepository, tasteRepository,
                Objects.requireNonNull(categoryRepository,
                        "Ingredient category repository must not be null.")::findAll);
    }

    private CreateRecipeController(RecipeRepository recipeRepository,
                                   IngredientRepository ingredientRepository,
                                   TasteRepository tasteRepository,
                                   java.util.function.Supplier<List<IngredientCategory>>
                                           categoryLoader) {
        this.ingredientRepository = Objects.requireNonNull(
                ingredientRepository, "Ingredient repository must not be null.");
        this.tasteRepository = Objects.requireNonNull(
                tasteRepository, "Taste repository must not be null.");
        this.categoryLoader = Objects.requireNonNull(
                categoryLoader, "Ingredient category loader must not be null.");
        formService = new RecipeFormService(recipeRepository, ingredientRepository, tasteRepository);
    }

    @FXML
    private void initialize() {
        ingredientEditor = new RecipeIngredientEditor(ingredientRowsContainer);
        tasteEditor = new RecipeTasteEditor(tasteOptionsContainer, newTasteField);
        stepEditor = new RecipeStepEditor(stepRowsContainer);
        servingCountField.setText(RecipeFormService.DEFAULT_SERVING_COUNT);
        configureDishTypeBox();
        configureTimeUnitBox(preparationTimeUnitBox);
        configureTimeUnitBox(cookingTimeUnitBox);
        configureTimeUnitBox(bakingTimeUnitBox);
        configureTimeUnitBox(restingTimeUnitBox);
        ingredientEditor.addGroup();
        loadReferenceData();
    }

    @Override
    public void setNavigator(ViewNavigator navigator) {
        this.navigator = Objects.requireNonNull(navigator, "Navigator must not be null.");
    }

    /** Switches the shared form to edit mode and fills every persisted recipe value. */
    public void editRecipe(Recipe recipe) {
        editingRecipe = Objects.requireNonNull(recipe, "Recipe must not be null.");
        titleLabel.setText("Gericht bearbeiten");
        subtitleLabel.setText("Passe Grunddaten, Zutaten, Geschmacksrichtungen und Zubereitung an.");
        nameField.setText(recipe.getName());
        servingCountField.setText(Integer.toString(recipe.getStandardServingCount()));
        dishTypeBox.getSelectionModel().select(recipe.getDishType());
        fillDuration(preparationTimeField, preparationTimeUnitBox, recipe.getPreparationTime());
        fillDuration(cookingTimeField, cookingTimeUnitBox, recipe.getCookingTime());
        fillDuration(bakingTimeField, bakingTimeUnitBox, recipe.getBakingTime());
        fillDuration(restingTimeField, restingTimeUnitBox, recipe.getRestingTime());
        NutritionInfo nutrition = recipe.getNutritionInfo().orElse(null);
        caloriesField.setText(nutrition == null ? "" : integerText(nutrition.getCaloriesKcal()));
        proteinField.setText(nutrition == null ? "" : decimalText(nutrition.getProteinGrams()));
        carbohydratesField.setText(nutrition == null
                ? "" : decimalText(nutrition.getCarbohydrateGrams()));
        fatField.setText(nutrition == null ? "" : decimalText(nutrition.getFatGrams()));
        ingredientEditor.fill(recipe);
        tasteEditor.fill(recipe);
        stepEditor.fill(recipe);
    }

    @FXML
    private void addIngredientRow() {
        ingredientEditor.addGroup();
    }

    @FXML
    private void addStepRow() {
        stepEditor.addRow();
    }

    @FXML
    private void addTaste() {
        if (!tasteEditor.addEnteredTaste()) {
            showMessage("Bitte gib eine Geschmacksrichtung ein.");
            return;
        }
        clearMessage();
    }

    @FXML
    private void saveRecipe() {
        clearMessage();
        try {
            Recipe savedRecipe = editingRecipe == null
                    ? formService.createAndSave(readFormInput())
                    : formService.updateAndSave(editingRecipe.getId(), readFormInput());
            if (editingRecipe == null) {
                navigator.navigateTo(ViewType.RECIPES);
            } else {
                navigator.navigateToRecipeDetail(savedRecipe);
            }
        } catch (RecipeFormValidationException exception) {
            showMessage(exception.getErrors().stream()
                    .map(error -> "• " + error)
                    .collect(Collectors.joining(System.lineSeparator())));
        } catch (PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not create recipe.", exception);
            showMessage("Das Gericht konnte nicht gespeichert werden. Neu angelegte Zutaten "
                    + "oder Geschmacksrichtungen bleiben verfügbar. Bitte versuche es erneut.");
        }
    }

    @FXML
    private void cancel() {
        if (editingRecipe == null) {
            navigator.navigateTo(ViewType.RECIPES);
        } else {
            navigator.navigateToRecipeDetail(editingRecipe);
        }
    }

    private void loadReferenceData() {
        try {
            List<Ingredient> ingredients = ingredientRepository.findAll().stream()
                    .sorted(Comparator.comparing(Ingredient::getName,
                            String.CASE_INSENSITIVE_ORDER))
                    .toList();
            List<IngredientCategory> categories = categoryLoader.get().stream()
                    .sorted(Comparator.comparingInt(IngredientCategory::getPosition)
                            .thenComparing(IngredientCategory::getName))
                    .toList();
            ingredientEditor.replaceReferenceData(ingredients, categories);
            tasteRepository.findAll().stream()
                    .sorted(Comparator.comparing(Taste::getName, String.CASE_INSENSITIVE_ORDER))
                    .map(Taste::getName)
                    .forEach(tasteEditor::addAvailableTaste);
        } catch (PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not load recipe form data.", exception);
            showMessage("Zutaten und Geschmacksrichtungen konnten nicht geladen werden.");
            saveButton.setDisable(true);
        }
    }

    private RecipeFormInput readFormInput() {
        return RecipeFormInput.withIngredientGroupDurations(nameField.getText(),
                servingCountField.getText(), ingredientEditor.inputs(),
                tasteEditor.selectedNames(), stepEditor.descriptions(),
                preparationTimeField.getText(), preparationTimeUnitBox.getValue(),
                cookingTimeField.getText(), cookingTimeUnitBox.getValue(),
                bakingTimeField.getText(), bakingTimeUnitBox.getValue(),
                restingTimeField.getText(), restingTimeUnitBox.getValue(), caloriesField.getText(),
                proteinField.getText(), carbohydratesField.getText(), fatField.getText(),
                dishTypeBox.getValue());
    }

    private void configureDishTypeBox() {
        dishTypeBox.getItems().setAll(DishType.values());
        dishTypeBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(DishType dishType) {
                return dishType == null ? "" : GermanRecipeDisplay.dishType(dishType);
            }

            @Override
            public DishType fromString(String value) {
                throw new UnsupportedOperationException("Dish type selection is not text-based.");
            }
        });
        dishTypeBox.getSelectionModel().select(DishType.MAIN);
    }

    private static void configureTimeUnitBox(ComboBox<RecipeTimeUnit> unitBox) {
        unitBox.getItems().setAll(RecipeTimeUnit.values());
        unitBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(RecipeTimeUnit unit) {
                return unit == null ? "" : unit.getDisplayName();
            }

            @Override
            public RecipeTimeUnit fromString(String value) {
                throw new UnsupportedOperationException("Recipe time units are selected.");
            }
        });
        unitBox.getSelectionModel().select(RecipeTimeUnit.MINUTES);
    }

    private void showMessage(String message) {
        formMessage.setText(message);
        formMessage.setManaged(true);
        formMessage.setVisible(true);
    }

    private void clearMessage() {
        formMessage.setText("");
        formMessage.setManaged(false);
        formMessage.setVisible(false);
    }

    private static void fillDuration(TextField field, ComboBox<RecipeTimeUnit> unitBox,
                                     Optional<Duration> duration) {
        if (duration.isEmpty()) {
            field.clear();
            unitBox.getSelectionModel().select(RecipeTimeUnit.MINUTES);
            return;
        }
        RecipeTimeUnit.EditValue editValue = RecipeTimeUnit.forEditing(duration.orElseThrow());
        field.setText(Integer.toString(editValue.value()));
        unitBox.getSelectionModel().select(editValue.unit());
    }

    private static String integerText(OptionalInt value) {
        return value.isPresent() ? Integer.toString(value.getAsInt()) : "";
    }

    private static String decimalText(Optional<java.math.BigDecimal> value) {
        return value.map(GermanRecipeDisplay::editableDecimal).orElse("");
    }

}
