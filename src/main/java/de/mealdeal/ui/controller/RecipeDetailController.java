package de.mealdeal.ui.controller;

import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredientGroup;
import de.mealdeal.domain.NutritionInfo;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.RecipeDeletionRestrictedException;
import de.mealdeal.persistence.repository.RecipeRepository;
import de.mealdeal.service.RecipeScaler;
import de.mealdeal.ui.navigation.NavigationAware;
import de.mealdeal.ui.navigation.ViewNavigator;
import de.mealdeal.ui.navigation.ViewType;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.BooleanSupplier;

/** Renders one recipe and updates its displayed quantities for a serving count. */
public final class RecipeDetailController implements NavigationAware {

    private static final System.Logger LOGGER =
            System.getLogger(RecipeDetailController.class.getName());
    private static final int MAX_SERVING_COUNT = 999;

    private final RecipeRepository recipeRepository;
    private final RecipeScaler recipeScaler;
    private ViewNavigator navigator;
    private Recipe recipe;

    @FXML
    private Label nameLabel;
    @FXML
    private Label standardServingLabel;
    @FXML
    private Label dishTypeLabel;
    @FXML
    private Label tastesLabel;
    @FXML
    private Spinner<Integer> servingSpinner;
    @FXML
    private VBox timeSection;
    @FXML
    private VBox timesContainer;
    @FXML
    private VBox nutritionSection;
    @FXML
    private VBox nutritionContainer;
    @FXML
    private VBox ingredientsContainer;
    @FXML
    private VBox stepsContainer;
    @FXML
    private Label emptyStepsLabel;

    /** Creates the detail controller with deletion persistence and scaling dependencies. */
    public RecipeDetailController(RecipeRepository recipeRepository, RecipeScaler recipeScaler) {
        this.recipeRepository = Objects.requireNonNull(
                recipeRepository, "Recipe repository must not be null.");
        this.recipeScaler = Objects.requireNonNull(recipeScaler, "Recipe scaler must not be null.");
    }

    @Override
    public void setNavigator(ViewNavigator navigator) {
        this.navigator = Objects.requireNonNull(navigator, "Navigator must not be null.");
    }

    /** Supplies the selected recipe after FXML loading and renders its complete detail state. */
    public void showRecipe(Recipe recipe) {
        this.recipe = Objects.requireNonNull(recipe, "Recipe must not be null.");
        nameLabel.setText(recipe.getName());
        standardServingLabel.setText("Standard: "
                + servingText(recipe.getStandardServingCount()));
        dishTypeLabel.setText(GermanRecipeDisplay.dishType(recipe.getDishType()));
        tastesLabel.setText(recipe.getTastes().stream()
                .map(taste -> taste.getName())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .reduce((first, second) -> first + ", " + second)
                .orElse("Keine Angabe"));
        renderTimes();
        renderNutrition();
        renderSteps();
        configureServingSelection();
    }

    @FXML
    private void backToRecipes() {
        navigator.navigateTo(ViewType.RECIPES);
    }

    @FXML
    private void editRecipe() {
        navigator.navigateToRecipeEdit(recipe);
    }

    @FXML
    private void deleteRecipe() {
        try {
            DeletionOutcome outcome = deleteAfterConfirmation(recipe, this::confirmDeletion);
            if (outcome == DeletionOutcome.DELETED || outcome == DeletionOutcome.NOT_FOUND) {
                navigator.navigateTo(ViewType.RECIPES);
            }
        } catch (RecipeDeletionRestrictedException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Recipe is still referenced by a meal plan entry.", exception);
            showError("Gericht wird noch verwendet",
                    "Dieses Gericht ist noch im Wochenplan eingetragen und kann deshalb nicht "
                            + "gelöscht werden. Entferne zuerst die entsprechende Planung.");
        } catch (PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not delete recipe.", exception);
            showError("Gericht konnte nicht gelöscht werden",
                    "Beim Löschen ist ein Datenbankfehler aufgetreten. Bitte versuche es erneut.");
        }
    }

    DeletionOutcome deleteAfterConfirmation(Recipe target, BooleanSupplier confirmation) {
        Objects.requireNonNull(target, "Recipe must not be null.");
        Objects.requireNonNull(confirmation, "Confirmation must not be null.");
        if (!confirmation.getAsBoolean()) {
            return DeletionOutcome.CANCELLED;
        }
        return recipeRepository.deleteById(target.getId())
                ? DeletionOutcome.DELETED : DeletionOutcome.NOT_FOUND;
    }

    private boolean confirmDeletion() {
        ButtonType deleteButton = new ButtonType(
                "Löschen", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType(
                "Abbrechen", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Zutaten und Geschmacksrichtungen bleiben erhalten.",
                cancelButton, deleteButton);
        confirmation.initOwner(nameLabel.getScene().getWindow());
        confirmation.setTitle("Gericht löschen");
        confirmation.setHeaderText("„" + recipe.getName() + "“ wirklich löschen?");
        return confirmation.showAndWait().filter(deleteButton::equals).isPresent();
    }

    private void showError(String title, String message) {
        Alert error = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        error.initOwner(nameLabel.getScene().getWindow());
        error.setTitle(title);
        error.setHeaderText(title);
        error.showAndWait();
    }

    private void configureServingSelection() {
        SpinnerValueFactory.IntegerSpinnerValueFactory values =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(
                        1, MAX_SERVING_COUNT, recipe.getStandardServingCount());
        servingSpinner.setValueFactory(values);
        values.valueProperty().addListener((ignored, previous, selected) ->
                renderIngredients(selected));
        renderIngredients(values.getValue());
    }

    private void renderIngredients(int servingCount) {
        ingredientsContainer.getChildren().clear();
        var groups = ingredientGroupDisplays(recipe, servingCount, recipeScaler);
        if (groups.isEmpty()) {
            ingredientsContainer.getChildren().add(emptyLabel("Keine Zutaten angegeben."));
            return;
        }
        groups.forEach(group -> ingredientsContainer.getChildren().add(
                group.options().size() == 1
                        ? ingredientOptionRow(group.options().getFirst(), false)
                        : ingredientGroup(group)));
    }

    private void renderSteps() {
        stepsContainer.getChildren().clear();
        boolean empty = recipe.getSteps().isEmpty();
        emptyStepsLabel.setManaged(empty);
        emptyStepsLabel.setVisible(empty);
        if (empty) {
            return;
        }
        recipe.getSteps().forEach(step -> {
            Label position = new Label(step.getPosition() + ".");
            position.getStyleClass().add("detail-step-position");
            Label description = new Label(step.getDescription());
            description.setWrapText(true);
            description.setMaxWidth(Double.MAX_VALUE);
            description.getStyleClass().add("detail-step-text");
            HBox row = new HBox(14, position, description);
            row.getStyleClass().add("detail-row");
            stepsContainer.getChildren().add(row);
        });
    }

    private void renderTimes() {
        timesContainer.getChildren().clear();
        timeDisplays(recipe).forEach(time -> addTimeRow(time.label(), time.minutes()));
        boolean hasTimes = !timesContainer.getChildren().isEmpty();
        timeSection.setManaged(hasTimes);
        timeSection.setVisible(hasTimes);
    }

    static List<TimeDisplay> timeDisplays(Recipe recipe) {
        Objects.requireNonNull(recipe, "Recipe must not be null.");
        List<TimeDisplay> result = new ArrayList<>();
        addTimeDisplay(result, "Vorbereitungszeit", recipe.getPreparationTimeMinutes());
        addTimeDisplay(result, "Kochzeit", recipe.getCookingTimeMinutes());
        addTimeDisplay(result, "Backzeit", recipe.getBakingTimeMinutes());
        addTimeDisplay(result, "Gesamtzeit", recipe.getTotalTimeMinutes());
        return List.copyOf(result);
    }

    private static void addTimeDisplay(List<TimeDisplay> target, String label,
                                       OptionalInt minutes) {
        if (minutes.isPresent()) {
            target.add(new TimeDisplay(label,
                    GermanRecipeDisplay.duration(minutes.getAsInt())));
        }
    }

    private void renderNutrition() {
        nutritionContainer.getChildren().clear();
        recipe.getNutritionInfo().ifPresent(this::addNutritionRows);
        boolean hasNutrition = !nutritionContainer.getChildren().isEmpty();
        nutritionSection.setManaged(hasNutrition);
        nutritionSection.setVisible(hasNutrition);
    }

    private void addNutritionRows(NutritionInfo nutrition) {
        if (nutrition.getCaloriesKcal().isPresent()) {
            nutritionContainer.getChildren().add(nutritionRow("Kalorien",
                    nutrition.getCaloriesKcal().getAsInt() + " kcal"));
        }
        addNutritionRow("Protein", nutrition.getProteinGrams());
        addNutritionRow("Kohlenhydrate", nutrition.getCarbohydrateGrams());
        addNutritionRow("Fett", nutrition.getFatGrams());
    }

    private void addNutritionRow(String label, java.util.Optional<BigDecimal> grams) {
        grams.ifPresent(value -> nutritionContainer.getChildren().add(
                nutritionRow(label, GermanRecipeDisplay.decimal(value) + " g")));
    }

    private static HBox nutritionRow(String label, String value) {
        Label name = new Label(label);
        name.getStyleClass().add("detail-time-name");
        Label displayedValue = new Label(value);
        displayedValue.getStyleClass().add("detail-time-value");
        HBox row = new HBox(16, name, displayedValue);
        row.getStyleClass().add("detail-row");
        return row;
    }

    private void addTimeRow(String label, String minutes) {
        Label name = new Label(label);
        name.getStyleClass().add("detail-time-name");
        Label value = new Label(minutes);
        value.getStyleClass().add("detail-time-value");
        HBox row = new HBox(16, name, value);
        row.getStyleClass().add("detail-row");
        timesContainer.getChildren().add(row);
    }

    static List<IngredientGroupDisplay> ingredientGroupDisplays(
            Recipe recipe, int servingCount, RecipeScaler scaler) {
        Objects.requireNonNull(recipe, "Recipe must not be null.");
        Objects.requireNonNull(scaler, "Recipe scaler must not be null.");
        return scaler.scaleIngredientGroups(recipe, servingCount).stream()
                .map(RecipeDetailController::ingredientGroupDisplay)
                .toList();
    }

    private static IngredientGroupDisplay ingredientGroupDisplay(RecipeIngredientGroup group) {
        return new IngredientGroupDisplay(group.getOptions().stream()
                .map(option -> new IngredientOptionDisplay(
                        GermanRecipeDisplay.quantity(option.getQuantity(), option.getUnit()),
                        option.getIngredient().getName(),
                        option.getId().equals(group.getStandardOptionId())))
                .toList());
    }

    private static VBox ingredientGroup(IngredientGroupDisplay group) {
        VBox container = new VBox(7);
        container.getStyleClass().add("detail-ingredient-group");
        for (int index = 0; index < group.options().size(); index++) {
            if (index > 0) {
                Label separator = new Label("oder");
                separator.getStyleClass().add("detail-alternative-separator");
                container.getChildren().add(separator);
            }
            container.getChildren().add(ingredientOptionRow(group.options().get(index), true));
        }
        return container;
    }

    private static HBox ingredientOptionRow(IngredientOptionDisplay option,
                                            boolean showStandard) {
        Label quantity = new Label(option.quantity());
        quantity.getStyleClass().add("detail-quantity");
        Label name = new Label(option.ingredientName());
        name.getStyleClass().add("detail-row-text");
        HBox row = new HBox(16, quantity, name);
        row.getStyleClass().add("detail-row");
        if (showStandard && option.standard()) {
            Label standard = new Label("Standard");
            standard.getStyleClass().add("detail-standard-badge");
            row.getChildren().add(standard);
        }
        return row;
    }

    private static Label emptyLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("card-text");
        return label;
    }

    private static String servingText(int count) {
        return count + (count == 1 ? " Person" : " Personen");
    }

    enum DeletionOutcome {
        CANCELLED,
        DELETED,
        NOT_FOUND
    }

    record TimeDisplay(String label, String minutes) {
    }

    record IngredientGroupDisplay(List<IngredientOptionDisplay> options) {
        IngredientGroupDisplay {
            options = List.copyOf(options);
        }
    }

    record IngredientOptionDisplay(String quantity, String ingredientName, boolean standard) {
    }
}
