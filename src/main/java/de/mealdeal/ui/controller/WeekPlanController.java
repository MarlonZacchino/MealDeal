package de.mealdeal.ui.controller;

import de.mealdeal.domain.DishType;
import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredientOption;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.service.MealPlanDay;
import de.mealdeal.service.WeeklyMealPlanDayDraft;
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
import javafx.scene.control.TitledPane;
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
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

/** Renders editable main, side and dessert planning state for the current week. */
public final class WeekPlanController implements NavigationAware {

    private static final System.Logger LOGGER =
            System.getLogger(WeekPlanController.class.getName());
    private static final int MAX_SERVING_COUNT = 999;
    private static final DateTimeFormatter DAY_NAME =
            DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN);
    private static final DateTimeFormatter FULL_DATE =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN);
    private static final StringConverter<Recipe> RECIPE_CONVERTER = new StringConverter<>() {
        @Override public String toString(Recipe recipe) { return recipe == null ? "" : recipe.getName(); }
        @Override public Recipe fromString(String value) {
            throw new UnsupportedOperationException("Recipe selection is not editable.");
        }
    };
    private static final StringConverter<RecipeIngredientOption> OPTION_CONVERTER =
            new StringConverter<>() {
                @Override public String toString(RecipeIngredientOption option) {
                    return option == null ? "" : GermanRecipeDisplay.quantity(
                            option.getQuantity(), option.getUnit()) + " "
                            + option.getIngredient().getName();
                }
                @Override public RecipeIngredientOption fromString(String value) {
                    throw new UnsupportedOperationException(
                            "Ingredient option selection is not editable.");
                }
            };

    private final WeeklyMealPlanService mealPlanService;
    private final Map<LocalDate, MealPlanDay> loadedDays = new LinkedHashMap<>();
    private final Map<LocalDate, WeeklyMealPlanDayDraft> dayDrafts = new LinkedHashMap<>();
    private final Map<LocalDate, WeekPlanDayViewState> dayViewStates = new LinkedHashMap<>();
    private Consumer<Recipe> detailNavigation;
    private List<Recipe> mainRecipes = List.of();
    private List<Recipe> sideRecipes = List.of();
    private List<Recipe> dessertRecipes = List.of();

    @FXML private Label weekRangeLabel;
    @FXML private VBox dayCardsContainer;
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

    @FXML private void initialize() {
        saveChangesButton.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        saveChangesMessage.setMaxSize(360, Region.USE_PREF_SIZE);
        refresh();
    }

    /** Reloads recipes and the persisted state for the current calendar week. */
    @FXML public void refresh() {
        try {
            mainRecipes = mealPlanService.loadAvailableRecipes(DishType.MAIN);
            sideRecipes = mealPlanService.loadAvailableRecipes(DishType.SIDE);
            dessertRecipes = mealPlanService.loadAvailableRecipes(DishType.DESSERT);
            renderWeek(mealPlanService.loadCurrentWeek());
            showContent();
            clearSaveMessage();
        } catch (PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not load weekly meal plan.", exception);
            showLoadError();
        }
    }

    @FXML private void saveChanges() {
        try {
            mealPlanService.saveChanges(dayDrafts.values().stream()
                    .map(WeeklyMealPlanDayDraft::toSaveSnapshot).toList());
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

    private void renderWeek(List<MealPlanDay> days) {
        if (days.size() != 7) {
            throw new IllegalStateException("Current week must contain seven days.");
        }
        loadedDays.clear();
        dayDrafts.clear();
        dayViewStates.clear();
        for (MealPlanDay day : days) {
            loadedDays.put(day.date(), day);
            dayDrafts.put(day.date(), new WeeklyMealPlanDayDraft(day));
            dayViewStates.put(day.date(), new WeekPlanDayViewState());
        }
        weekRangeLabel.setText(FULL_DATE.format(days.getFirst().date())
                + " – " + FULL_DATE.format(days.getLast().date()));
        dayCardsContainer.getChildren().setAll(days.stream().map(this::createDayCard).toList());
        updateSaveButtonState();
    }

    private TitledPane createDayCard(MealPlanDay day) {
        WeeklyMealPlanDayDraft draft = dayDrafts.get(day.date());
        WeekPlanDayViewState viewState = dayViewStates.get(day.date());
        VBox content = new VBox(16);
        content.setMaxWidth(Double.MAX_VALUE);
        content.getStyleClass().add("meal-plan-day-content");
        Label localChangeNote = new Label(draft.isChanged()
                ? "Ungespeicherte Änderungen." : "");
        localChangeNote.setManaged(draft.isChanged());
        localChangeNote.setVisible(draft.isChanged());
        localChangeNote.setWrapText(true);
        localChangeNote.getStyleClass().add("meal-plan-unsaved-note");
        content.getChildren().addAll(mainSection(day.date(), draft),
                sideSection(day.date(), draft), dessertSection(day.date(), draft), localChangeNote);

        Label summary = new Label(viewState.summary(draft));
        summary.setWrapText(true);
        summary.getStyleClass().add("meal-plan-day-summary");
        VBox title = new VBox(5, dayHeader(day), summary);
        title.setMaxWidth(Double.MAX_VALUE);
        title.getStyleClass().add("meal-plan-day-title");

        TitledPane card = new TitledPane();
        card.setGraphic(title);
        card.setContent(content);
        card.setExpanded(viewState.isExpanded());
        card.setAnimated(true);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().addAll("card", "expandable-pane", "meal-plan-day-card");
        if (day.today()) {
            card.getStyleClass().add("meal-plan-day-today");
        }
        card.expandedProperty().addListener((ignored, previous, expanded) ->
                viewState.setExpanded(expanded));
        return card;
    }

    private HBox dayHeader(MealPlanDay day) {
        Label dayName = new Label(titleCase(DAY_NAME.format(day.date())));
        dayName.getStyleClass().add("meal-plan-day-name");
        Label date = new Label(FULL_DATE.format(day.date()));
        date.getStyleClass().add("meal-plan-date");
        HBox header = new HBox(14, new VBox(3, dayName, date));
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

    private VBox mainSection(LocalDate date, WeeklyMealPlanDayDraft draft) {
        VBox section = new VBox(10);
        section.getStyleClass().add("meal-plan-role-section");
        Label title = new Label("Hauptgericht");
        title.getStyleClass().add("section-title");
        MealPlanEntry entry = draft.getMainEntry().orElse(null);
        if (entry == null) {
            Label empty = new Label("Kein Hauptgericht geplant.");
            empty.getStyleClass().add("card-text");
            section.getChildren().add(empty);
        } else {
            section.getChildren().add(recipeLink(entry));
        }

        ComboBox<Recipe> selection = recipeBox(mainRecipes, "Hauptgericht auswählen");
        selection.setValue(entry == null ? null : entry.getRecipe());
        Spinner<Integer> servings = servingSpinner(entry == null
                ? Recipe.DEFAULT_SERVING_COUNT : entry.getServingCount());
        servings.setDisable(entry == null);
        selection.valueProperty().addListener((ignored, previous, selected) -> {
            draft.setMainRecipe(selected);
            rerenderDay(date);
        });
        servings.valueProperty().addListener((ignored, previous, selected) -> {
            if (draft.getMainEntry().isPresent()) {
                draft.setMainServingCount(selected);
                rerenderDay(date);
            }
        });

        FlowPane controls = controls(labeledControl("Hauptgericht", selection),
                labeledControl("Personen", servings));
        if (entry != null) {
            Button remove = new Button("Hauptgericht entfernen");
            remove.getStyleClass().add("danger-button");
            remove.setOnAction(ignored -> {
                draft.setMainRecipe(null);
                rerenderDay(date);
            });
            controls.getChildren().add(remove);
        }
        section.getChildren().add(controls);
        if (entry != null) {
            VBox alternatives = alternativeSelections(entry, (groupId, optionId) -> {
                draft.setMainIngredientOption(groupId, optionId);
                rerenderDay(date);
            });
            if (!alternatives.getChildren().isEmpty()) {
                section.getChildren().add(alternatives);
            }
        }
        return section;
    }

    private VBox sideSection(LocalDate date, WeeklyMealPlanDayDraft draft) {
        VBox section = new VBox(10);
        section.getStyleClass().add("meal-plan-role-section");
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Beilagen");
        title.getStyleClass().add("section-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button add = new Button("Beilage hinzufügen");
        add.getStyleClass().add("secondary-button");
        add.setDisable(sideRecipes.isEmpty());
        add.setOnAction(ignored -> {
            draft.addSide(sideRecipes.getFirst());
            rerenderDay(date);
        });
        header.getChildren().addAll(title, spacer, add);
        section.getChildren().add(header);

        if (draft.getSideEntries().isEmpty()) {
            Label empty = new Label(sideRecipes.isEmpty()
                    ? "Keine Beilagen-Rezepte verfügbar." : "Keine Beilage geplant.");
            empty.getStyleClass().add("card-text");
            section.getChildren().add(empty);
        } else {
            VBox rows = new VBox(10);
            for (int index = 0; index < draft.getSideEntries().size(); index++) {
                rows.getChildren().add(sideRow(date, draft, index));
            }
            section.getChildren().add(rows);
        }
        return section;
    }

    private VBox sideRow(LocalDate date, WeeklyMealPlanDayDraft draft, int index) {
        MealPlanEntry side = draft.getSideEntries().get(index);
        ComboBox<Recipe> selection = recipeBox(sideRecipes, "Beilage auswählen");
        selection.setValue(side.getRecipe());
        Spinner<Integer> servings = servingSpinner(side.getServingCount());
        selection.valueProperty().addListener((ignored, previous, selected) -> {
            if (selected != null) {
                draft.setSideRecipe(index, selected);
                rerenderDay(date);
            }
        });
        servings.valueProperty().addListener((ignored, previous, selected) -> {
            draft.setSideServingCount(index, selected);
            rerenderDay(date);
        });
        Button up = new Button("↑");
        up.setDisable(index == 0);
        up.setAccessibleText("Beilage nach oben verschieben");
        up.setOnAction(ignored -> { draft.moveSideUp(index); rerenderDay(date); });
        Button down = new Button("↓");
        down.setDisable(index == draft.getSideEntries().size() - 1);
        down.setAccessibleText("Beilage nach unten verschieben");
        down.setOnAction(ignored -> { draft.moveSideDown(index); rerenderDay(date); });
        Button remove = new Button("Entfernen");
        remove.getStyleClass().add("danger-button");
        remove.setOnAction(ignored -> { draft.removeSide(index); rerenderDay(date); });

        FlowPane controls = controls(labeledControl("Beilage", selection),
                labeledControl("Personen", servings), up, down, remove);
        VBox row = new VBox(7, recipeLink(side), controls);
        VBox alternatives = alternativeSelections(side, (groupId, optionId) -> {
            draft.setSideIngredientOption(index, groupId, optionId);
            rerenderDay(date);
        });
        if (!alternatives.getChildren().isEmpty()) {
            row.getChildren().add(alternatives);
        }
        row.getStyleClass().add("meal-plan-side-row");
        return row;
    }

    private VBox dessertSection(LocalDate date, WeeklyMealPlanDayDraft draft) {
        VBox section = new VBox(10);
        section.getStyleClass().add("meal-plan-role-section");
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Nachtische");
        title.getStyleClass().add("section-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button add = new Button("Nachtisch hinzufügen");
        add.getStyleClass().add("secondary-button");
        add.setDisable(dessertRecipes.isEmpty());
        add.setOnAction(ignored -> {
            draft.addDessert(dessertRecipes.getFirst());
            rerenderDay(date);
        });
        header.getChildren().addAll(title, spacer, add);
        section.getChildren().add(header);

        if (draft.getDessertEntries().isEmpty()) {
            Label empty = new Label(dessertRecipes.isEmpty()
                    ? "Keine Nachtisch-Rezepte verfügbar." : "Kein Nachtisch geplant.");
            empty.getStyleClass().add("card-text");
            section.getChildren().add(empty);
        } else {
            VBox rows = new VBox(10);
            for (int index = 0; index < draft.getDessertEntries().size(); index++) {
                rows.getChildren().add(dessertRow(date, draft, index));
            }
            section.getChildren().add(rows);
        }
        return section;
    }

    private VBox dessertRow(LocalDate date, WeeklyMealPlanDayDraft draft, int index) {
        MealPlanEntry dessert = draft.getDessertEntries().get(index);
        ComboBox<Recipe> selection = recipeBox(dessertRecipes, "Nachtisch auswählen");
        selection.setValue(dessert.getRecipe());
        Spinner<Integer> servings = servingSpinner(dessert.getServingCount());
        selection.valueProperty().addListener((ignored, previous, selected) -> {
            if (selected != null) {
                draft.setDessertRecipe(index, selected);
                rerenderDay(date);
            }
        });
        servings.valueProperty().addListener((ignored, previous, selected) -> {
            draft.setDessertServingCount(index, selected);
            rerenderDay(date);
        });
        Button up = new Button("↑");
        up.setDisable(index == 0);
        up.setAccessibleText("Nachtisch nach oben verschieben");
        up.setOnAction(ignored -> { draft.moveDessertUp(index); rerenderDay(date); });
        Button down = new Button("↓");
        down.setDisable(index == draft.getDessertEntries().size() - 1);
        down.setAccessibleText("Nachtisch nach unten verschieben");
        down.setOnAction(ignored -> { draft.moveDessertDown(index); rerenderDay(date); });
        Button remove = new Button("Entfernen");
        remove.getStyleClass().add("danger-button");
        remove.setOnAction(ignored -> { draft.removeDessert(index); rerenderDay(date); });

        FlowPane controls = controls(labeledControl("Nachtisch", selection),
                labeledControl("Personen", servings), up, down, remove);
        VBox row = new VBox(7, recipeLink(dessert), controls);
        VBox alternatives = alternativeSelections(dessert, (groupId, optionId) -> {
            draft.setDessertIngredientOption(index, groupId, optionId);
            rerenderDay(date);
        });
        if (!alternatives.getChildren().isEmpty()) {
            row.getChildren().add(alternatives);
        }
        row.getStyleClass().add("meal-plan-dessert-row");
        return row;
    }

    private static VBox alternativeSelections(
            MealPlanEntry entry, BiConsumer<UUID, UUID> selectionHandler) {
        VBox container = new VBox(8);
        container.getStyleClass().add("meal-plan-ingredient-selections");
        entry.getRecipe().getIngredientGroups().stream()
                .filter(group -> group.getOptions().size() > 1)
                .forEach(group -> {
                    ComboBox<RecipeIngredientOption> selection = new ComboBox<>(
                            FXCollections.observableArrayList(group.getOptions()));
                    selection.setConverter(OPTION_CONVERTER);
                    selection.setValue(entry.getSelectedOption(group));
                    selection.setMaxWidth(Double.MAX_VALUE);
                    selection.getStyleClass().add("meal-plan-ingredient-picker");
                    selection.valueProperty().addListener((ignored, previous, selected) -> {
                        if (selected != null) {
                            selectionHandler.accept(group.getId(), selected.getId());
                        }
                    });
                    String groupName = group.getOptions().stream()
                            .map(option -> option.getIngredient().getName())
                            .reduce((first, second) -> first + " oder " + second)
                            .orElseThrow();
                    container.getChildren().add(labeledControl(groupName, selection));
                });
        return container;
    }

    private void rerenderDay(LocalDate date) {
        MealPlanDay day = loadedDays.get(date);
        int index = new java.util.ArrayList<>(loadedDays.keySet()).indexOf(date);
        dayCardsContainer.getChildren().set(index, createDayCard(day));
        updateSaveButtonState();
    }

    private ComboBox<Recipe> recipeBox(List<Recipe> recipes, String promptText) {
        ComboBox<Recipe> selection = new ComboBox<>(FXCollections.observableArrayList(recipes));
        selection.setConverter(RECIPE_CONVERTER);
        selection.setPromptText(recipes.isEmpty() ? "Keine Gerichte verfügbar" : promptText);
        selection.setDisable(recipes.isEmpty());
        selection.getStyleClass().add("meal-plan-recipe-picker");
        return selection;
    }

    private static Spinner<Integer> servingSpinner(int value) {
        Spinner<Integer> spinner = new Spinner<>();
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                1, MAX_SERVING_COUNT, value));
        spinner.setEditable(false);
        spinner.getStyleClass().add("meal-plan-serving-spinner");
        return spinner;
    }

    private Button recipeLink(MealPlanEntry entry) {
        Button link = new Button(entry.getRecipe().getName());
        link.setAccessibleText("Details zu " + entry.getRecipe().getName() + " öffnen");
        link.setOnAction(ignored -> openRecipe(entry.getRecipe()));
        link.getStyleClass().add("meal-plan-recipe-link");
        return link;
    }

    private static FlowPane controls(Node... nodes) {
        FlowPane controls = new FlowPane(12, 10);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.getStyleClass().add("meal-plan-controls");
        controls.getChildren().addAll(nodes);
        return controls;
    }

    private static VBox labeledControl(String text, Node control) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        return new VBox(6, label, control);
    }

    private void updateSaveButtonState() {
        saveChangesButton.setDisable(dayDrafts.values().stream()
                .noneMatch(WeeklyMealPlanDayDraft::isChanged));
    }

    private void showContent() {
        dayCardsContainer.setManaged(true); dayCardsContainer.setVisible(true);
        saveChangesButton.setManaged(true); saveChangesButton.setVisible(true);
        errorState.setManaged(false); errorState.setVisible(false);
    }

    private void showLoadError() {
        dayCardsContainer.setManaged(false); dayCardsContainer.setVisible(false);
        saveChangesButton.setManaged(false); saveChangesButton.setVisible(false);
        clearSaveMessage();
        errorMessage.setText("Die aktuelle Woche konnte nicht geladen werden. Bitte versuche es erneut.");
        errorState.setManaged(true); errorState.setVisible(true);
    }

    private void clearSaveMessage() {
        saveChangesMessage.setManaged(false); saveChangesMessage.setVisible(false);
        saveChangesMessage.setText("");
    }

    private void showSaveMessage(String message, boolean error) {
        saveChangesMessage.setText(message);
        saveChangesMessage.getStyleClass().removeAll("form-error", "form-message");
        saveChangesMessage.getStyleClass().add(error ? "form-error" : "form-message");
        saveChangesMessage.setManaged(true); saveChangesMessage.setVisible(true);
    }

    private static String titleCase(String value) {
        return value.isEmpty() ? value
                : value.substring(0, 1).toUpperCase(Locale.GERMAN) + value.substring(1);
    }
}
