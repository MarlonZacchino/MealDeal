package de.mealdeal.ui.controller;

import de.mealdeal.domain.NutritionInfo;
import de.mealdeal.domain.Recipe;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.RecipeDeletionRestrictedException;
import de.mealdeal.persistence.repository.RecipeRepository;
import de.mealdeal.service.RecipeScaler;
import de.mealdeal.ui.navigation.NavigationAware;
import de.mealdeal.ui.navigation.ViewNavigator;
import de.mealdeal.ui.navigation.ViewType;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/** Renders one recipe and keeps serving and alternative choices local to this view. */
public final class RecipeDetailController implements NavigationAware {

    private static final System.Logger LOGGER =
            System.getLogger(RecipeDetailController.class.getName());
    private static final int MAX_SERVING_COUNT = 999;

    private final RecipeRepository recipeRepository;
    private final RecipeScaler recipeScaler;
    private final ListChangeListener<String> viewportListener = ignored -> applyResponsiveLayout();
    private ViewNavigator navigator;
    private Recipe recipe;
    private RecipeDetailIngredientModel ingredientModel;
    private Scene observedScene;

    @FXML private Label nameLabel;
    @FXML private Label standardServingLabel;
    @FXML private Label dishTypeLabel;
    @FXML private FlowPane tastesContainer;
    @FXML private Spinner<Integer> servingSpinner;
    @FXML private GridPane detailMetaGrid;
    @FXML private GridPane detailSectionsGrid;
    @FXML private VBox servingSection;
    @FXML private VBox tasteSection;
    @FXML private VBox timeSection;
    @FXML private Label preparationTimeValue;
    @FXML private Label cookingTimeValue;
    @FXML private Label bakingTimeValue;
    @FXML private Label restingTimeValue;
    @FXML private Label totalTimeValue;
    @FXML private VBox nutritionSection;
    @FXML private GridPane nutritionContainer;
    @FXML private VBox ingredientsSection;
    @FXML private VBox ingredientsContainer;
    @FXML private VBox stepsSection;
    @FXML private VBox stepsContainer;
    @FXML private Label emptyStepsLabel;

    public RecipeDetailController(RecipeRepository recipeRepository, RecipeScaler recipeScaler) {
        this.recipeRepository = Objects.requireNonNull(
                recipeRepository, "Recipe repository must not be null.");
        this.recipeScaler = Objects.requireNonNull(recipeScaler, "Recipe scaler must not be null.");
    }

    @FXML
    private void initialize() {
        detailSectionsGrid.sceneProperty().addListener((ignored, previous, current) -> {
            if (previous != null) {
                previous.getRoot().getStyleClass().removeListener(viewportListener);
            }
            observedScene = current;
            if (current != null) {
                current.getRoot().getStyleClass().addListener(viewportListener);
            }
            applyResponsiveLayout();
        });
    }

    @Override
    public void setNavigator(ViewNavigator navigator) {
        this.navigator = Objects.requireNonNull(navigator, "Navigator must not be null.");
    }

    /** Supplies the selected recipe after FXML loading and resets transient choices. */
    public void showRecipe(Recipe recipe) {
        this.recipe = Objects.requireNonNull(recipe, "Recipe must not be null.");
        ingredientModel = new RecipeDetailIngredientModel(recipe, recipeScaler);
        nameLabel.setText(recipe.getName());
        standardServingLabel.setText("Standard: " + servingText(recipe.getStandardServingCount()));
        dishTypeLabel.setText(GermanRecipeDisplay.dishType(recipe.getDishType()));
        renderTastes();
        renderTimes();
        renderNutrition();
        renderSteps();
        configureServingSelection();
        applyResponsiveLayout();
    }

    @FXML private void backToRecipes() { navigator.navigateTo(ViewType.RECIPES); }

    @FXML private void editRecipe() { navigator.navigateToRecipeEdit(recipe); }

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
        ButtonType deleteButton = new ButtonType("Löschen", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Abbrechen", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Zutaten und Geschmacksrichtungen bleiben erhalten.", cancelButton, deleteButton);
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
        values.valueProperty().addListener((ignored, previous, selected) -> {
            ingredientModel.setServingCount(selected);
            renderIngredients();
        });
        renderIngredients();
    }

    private void renderTastes() {
        tastesContainer.getChildren().clear();
        if (recipe.getTastes().isEmpty()) {
            tastesContainer.getChildren().add(emptyLabel("Keine Geschmacksrichtung angegeben."));
            return;
        }
        recipe.getTastes().forEach(taste -> {
            Label chip = new Label(taste.getName());
            chip.getStyleClass().add("selection-chip");
            tastesContainer.getChildren().add(chip);
        });
    }

    private void renderIngredients() {
        ingredientsContainer.getChildren().clear();
        List<RecipeDetailIngredientModel.IngredientRow> rows = ingredientModel.rows();
        if (rows.isEmpty()) {
            ingredientsContainer.getChildren().add(emptyLabel("Keine Zutaten angegeben."));
            return;
        }
        rows.forEach(row -> ingredientsContainer.getChildren().add(ingredientRow(row)));
    }

    private HBox ingredientRow(RecipeDetailIngredientModel.IngredientRow row) {
        Region name = row.hasAlternatives()
                ? alternativePicker(row)
                : ingredientName(row.ingredientName());
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);
        Label quantity = new Label(row.quantity());
        quantity.getStyleClass().add("detail-ingredient-quantity");
        HBox result = new HBox(12, name, quantity);
        result.getStyleClass().addAll("detail-item-block", "detail-ingredient-row");
        return result;
    }

    private static Label ingredientName(String ingredientName) {
        Label name = new Label(ingredientName);
        name.setWrapText(true);
        name.getStyleClass().add("detail-ingredient-name");
        return name;
    }

    private ComboBox<RecipeDetailIngredientModel.Alternative> alternativePicker(
            RecipeDetailIngredientModel.IngredientRow row) {
        ComboBox<RecipeDetailIngredientModel.Alternative> alternatives = new ComboBox<>();
        alternatives.getItems().setAll(row.alternatives());
        alternatives.setValue(row.alternatives().stream()
                .filter(option -> option.id().equals(row.selectedOptionId()))
                .findFirst().orElseThrow());
        alternatives.getStyleClass().add("detail-alternative-picker");
        alternatives.setOnAction(ignored -> {
            var selected = alternatives.getValue();
            if (selected != null) {
                ingredientModel.selectOption(row.groupId(), selected.id());
                renderIngredients();
            }
        });
        return alternatives;
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
            HBox.setHgrow(description, Priority.ALWAYS);
            HBox row = new HBox(12, position, description);
            row.getStyleClass().addAll("detail-item-block", "detail-step-row");
            stepsContainer.getChildren().add(row);
        });
    }

    private void renderTimes() {
        setTime(preparationTimeValue, recipe.getPreparationTime());
        setTime(cookingTimeValue, recipe.getCookingTime());
        setTime(bakingTimeValue, recipe.getBakingTime());
        setTime(restingTimeValue, recipe.getRestingTime());
        setTime(totalTimeValue, recipe.getTotalTime());
    }

    static List<TimeDisplay> timeDisplays(Recipe recipe) {
        Objects.requireNonNull(recipe, "Recipe must not be null.");
        return List.of(
                new TimeDisplay("Vorbereitung", displayTime(recipe.getPreparationTime())),
                new TimeDisplay("Kochen", displayTime(recipe.getCookingTime())),
                new TimeDisplay("Backen", displayTime(recipe.getBakingTime())),
                new TimeDisplay("Ruhezeit", displayTime(recipe.getRestingTime())),
                new TimeDisplay("Gesamt", displayTime(recipe.getTotalTime())));
    }

    private static void setTime(Label label, Optional<Duration> duration) {
        label.setText(displayTime(duration));
    }

    private static String displayTime(Optional<Duration> duration) {
        return duration.map(GermanRecipeDisplay::duration).orElse("–");
    }

    private void renderNutrition() {
        nutritionContainer.getChildren().clear();
        recipe.getNutritionInfo().stream()
                .flatMap(nutrition -> nutritionDisplays(nutrition).stream())
                .forEach(this::addNutritionItem);
        boolean hasNutrition = !nutritionContainer.getChildren().isEmpty();
        nutritionSection.setManaged(hasNutrition);
        nutritionSection.setVisible(hasNutrition);
    }

    static List<NutritionDisplay> nutritionDisplays(NutritionInfo nutrition) {
        Objects.requireNonNull(nutrition, "Nutrition information must not be null.");
        List<NutritionDisplay> displays = new ArrayList<>();
        nutrition.getCaloriesKcal().ifPresent(value -> displays.add(
                new NutritionDisplay("Kalorien", value + " kcal")));
        addNutritionDisplay(displays, "Protein", nutrition.getProteinGrams());
        addNutritionDisplay(displays, "Kohlenhydrate", nutrition.getCarbohydrateGrams());
        addNutritionDisplay(displays, "Fett", nutrition.getFatGrams());
        return List.copyOf(displays);
    }

    private static void addNutritionDisplay(List<NutritionDisplay> displays, String label,
                                            Optional<BigDecimal> grams) {
        grams.ifPresent(value -> displays.add(new NutritionDisplay(label,
                GermanRecipeDisplay.decimal(value) + " g")));
    }

    private void addNutritionItem(NutritionDisplay display) {
        VBox item = new VBox(3);
        item.getStyleClass().addAll("detail-item-block", "detail-nutrition-item");
        Label name = new Label(display.label());
        name.getStyleClass().add("detail-nutrition-label");
        Label displayedValue = new Label(display.value());
        displayedValue.getStyleClass().add("detail-nutrition-value");
        item.getChildren().addAll(name, displayedValue);
        int index = nutritionContainer.getChildren().size();
        nutritionContainer.add(item, index % 2, index / 2);
        GridPane.setHgrow(item, Priority.ALWAYS);
    }

    private void applyResponsiveLayout() {
        if (detailMetaGrid == null || detailSectionsGrid == null) {
            return;
        }
        boolean compact = observedScene != null
                && observedScene.getRoot().getStyleClass().contains("viewport-compact");
        configureColumns(detailMetaGrid, compact);
        configureColumns(detailSectionsGrid, compact);
        if (compact) {
            place(servingSection, 0, 0);
            place(tasteSection, 0, 1);
            place(timeSection, 0, 0);
            place(nutritionSection, 0, 1);
            place(ingredientsSection, 0, 2);
            place(stepsSection, 0, 3);
        } else {
            place(servingSection, 0, 0);
            place(tasteSection, 1, 0);
            place(timeSection, 0, 0);
            place(nutritionSection, 1, 0);
            place(ingredientsSection, 0, 1);
            place(stepsSection, 1, 1);
        }
        GridPane.setColumnSpan(timeSection, !compact && !nutritionSection.isManaged() ? 2 : 1);
    }

    private static void configureColumns(GridPane grid, boolean compact) {
        grid.getColumnConstraints().clear();
        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(compact ? 100 : 45);
        grid.getColumnConstraints().add(left);
        if (!compact) {
            ColumnConstraints right = new ColumnConstraints();
            right.setPercentWidth(55);
            grid.getColumnConstraints().add(right);
        }
    }

    private static void place(Region node, int column, int row) {
        GridPane.setColumnIndex(node, column);
        GridPane.setRowIndex(node, row);
        GridPane.setHgrow(node, Priority.ALWAYS);
        GridPane.setVgrow(node, Priority.NEVER);
        node.setMaxWidth(Double.MAX_VALUE);
    }

    private static Label emptyLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("card-text");
        return label;
    }

    private static String servingText(int count) {
        return count + (count == 1 ? " Person" : " Personen");
    }

    enum DeletionOutcome { CANCELLED, DELETED, NOT_FOUND }

    record TimeDisplay(String label, String value) { }

    record NutritionDisplay(String label, String value) { }
}
