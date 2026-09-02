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
import de.mealdeal.ui.navigation.NavigationAware;
import de.mealdeal.ui.navigation.ViewNavigator;
import de.mealdeal.ui.navigation.ViewType;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Optional;
import java.util.stream.Collectors;

/** Controls the shared form for creating or editing a recipe. */
public final class CreateRecipeController implements NavigationAware {

    private static final System.Logger LOGGER =
            System.getLogger(CreateRecipeController.class.getName());

    private final IngredientRepository ingredientRepository;
    private final TasteRepository tasteRepository;
    private final RecipeFormService formService;
    private final java.util.function.Supplier<List<IngredientCategory>> categoryLoader;
    private final List<IngredientGroupFormRow> ingredientGroups = new ArrayList<>();
    private final List<RecipeStepFormRow> stepRows = new ArrayList<>();
    private final List<Ingredient> availableIngredients = new ArrayList<>();
    private final List<IngredientCategory> availableCategories =
            new ArrayList<>(IngredientCategories.all());

    private ViewNavigator navigator;
    private Recipe editingRecipe;

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
    private TextField cookingTimeField;
    @FXML
    private TextField bakingTimeField;
    @FXML
    private TextField restingTimeField;
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
        servingCountField.setText(RecipeFormService.DEFAULT_SERVING_COUNT);
        configureDishTypeBox();
        addIngredientRow();
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
        preparationTimeField.setText(timeText(recipe.getPreparationTimeMinutes()));
        cookingTimeField.setText(timeText(recipe.getCookingTimeMinutes()));
        bakingTimeField.setText(timeText(recipe.getBakingTimeMinutes()));
        restingTimeField.setText(timeText(recipe.getRestingTimeMinutes()));
        NutritionInfo nutrition = recipe.getNutritionInfo().orElse(null);
        caloriesField.setText(nutrition == null ? "" : timeText(nutrition.getCaloriesKcal()));
        proteinField.setText(nutrition == null ? "" : decimalText(nutrition.getProteinGrams()));
        carbohydratesField.setText(nutrition == null
                ? "" : decimalText(nutrition.getCarbohydrateGrams()));
        fatField.setText(nutrition == null ? "" : decimalText(nutrition.getFatGrams()));
        fillIngredients(recipe);
        fillTastes(recipe);
        fillSteps(recipe);
    }

    @FXML
    private void addIngredientRow() {
        IngredientGroupFormRow group = new IngredientGroupFormRow(
                availableIngredients, availableCategories, this::removeIngredientGroup);
        ingredientGroups.add(group);
        ingredientRowsContainer.getChildren().add(group.container());
        updateIngredientGroupRemoveButtons();
    }

    @FXML
    private void addStepRow() {
        RecipeStepFormRow row = new RecipeStepFormRow(this::removeStepRow);
        stepRows.add(row);
        stepRowsContainer.getChildren().add(row.container());
        renumberSteps();
    }

    @FXML
    private void addTaste() {
        String name = newTasteField.getText() == null ? "" : newTasteField.getText().strip();
        if (name.isEmpty()) {
            showMessage("Bitte gib eine Geschmacksrichtung ein.");
            return;
        }

        CheckBox existing = findTasteCheckBox(name);
        if (existing != null) {
            existing.setSelected(true);
        } else {
            addTasteOption(name, true);
        }
        newTasteField.clear();
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

    private void fillIngredients(Recipe recipe) {
        ingredientGroups.clear();
        ingredientRowsContainer.getChildren().clear();
        recipe.getIngredientGroups().forEach(recipeGroup -> {
            IngredientGroupFormRow group = new IngredientGroupFormRow(
                    availableIngredients, availableCategories,
                    this::removeIngredientGroup, recipeGroup);
            ingredientGroups.add(group);
            ingredientRowsContainer.getChildren().add(group.container());
        });
        if (ingredientGroups.isEmpty()) {
            addIngredientRow();
        } else {
            updateIngredientGroupRemoveButtons();
        }
    }

    private void fillTastes(Recipe recipe) {
        for (Taste taste : recipe.getTastes()) {
            CheckBox option = findTasteCheckBox(taste.getName());
            if (option == null) {
                addTasteOption(taste.getName(), true);
            } else {
                option.setSelected(true);
            }
        }
    }

    private void fillSteps(Recipe recipe) {
        stepRows.clear();
        stepRowsContainer.getChildren().clear();
        recipe.getSteps().forEach(step -> {
            RecipeStepFormRow row = new RecipeStepFormRow(this::removeStepRow);
            row.setDescription(step.getDescription());
            stepRows.add(row);
            stepRowsContainer.getChildren().add(row.container());
        });
        renumberSteps();
    }

    private void loadReferenceData() {
        try {
            availableIngredients.clear();
            availableIngredients.addAll(ingredientRepository.findAll().stream()
                    .sorted(Comparator.comparing(Ingredient::getName,
                            String.CASE_INSENSITIVE_ORDER))
                    .toList());
            availableCategories.clear();
            availableCategories.addAll(categoryLoader.get().stream()
                    .sorted(Comparator.comparingInt(IngredientCategory::getPosition)
                            .thenComparing(IngredientCategory::getName))
                    .toList());
            ingredientGroups.forEach(IngredientGroupFormRow::refreshIngredients);
            ingredientGroups.forEach(IngredientGroupFormRow::refreshCategories);
            tasteRepository.findAll().stream()
                    .sorted(Comparator.comparing(Taste::getName, String.CASE_INSENSITIVE_ORDER))
                    .forEach(taste -> addTasteOption(taste.getName(), false));
        } catch (PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not load recipe form data.", exception);
            showMessage("Zutaten und Geschmacksrichtungen konnten nicht geladen werden.");
            saveButton.setDisable(true);
        }
    }

    private RecipeFormInput readFormInput() {
        var groups = ingredientGroups.stream().map(IngredientGroupFormRow::toInput).toList();
        List<String> tastes = tasteOptionsContainer.getChildren().stream()
                .filter(CheckBox.class::isInstance)
                .map(CheckBox.class::cast)
                .filter(CheckBox::isSelected)
                .map(CheckBox::getText)
                .toList();
        List<String> steps = stepRows.stream().map(RecipeStepFormRow::description).toList();
        return RecipeFormInput.withIngredientGroups(nameField.getText(),
                servingCountField.getText(), groups, tastes, steps, preparationTimeField.getText(),
                cookingTimeField.getText(), bakingTimeField.getText(), restingTimeField.getText(),
                caloriesField.getText(), proteinField.getText(),
                carbohydratesField.getText(), fatField.getText(), dishTypeBox.getValue());
    }

    private void removeIngredientGroup(IngredientGroupFormRow group) {
        if (ingredientGroups.size() <= 1) {
            return;
        }
        ingredientGroups.remove(group);
        ingredientRowsContainer.getChildren().remove(group.container());
        updateIngredientGroupRemoveButtons();
    }

    private void removeStepRow(RecipeStepFormRow row) {
        stepRows.remove(row);
        stepRowsContainer.getChildren().remove(row.container());
        renumberSteps();
    }

    private void updateIngredientGroupRemoveButtons() {
        boolean disable = ingredientGroups.size() == 1;
        ingredientGroups.forEach(group -> group.setGroupRemovalDisabled(disable));
    }

    private void renumberSteps() {
        for (int index = 0; index < stepRows.size(); index++) {
            stepRows.get(index).setPosition(index + 1);
        }
    }

    private void addTasteOption(String name, boolean selected) {
        CheckBox checkBox = new CheckBox(name);
        checkBox.setSelected(selected);
        checkBox.getStyleClass().add("taste-option");
        tasteOptionsContainer.getChildren().add(checkBox);
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

    private CheckBox findTasteCheckBox(String name) {
        String normalizedName = name.toLowerCase(Locale.ROOT);
        return tasteOptionsContainer.getChildren().stream()
                .filter(CheckBox.class::isInstance)
                .map(CheckBox.class::cast)
                .filter(checkBox -> checkBox.getText().strip().toLowerCase(Locale.ROOT)
                        .equals(normalizedName))
                .findFirst().orElse(null);
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

    private static String timeText(OptionalInt minutes) {
        return minutes.isPresent() ? Integer.toString(minutes.getAsInt()) : "";
    }

    private static String decimalText(Optional<java.math.BigDecimal> value) {
        return value.map(GermanRecipeDisplay::editableDecimal).orElse("");
    }

}
