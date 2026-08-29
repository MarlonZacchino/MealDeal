package de.mealdeal.ui.controller;

import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.Recipe;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.service.MealPlanDay;
import de.mealdeal.service.WeeklyMealPlanService;
import de.mealdeal.ui.navigation.NavigationAware;
import de.mealdeal.ui.navigation.ViewNavigator;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** Renders and edits the seven entries of the current weekly meal plan. */
public final class WeekPlanController implements NavigationAware {

    private static final System.Logger LOGGER =
            System.getLogger(WeekPlanController.class.getName());
    private static final int MAX_SERVING_COUNT = 999;
    private static final DateTimeFormatter DAY_NAME =
            DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN);
    private static final DateTimeFormatter FULL_DATE =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN);
    private static final StringConverter<Recipe> RECIPE_CONVERTER = new StringConverter<>() {
        @Override
        public String toString(Recipe recipe) {
            return recipe == null ? "" : recipe.getName();
        }

        @Override
        public Recipe fromString(String value) {
            throw new UnsupportedOperationException("Recipe selection is not editable.");
        }
    };

    private final WeeklyMealPlanService mealPlanService;
    private Consumer<Recipe> detailNavigation;

    @FXML
    private Label weekRangeLabel;
    @FXML
    private VBox dayCardsContainer;
    @FXML
    private VBox errorState;
    @FXML
    private Label errorMessage;

    /** Creates the controller with the application service used by the view. */
    public WeekPlanController(WeeklyMealPlanService mealPlanService) {
        this(mealPlanService, ignored -> {
            throw new IllegalStateException("Navigator has not been configured.");
        });
    }

    WeekPlanController(WeeklyMealPlanService mealPlanService,
                       Consumer<Recipe> detailNavigation) {
        this.mealPlanService = Objects.requireNonNull(
                mealPlanService, "Weekly meal plan service must not be null.");
        this.detailNavigation = Objects.requireNonNull(
                detailNavigation, "Detail navigation must not be null.");
    }

    @Override
    public void setNavigator(ViewNavigator navigator) {
        ViewNavigator configuredNavigator = Objects.requireNonNull(
                navigator, "Navigator must not be null.");
        detailNavigation = configuredNavigator::navigateToRecipeDetail;
    }

    @FXML
    private void initialize() {
        refresh();
    }

    /** Reloads recipes and all current-week entries from their repositories. */
    @FXML
    public void refresh() {
        try {
            List<Recipe> recipes = mealPlanService.loadAvailableRecipes();
            List<MealPlanDay> days = mealPlanService.loadCurrentWeek();
            renderWeek(days, recipes);
            showContent();
        } catch (PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not load weekly meal plan.", exception);
            showLoadError();
        }
    }

    void openRecipe(Recipe recipe) {
        detailNavigation.accept(Objects.requireNonNull(recipe, "Recipe must not be null."));
    }

    private void renderWeek(List<MealPlanDay> days, List<Recipe> recipes) {
        dayCardsContainer.getChildren().clear();
        if (days.isEmpty()) {
            throw new IllegalStateException("Current week must contain seven days.");
        }
        weekRangeLabel.setText(FULL_DATE.format(days.getFirst().date())
                + " – " + FULL_DATE.format(days.getLast().date()));
        days.forEach(day -> dayCardsContainer.getChildren().add(createDayCard(day, recipes)));
    }

    private VBox createDayCard(MealPlanDay day, List<Recipe> recipes) {
        VBox card = new VBox(16);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().addAll("card", "meal-plan-day-card");
        if (day.today()) {
            card.getStyleClass().add("meal-plan-day-today");
        }

        MealPlanEntry plannedEntry = day.entry().orElse(null);
        Label operationMessage = new Label();
        operationMessage.setManaged(false);
        operationMessage.setVisible(false);
        operationMessage.setWrapText(true);
        operationMessage.getStyleClass().add("form-message");

        card.getChildren().addAll(dayHeader(day), planState(plannedEntry, recipes.isEmpty()),
                planningControls(day.date(), plannedEntry, recipes, operationMessage),
                operationMessage);
        return card;
    }

    private HBox dayHeader(MealPlanDay day) {
        Label dayName = new Label(titleCase(DAY_NAME.format(day.date())));
        dayName.getStyleClass().add("meal-plan-day-name");
        Label date = new Label(FULL_DATE.format(day.date()));
        date.getStyleClass().add("meal-plan-date");
        VBox dateBlock = new VBox(3, dayName, date);

        HBox header = new HBox(14, dateBlock);
        header.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        header.getChildren().add(spacer);
        if (day.today()) {
            Label today = new Label("Heute");
            today.getStyleClass().add("meal-plan-today-badge");
            header.getChildren().add(today);
        }
        return header;
    }

    private VBox planState(MealPlanEntry plannedEntry, boolean noRecipesAvailable) {
        if (plannedEntry == null) {
            Label emptyTitle = new Label("Noch nichts geplant.");
            emptyTitle.getStyleClass().add("meal-plan-empty-title");
            Label instruction = new Label(noRecipesAvailable
                    ? "Lege zuerst ein Gericht an, bevor du diesen Tag planst."
                    : "Wähle ein Gericht und die gewünschte Personenanzahl aus.");
            instruction.setWrapText(true);
            instruction.getStyleClass().add("card-text");
            return new VBox(5, emptyTitle, instruction);
        }

        Recipe recipe = plannedEntry.getRecipe();
        Button recipeLink = new Button(recipe.getName());
        recipeLink.setAccessibleText("Details zu " + recipe.getName() + " öffnen");
        recipeLink.setOnAction(ignored -> openRecipe(recipe));
        recipeLink.getStyleClass().add("meal-plan-recipe-link");
        Label servings = new Label("Geplant für " + servingText(plannedEntry.getServingCount()));
        servings.getStyleClass().add("card-text");
        return new VBox(5, recipeLink, servings);
    }

    private FlowPane planningControls(LocalDate date, MealPlanEntry plannedEntry,
                                      List<Recipe> recipes, Label operationMessage) {
        ComboBox<Recipe> recipeSelection = new ComboBox<>(
                FXCollections.observableArrayList(recipes));
        recipeSelection.setConverter(RECIPE_CONVERTER);
        recipeSelection.setPromptText(recipes.isEmpty()
                ? "Keine Gerichte verfügbar" : "Gericht auswählen");
        recipeSelection.setDisable(recipes.isEmpty());
        recipeSelection.setAccessibleText("Gericht für diesen Tag auswählen");
        recipeSelection.getStyleClass().add("meal-plan-recipe-picker");

        int initialServingCount = plannedEntry == null
                ? Recipe.DEFAULT_SERVING_COUNT : plannedEntry.getServingCount();
        Spinner<Integer> servingSelection = new Spinner<>();
        servingSelection.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                1, MAX_SERVING_COUNT, initialServingCount));
        servingSelection.setEditable(false);
        servingSelection.setDisable(recipes.isEmpty());
        servingSelection.setAccessibleText("Geplante Personenanzahl");
        servingSelection.getStyleClass().add("meal-plan-serving-spinner");

        if (plannedEntry != null) {
            recipeSelection.setValue(plannedEntry.getRecipe());
        }
        recipeSelection.valueProperty().addListener((ignored, previous, selected) -> {
            if (plannedEntry == null && selected != null) {
                servingSelection.getValueFactory().setValue(
                        selected.getStandardServingCount());
            }
        });

        Button save = new Button(plannedEntry == null ? "Gericht planen" : "Änderungen speichern");
        save.disableProperty().bind(recipeSelection.valueProperty().isNull());
        save.getStyleClass().add("primary-button");
        save.setOnAction(ignored -> savePlan(date, recipeSelection.getValue(),
                servingSelection.getValue(), operationMessage));

        FlowPane controls = new FlowPane(12, 12);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.getStyleClass().add("meal-plan-controls");
        controls.getChildren().addAll(labeledControl("Gericht", recipeSelection),
                labeledControl("Personen", servingSelection), save);
        if (plannedEntry != null) {
            Button remove = new Button("Planung entfernen");
            remove.getStyleClass().add("danger-button");
            remove.setOnAction(ignored -> removePlan(date, operationMessage));
            controls.getChildren().add(remove);
        }
        return controls;
    }

    private static VBox labeledControl(String text, javafx.scene.Node control) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        return new VBox(6, label, control);
    }

    private void savePlan(LocalDate date, Recipe recipe, int servingCount,
                          Label operationMessage) {
        try {
            mealPlanService.plan(date, recipe, servingCount);
            refresh();
        } catch (IllegalArgumentException | PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not save meal plan entry.", exception);
            showOperationError(operationMessage,
                    "Die Planung konnte nicht gespeichert werden. Bitte versuche es erneut.");
        }
    }

    private void removePlan(LocalDate date, Label operationMessage) {
        try {
            mealPlanService.remove(date);
            refresh();
        } catch (IllegalArgumentException | PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not remove meal plan entry.", exception);
            showOperationError(operationMessage,
                    "Die Planung konnte nicht entfernt werden. Bitte versuche es erneut.");
        }
    }

    private void showContent() {
        dayCardsContainer.setManaged(true);
        dayCardsContainer.setVisible(true);
        errorState.setManaged(false);
        errorState.setVisible(false);
    }

    private void showLoadError() {
        dayCardsContainer.setManaged(false);
        dayCardsContainer.setVisible(false);
        errorMessage.setText(
                "Die aktuelle Woche konnte nicht geladen werden. Bitte versuche es erneut.");
        errorState.setManaged(true);
        errorState.setVisible(true);
    }

    private static void showOperationError(Label label, String message) {
        label.setText(message);
        label.setManaged(true);
        label.setVisible(true);
    }

    private static String servingText(int servingCount) {
        return servingCount + (servingCount == 1 ? " Person" : " Personen");
    }

    private static String titleCase(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.GERMAN) + value.substring(1);
    }
}
