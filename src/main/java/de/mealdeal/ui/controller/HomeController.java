package de.mealdeal.ui.controller;

import de.mealdeal.domain.MealRole;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.service.MealPlanDay;
import de.mealdeal.service.WeeklyMealPlanService;
import de.mealdeal.ui.navigation.NavigationAware;
import de.mealdeal.ui.navigation.ViewNavigator;
import de.mealdeal.ui.navigation.ViewType;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Renders the current plan summary and routes start-page actions to existing views. */
public final class HomeController implements NavigationAware {

    private static final System.Logger LOGGER = System.getLogger(HomeController.class.getName());
    private static final DateTimeFormatter WEEKDAY =
            DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN);
    private static final DateTimeFormatter SHORT_DATE =
            DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMAN);

    private final Supplier<List<MealPlanDay>> currentWeekLoader;
    private Consumer<ViewType> navigation;

    @FXML private VBox todayPlanContent;
    @FXML private VBox todayEmptyState;
    @FXML private VBox todayErrorState;
    @FXML private VBox weekOverviewContainer;
    @FXML private VBox weekErrorState;

    /** Creates the controller with the existing weekly meal-plan application service. */
    public HomeController(WeeklyMealPlanService mealPlanService) {
        this(Objects.requireNonNull(mealPlanService,
                "Weekly meal plan service must not be null.")::loadCurrentWeek);
    }

    /** Preserves the FXML fallback constructor for isolated resource loading. */
    public HomeController() {
        this(() -> {
            throw new IllegalStateException("Weekly meal plan service has not been configured.");
        });
    }

    private HomeController(Supplier<List<MealPlanDay>> currentWeekLoader) {
        this.currentWeekLoader = Objects.requireNonNull(
                currentWeekLoader, "Current-week loader must not be null.");
        navigation = ignored -> {
            throw new IllegalStateException("Navigator has not been configured.");
        };
    }

    HomeController(Consumer<ViewType> navigation) {
        currentWeekLoader = List::of;
        this.navigation = Objects.requireNonNull(navigation, "Navigation must not be null.");
    }

    @Override
    public void setNavigator(ViewNavigator navigator) {
        ViewNavigator configuredNavigator = Objects.requireNonNull(
                navigator, "Navigator must not be null.");
        navigation = configuredNavigator::navigateTo;
    }

    @FXML
    private void initialize() {
        refresh();
    }

    /** Reloads the complete current-week plan used by the start-page summaries. */
    @FXML
    public void refresh() {
        try {
            renderCurrentWeek(currentWeekLoader.get());
            showPlanContent();
        } catch (PersistenceException | IllegalStateException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not load start-page meal plan.", exception);
            showPlanLoadError();
        }
    }

    @FXML
    void openSearch() {
        navigation.accept(ViewType.SEARCH);
    }

    @FXML
    private void openRecipes() {
        navigation.accept(ViewType.RECIPES);
    }

    @FXML
    private void openCreateRecipe() {
        navigation.accept(ViewType.CREATE_RECIPE);
    }

    @FXML
    private void openWeekPlan() {
        navigation.accept(ViewType.WEEK_PLAN);
    }

    @FXML
    private void openShopping() {
        navigation.accept(ViewType.SHOPPING);
    }

    private void renderCurrentWeek(List<MealPlanDay> days) {
        Objects.requireNonNull(days, "Current-week days must not be null.");
        if (days.size() != 7) {
            throw new IllegalStateException("Current week must contain seven days.");
        }
        MealPlanDay today = days.stream().filter(MealPlanDay::today).findFirst()
                .orElseThrow(() -> new IllegalStateException("Current week must contain today."));
        renderToday(HomeMealPlanViewModel.from(today));
        weekOverviewContainer.getChildren().setAll(days.stream()
                .map(this::createWeekDay).toList());
    }

    private void renderToday(HomeMealPlanViewModel day) {
        todayPlanContent.getChildren().clear();
        if (day.isEmpty()) {
            todayPlanContent.setManaged(false);
            todayPlanContent.setVisible(false);
            todayEmptyState.setManaged(true);
            todayEmptyState.setVisible(true);
            return;
        }

        day.mainEntry().ifPresent(entry -> {
            todayPlanContent.getChildren().add(roleTitle("Hauptgericht"));
            todayPlanContent.getChildren().add(recipeEntry(entry, MealRole.MAIN));
        });
        if (!day.sideEntries().isEmpty()) {
            todayPlanContent.getChildren().add(roleTitle("Beilagen"));
            day.sideEntries().stream().map(entry -> recipeEntry(entry, MealRole.SIDE))
                    .forEach(todayPlanContent.getChildren()::add);
        }
        if (!day.dessertEntries().isEmpty()) {
            todayPlanContent.getChildren().add(roleTitle("Nachtische"));
            day.dessertEntries().stream().map(entry -> recipeEntry(entry, MealRole.DESSERT))
                    .forEach(todayPlanContent.getChildren()::add);
        }
        todayPlanContent.setManaged(true);
        todayPlanContent.setVisible(true);
        todayEmptyState.setManaged(false);
        todayEmptyState.setVisible(false);
    }

    private VBox createWeekDay(MealPlanDay day) {
        HomeMealPlanViewModel display = HomeMealPlanViewModel.from(day);
        Label title = new Label(titleCase(WEEKDAY.format(day.date())) + " · "
                + SHORT_DATE.format(day.date()));
        title.getStyleClass().add("home-week-day-title");
        VBox dayBox = new VBox(6, title);
        dayBox.setMaxWidth(Double.MAX_VALUE);
        dayBox.getStyleClass().add("home-week-day");
        if (day.today()) {
            dayBox.getStyleClass().add("home-week-day-today");
        }
        display.mainEntry().map(entry -> recipeEntry(entry, MealRole.MAIN))
                .ifPresent(dayBox.getChildren()::add);
        display.sideEntries().stream().map(entry -> recipeEntry(entry, MealRole.SIDE))
                .forEach(dayBox.getChildren()::add);
        display.dessertEntries().stream().map(entry -> recipeEntry(entry, MealRole.DESSERT))
                .forEach(dayBox.getChildren()::add);
        if (display.isEmpty()) {
            Label empty = new Label("Noch nichts geplant");
            empty.getStyleClass().add("card-text");
            dayBox.getChildren().add(empty);
        }
        return dayBox;
    }

    private static Label roleTitle(String text) {
        Label title = new Label(text);
        title.getStyleClass().add("home-plan-role-title");
        return title;
    }

    private static Label recipeEntry(HomeMealPlanViewModel.RecipeEntry entry, MealRole role) {
        String prefix = switch (role) {
            case MAIN -> "";
            case SIDE -> "+ ";
            case DESSERT -> "• ";
        };
        String styleClass = switch (role) {
            case MAIN -> "home-plan-main-entry";
            case SIDE -> "home-plan-side-entry";
            case DESSERT -> "home-plan-dessert-entry";
        };
        Label recipe = new Label(prefix + entry.recipeName() + " · "
                + servingText(entry.servingCount()));
        recipe.setMaxWidth(Double.MAX_VALUE);
        recipe.setWrapText(true);
        recipe.getStyleClass().add(styleClass);
        return recipe;
    }

    private void showPlanContent() {
        todayErrorState.setManaged(false);
        todayErrorState.setVisible(false);
        weekOverviewContainer.setManaged(true);
        weekOverviewContainer.setVisible(true);
        weekErrorState.setManaged(false);
        weekErrorState.setVisible(false);
    }

    private void showPlanLoadError() {
        todayPlanContent.setManaged(false);
        todayPlanContent.setVisible(false);
        todayEmptyState.setManaged(false);
        todayEmptyState.setVisible(false);
        todayErrorState.setManaged(true);
        todayErrorState.setVisible(true);
        weekOverviewContainer.setManaged(false);
        weekOverviewContainer.setVisible(false);
        weekErrorState.setManaged(true);
        weekErrorState.setVisible(true);
    }

    private static String servingText(int servingCount) {
        return servingCount + (servingCount == 1 ? " Portion" : " Portionen");
    }

    private static String titleCase(String text) {
        return text.isEmpty() ? text
                : text.substring(0, 1).toUpperCase(Locale.GERMAN) + text.substring(1);
    }
}
