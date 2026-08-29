package de.mealdeal.ui.controller;

import de.mealdeal.ui.ApplicationContext;
import de.mealdeal.ui.navigation.ViewNavigator;
import de.mealdeal.ui.navigation.ViewType;
import de.mealdeal.ui.theme.ThemeMode;
import de.mealdeal.ui.theme.ThemeService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Handles navigation events for the persistent application sidebar. */
public final class MainController {

    private static final String ACTIVE_STYLE_CLASS = "nav-button-active";
    private static final String DARK_THEME_STYLE_CLASS = "theme-dark";

    private final ApplicationContext applicationContext;
    private final ThemeService themeService;
    private final Map<ViewType, Button> navigationButtons = new EnumMap<>(ViewType.class);

    @FXML
    private BorderPane rootShell;
    @FXML
    private StackPane contentHost;
    @FXML
    private Button homeButton;
    @FXML
    private Button recipesButton;
    @FXML
    private Button searchButton;
    @FXML
    private Button weekPlanButton;
    @FXML
    private Button shoppingButton;
    @FXML
    private ToggleButton themeToggle;

    private ViewNavigator navigator;

    public MainController(ApplicationContext applicationContext, ThemeService themeService) {
        this.applicationContext = Objects.requireNonNull(
                applicationContext, "Application context must not be null.");
        this.themeService = Objects.requireNonNull(
                themeService, "Theme service must not be null.");
    }

    @FXML
    private void initialize() {
        navigationButtons.put(ViewType.HOME, homeButton);
        navigationButtons.put(ViewType.RECIPES, recipesButton);
        navigationButtons.put(ViewType.SEARCH, searchButton);
        navigationButtons.put(ViewType.WEEK_PLAN, weekPlanButton);
        navigationButtons.put(ViewType.SHOPPING, shoppingButton);
        applyTheme(themeService.getMode());

        navigator = new ViewNavigator(contentHost, applicationContext);
        navigator.setNavigationListener(this::markActiveView);
        navigator.navigateTo(ViewType.HOME);
    }

    @FXML
    private void showHome() {
        navigator.navigateTo(ViewType.HOME);
    }

    @FXML
    private void showRecipes() {
        navigator.navigateTo(ViewType.RECIPES);
    }

    @FXML
    private void showSearch() {
        navigator.navigateTo(ViewType.SEARCH);
    }

    @FXML
    private void showWeekPlan() {
        navigator.navigateTo(ViewType.WEEK_PLAN);
    }

    @FXML
    private void showShopping() {
        navigator.navigateTo(ViewType.SHOPPING);
    }

    @FXML
    private void toggleTheme() {
        applyTheme(themeService.toggle());
    }

    private void applyTheme(ThemeMode mode) {
        rootShell.getStyleClass().remove(DARK_THEME_STYLE_CLASS);
        boolean dark = mode == ThemeMode.DARK;
        if (dark) {
            rootShell.getStyleClass().add(DARK_THEME_STYLE_CLASS);
        }
        themeToggle.setSelected(dark);
        themeToggle.setText(dark ? "Dark Mode" : "Light Mode");
        themeToggle.setAccessibleText(dark
                ? "Aktuell Dark Mode. Zu Light Mode wechseln."
                : "Aktuell Light Mode. Zu Dark Mode wechseln.");
    }

    private void markActiveView(ViewType activeView) {
        ViewType activeNavigationView = activeView == ViewType.CREATE_RECIPE
                || activeView == ViewType.RECIPE_DETAIL
                ? ViewType.RECIPES : activeView;
        navigationButtons.forEach((viewType, button) -> {
            button.getStyleClass().remove(ACTIVE_STYLE_CLASS);
            if (viewType == activeNavigationView) {
                button.getStyleClass().add(ACTIVE_STYLE_CLASS);
            }
        });
    }
}
