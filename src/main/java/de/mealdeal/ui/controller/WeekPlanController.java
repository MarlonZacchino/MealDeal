package de.mealdeal.ui.controller;

import de.mealdeal.domain.DishType;
import de.mealdeal.domain.Recipe;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.service.MealPlanDay;
import de.mealdeal.service.WeeklyMealPlanDayDraft;
import de.mealdeal.service.WeeklyMealPlanService;
import de.mealdeal.ui.navigation.NavigationAware;
import de.mealdeal.ui.navigation.ViewNavigator;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Coordinates weekly-plan loading, drafts, UI events, navigation and saving. */
public final class WeekPlanController implements NavigationAware {

    private static final System.Logger LOGGER =
            System.getLogger(WeekPlanController.class.getName());

    private final WeeklyMealPlanService mealPlanService;
    private final Map<LocalDate, MealPlanDay> loadedDays = new LinkedHashMap<>();
    private final Map<LocalDate, WeeklyMealPlanDayDraft> dayDrafts = new LinkedHashMap<>();
    private final Map<LocalDate, WeekPlanDayViewState> dayViewStates = new LinkedHashMap<>();
    private final WeekPlanDayCardFactory dayCardFactory = new WeekPlanDayCardFactory();
    private final MealRoleSectionFactory roleSectionFactory =
            new MealRoleSectionFactory(new MealPlanEntryRowFactory());
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

    @FXML
    private void initialize() {
        saveChangesButton.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        saveChangesMessage.setMaxSize(360, Region.USE_PREF_SIZE);
        refresh();
    }

    /** Reloads recipes and the persisted state for the current calendar week. */
    @FXML
    public void refresh() {
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

    @FXML
    private void saveChanges() {
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
        weekRangeLabel.setText(WeekPlanDayCardFactory.formatDate(days.getFirst().date())
                + " – " + WeekPlanDayCardFactory.formatDate(days.getLast().date()));
        dayCardsContainer.getChildren().setAll(days.stream().map(this::createDayCard).toList());
        updateSaveButtonState();
    }

    private TitledPane createDayCard(MealPlanDay day) {
        LocalDate date = day.date();
        WeeklyMealPlanDayDraft draft = dayDrafts.get(date);
        WeekPlanDayViewState viewState = dayViewStates.get(date);
        boolean editing = viewState.isEditing();

        VBox mainSection = roleSectionFactory.mainSection(draft.getMainEntry(), editing,
                mainRecipes, mainActions(date, draft), this::openRecipe);
        VBox sideSection = roleSectionFactory.sideSection(draft.getSideEntries(), editing,
                sideRecipes, sideActions(date, draft), this::openRecipe);
        VBox dessertSection = roleSectionFactory.dessertSection(draft.getDessertEntries(), editing,
                dessertRecipes, dessertActions(date, draft), this::openRecipe);

        return dayCardFactory.create(day, viewState.summary(draft), viewState.isExpanded(),
                editing, draft.isChanged(), mainSection, sideSection, dessertSection,
                () -> {
                    viewState.setEditing(!viewState.isEditing());
                    rerenderDay(date);
                }, viewState::setExpanded);
    }

    private MealRoleSectionFactory.MainActions mainActions(
            LocalDate date, WeeklyMealPlanDayDraft draft) {
        return new MealRoleSectionFactory.MainActions(
                recipe -> changeDay(date, () -> draft.setMainRecipe(recipe)),
                servings -> changeDay(date, () -> {
                    if (draft.getMainEntry().isPresent()) {
                        draft.setMainServingCount(servings);
                    }
                }),
                () -> changeDay(date, () -> draft.setMainRecipe(null)),
                (groupId, optionId) -> changeDay(date,
                        () -> draft.setMainIngredientOption(groupId, optionId)));
    }

    private MealRoleSectionFactory.OrderedRoleActions sideActions(
            LocalDate date, WeeklyMealPlanDayDraft draft) {
        return new MealRoleSectionFactory.OrderedRoleActions(
                () -> changeDay(date, () -> draft.addSide(sideRecipes.getFirst())),
                (index, recipe) -> changeDay(date, () -> draft.setSideRecipe(index, recipe)),
                (index, servings) -> changeDay(date,
                        () -> draft.setSideServingCount(index, servings)),
                index -> changeDay(date, () -> draft.removeSide(index)),
                index -> changeDay(date, () -> draft.moveSideUp(index)),
                index -> changeDay(date, () -> draft.moveSideDown(index)),
                (index, groupId, optionId) -> changeDay(date,
                        () -> draft.setSideIngredientOption(index, groupId, optionId)));
    }

    private MealRoleSectionFactory.OrderedRoleActions dessertActions(
            LocalDate date, WeeklyMealPlanDayDraft draft) {
        return new MealRoleSectionFactory.OrderedRoleActions(
                () -> changeDay(date, () -> draft.addDessert(dessertRecipes.getFirst())),
                (index, recipe) -> changeDay(date,
                        () -> draft.setDessertRecipe(index, recipe)),
                (index, servings) -> changeDay(date,
                        () -> draft.setDessertServingCount(index, servings)),
                index -> changeDay(date, () -> draft.removeDessert(index)),
                index -> changeDay(date, () -> draft.moveDessertUp(index)),
                index -> changeDay(date, () -> draft.moveDessertDown(index)),
                (index, groupId, optionId) -> changeDay(date,
                        () -> draft.setDessertIngredientOption(index, groupId, optionId)));
    }

    private void changeDay(LocalDate date, Runnable change) {
        change.run();
        rerenderDay(date);
    }

    private void rerenderDay(LocalDate date) {
        MealPlanDay day = loadedDays.get(date);
        int index = new java.util.ArrayList<>(loadedDays.keySet()).indexOf(date);
        dayCardsContainer.getChildren().set(index, createDayCard(day));
        updateSaveButtonState();
    }

    private void updateSaveButtonState() {
        saveChangesButton.setDisable(dayDrafts.values().stream()
                .noneMatch(WeeklyMealPlanDayDraft::isChanged));
    }

    private void showContent() {
        dayCardsContainer.setManaged(true);
        dayCardsContainer.setVisible(true);
        saveChangesButton.setManaged(true);
        saveChangesButton.setVisible(true);
        errorState.setManaged(false);
        errorState.setVisible(false);
    }

    private void showLoadError() {
        dayCardsContainer.setManaged(false);
        dayCardsContainer.setVisible(false);
        saveChangesButton.setManaged(false);
        saveChangesButton.setVisible(false);
        clearSaveMessage();
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
}
