package de.mealdeal.ui.controller;

import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.Recipe;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.service.MealPlanDay;
import de.mealdeal.service.MealPlanDraft;
import de.mealdeal.service.WeeklyMealPlanService;
import de.mealdeal.ui.navigation.NavigationAware;
import de.mealdeal.ui.navigation.ViewNavigator;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Renders the current week and keeps all local day edits until one shared save. */
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
    private final Map<LocalDate, DayDraft> dayDrafts = new LinkedHashMap<>();
    private Consumer<Recipe> detailNavigation;

    @FXML private Label weekRangeLabel;
    @FXML private VBox dayCardsContainer;
    @FXML private HBox weekSaveBar;
    @FXML private Button saveChangesButton;
    @FXML private Label saveChangesMessage;
    @FXML private VBox errorState;
    @FXML private Label errorMessage;

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
            clearSaveMessage();
        } catch (PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not load weekly meal plan.", exception);
            showLoadError();
        }
    }

    @FXML
    private void saveChanges() {
        try {
            mealPlanService.saveChanges(dayDrafts.values().stream()
                    .map(DayDraft::toMealPlanDraft)
                    .toList());
            refresh();
            showSaveMessage("Änderungen wurden gespeichert.", false);
        } catch (IllegalArgumentException | PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not save weekly meal plan changes.",
                    exception);
            showSaveMessage("Die Änderungen konnten nicht gespeichert werden. "
                    + "Bitte versuche es erneut.", true);
        }
    }

    void openRecipe(Recipe recipe) {
        detailNavigation.accept(Objects.requireNonNull(recipe, "Recipe must not be null."));
    }

    private void renderWeek(List<MealPlanDay> days, List<Recipe> recipes) {
        if (days.size() != 7) {
            throw new IllegalStateException("Current week must contain seven days.");
        }
        dayDrafts.clear();
        days.forEach(day -> dayDrafts.put(day.date(), new DayDraft(day.date(), day.entry().orElse(null))));
        weekRangeLabel.setText(FULL_DATE.format(days.getFirst().date())
                + " – " + FULL_DATE.format(days.getLast().date()));
        dayCardsContainer.getChildren().setAll(days.stream()
                .map(day -> createDayCard(day, recipes, dayDrafts.get(day.date())))
                .toList());
        updateSaveButtonState();
    }

    private VBox createDayCard(MealPlanDay day, List<Recipe> recipes, DayDraft draft) {
        VBox card = new VBox(16);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().addAll("card", "meal-plan-day-card");
        if (day.today()) {
            card.getStyleClass().add("meal-plan-day-today");
        }

        Label localChangeNote = new Label();
        localChangeNote.setManaged(false);
        localChangeNote.setVisible(false);
        localChangeNote.setWrapText(true);
        localChangeNote.getStyleClass().add("meal-plan-unsaved-note");

        card.getChildren().addAll(dayHeader(day), planState(draft.original(), recipes.isEmpty()),
                planningControls(recipes, draft, localChangeNote), localChangeNote);
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
        HBox.setHgrow(spacer, Priority.ALWAYS);
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

    private FlowPane planningControls(List<Recipe> recipes, DayDraft draft,
                                      Label localChangeNote) {
        ComboBox<Recipe> recipeSelection = new ComboBox<>(
                FXCollections.observableArrayList(recipes));
        recipeSelection.setConverter(RECIPE_CONVERTER);
        recipeSelection.setPromptText(recipes.isEmpty()
                ? "Keine Gerichte verfügbar" : "Gericht auswählen");
        recipeSelection.setDisable(recipes.isEmpty());
        recipeSelection.setAccessibleText("Gericht für diesen Tag auswählen");
        recipeSelection.getStyleClass().add("meal-plan-recipe-picker");
        recipeSelection.setValue(draft.recipe());

        Spinner<Integer> servingSelection = new Spinner<>();
        servingSelection.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                1, MAX_SERVING_COUNT, draft.servingCount()));
        servingSelection.setEditable(false);
        servingSelection.setDisable(recipes.isEmpty());
        servingSelection.setAccessibleText("Geplante Personenanzahl");
        servingSelection.getStyleClass().add("meal-plan-serving-spinner");

        Button remove = draft.original() == null ? null : new Button();
        recipeSelection.valueProperty().addListener((ignored, previous, selected) -> {
            if (draft.original() == null && previous == null && selected != null) {
                servingSelection.getValueFactory().setValue(selected.getStandardServingCount());
            }
            draft.setRecipe(selected);
            updateLocalChangeStatus(draft, localChangeNote, remove);
            updateSaveButtonState();
        });
        servingSelection.valueProperty().addListener((ignored, previous, selected) -> {
            draft.setServingCount(selected);
            updateLocalChangeStatus(draft, localChangeNote, remove);
            updateSaveButtonState();
        });

        if (remove != null) {
            remove.getStyleClass().add("danger-button");
            remove.setOnAction(ignored -> {
                if (draft.recipe() == null) {
                    recipeSelection.setValue(draft.original().getRecipe());
                    servingSelection.getValueFactory().setValue(
                            draft.original().getServingCount());
                } else {
                    recipeSelection.setValue(null);
                }
            });
        }

        FlowPane controls = new FlowPane(12, 12);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.getStyleClass().add("meal-plan-controls");
        controls.getChildren().addAll(labeledControl("Gericht", recipeSelection),
                labeledControl("Personen", servingSelection));
        if (remove != null) {
            controls.getChildren().add(remove);
        }
        updateLocalChangeStatus(draft, localChangeNote, remove);
        return controls;
    }

    private void updateLocalChangeStatus(DayDraft draft, Label note, Button remove) {
        if (!draft.isChanged()) {
            note.setManaged(false);
            note.setVisible(false);
            if (remove != null) {
                remove.setText("Planung entfernen");
            }
            return;
        }
        if (draft.recipe() == null) {
            note.setText("Planung wird beim Speichern entfernt.");
            if (remove != null) {
                remove.setText("Entfernen rückgängig");
            }
        } else if (draft.original() == null) {
            note.setText("Neue Planung wird beim Speichern angelegt.");
        } else {
            note.setText("Änderungen werden beim Speichern übernommen.");
            if (remove != null) {
                remove.setText("Planung entfernen");
            }
        }
        note.setManaged(true);
        note.setVisible(true);
    }

    private static VBox labeledControl(String text, Node control) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        return new VBox(6, label, control);
    }

    private void updateSaveButtonState() {
        saveChangesButton.setDisable(dayDrafts.values().stream().noneMatch(DayDraft::isChanged));
    }

    private void showContent() {
        dayCardsContainer.setManaged(true);
        dayCardsContainer.setVisible(true);
        weekSaveBar.setManaged(true);
        weekSaveBar.setVisible(true);
        errorState.setManaged(false);
        errorState.setVisible(false);
    }

    private void showLoadError() {
        dayCardsContainer.setManaged(false);
        dayCardsContainer.setVisible(false);
        weekSaveBar.setManaged(false);
        weekSaveBar.setVisible(false);
        errorMessage.setText(
                "Die aktuelle Woche konnte nicht geladen werden. Bitte versuche es erneut.");
        errorState.setManaged(true);
        errorState.setVisible(true);
    }

    private void clearSaveMessage() {
        saveChangesMessage.setManaged(false);
        saveChangesMessage.setVisible(false);
        saveChangesMessage.setText("");
    }

    private void showSaveMessage(String message, boolean error) {
        saveChangesMessage.setText(message);
        saveChangesMessage.getStyleClass().removeAll("form-error", "form-message");
        saveChangesMessage.getStyleClass().add(error ? "form-error" : "form-message");
        saveChangesMessage.setManaged(true);
        saveChangesMessage.setVisible(true);
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

    private static final class DayDraft {
        private final LocalDate date;
        private final MealPlanEntry original;
        private Recipe recipe;
        private int servingCount;

        private DayDraft(LocalDate date, MealPlanEntry original) {
            this.date = date;
            this.original = original;
            recipe = original == null ? null : original.getRecipe();
            servingCount = original == null ? Recipe.DEFAULT_SERVING_COUNT
                    : original.getServingCount();
        }

        private MealPlanEntry original() { return original; }
        private Recipe recipe() { return recipe; }
        private void setRecipe(Recipe recipe) { this.recipe = recipe; }
        private int servingCount() { return servingCount; }
        private void setServingCount(int servingCount) { this.servingCount = servingCount; }

        private boolean isChanged() {
            return original == null ? recipe != null : recipe == null
                    || !original.getRecipe().getId().equals(recipe.getId())
                    || original.getServingCount() != servingCount;
        }

        private MealPlanDraft toMealPlanDraft() {
            return new MealPlanDraft(date, Optional.ofNullable(recipe), servingCount);
        }
    }
}
