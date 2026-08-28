package de.mealdeal.ui.controller;

import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.service.RecipeScaler;
import de.mealdeal.ui.navigation.NavigationAware;
import de.mealdeal.ui.navigation.ViewNavigator;
import de.mealdeal.ui.navigation.ViewType;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Objects;

/** Renders one recipe and updates its displayed quantities for a serving count. */
public final class RecipeDetailController implements NavigationAware {

    private static final int MAX_SERVING_COUNT = 999;

    private final RecipeScaler recipeScaler;
    private ViewNavigator navigator;
    private Recipe recipe;

    @FXML
    private Label nameLabel;
    @FXML
    private Label standardServingLabel;
    @FXML
    private Label tastesLabel;
    @FXML
    private Spinner<Integer> servingSpinner;
    @FXML
    private VBox ingredientsContainer;
    @FXML
    private VBox stepsContainer;

    /** Creates the detail controller with the application's scaling service. */
    public RecipeDetailController(RecipeScaler recipeScaler) {
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
        tastesLabel.setText(recipe.getTastes().stream()
                .map(taste -> taste.getName())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .reduce((first, second) -> first + ", " + second)
                .orElse("Keine Angabe"));
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
        var scaledIngredients = recipeScaler.scale(recipe, servingCount);
        if (scaledIngredients.isEmpty()) {
            ingredientsContainer.getChildren().add(emptyLabel("Keine Zutaten angegeben."));
            return;
        }
        scaledIngredients.forEach(ingredient ->
                ingredientsContainer.getChildren().add(ingredientRow(ingredient)));
    }

    private void renderSteps() {
        stepsContainer.getChildren().clear();
        if (recipe.getSteps().isEmpty()) {
            stepsContainer.getChildren().add(emptyLabel("Keine Schritte angegeben."));
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

    private static HBox ingredientRow(RecipeIngredient ingredient) {
        Label quantity = new Label(GermanRecipeDisplay.quantity(
                ingredient.getQuantity(), ingredient.getUnit()));
        quantity.getStyleClass().add("detail-quantity");
        Label name = new Label(ingredient.getIngredient().getName());
        name.getStyleClass().add("detail-row-text");
        HBox row = new HBox(16, quantity, name);
        row.getStyleClass().add("detail-row");
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
}
