package de.mealdeal.ui.controller;

import de.mealdeal.ui.ApplicationContext;
import de.mealdeal.ui.navigation.ViewNavigator;
import de.mealdeal.ui.navigation.ViewType;
import de.mealdeal.ui.theme.ThemeMode;
import de.mealdeal.ui.theme.ThemeService;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Handles navigation events for the persistent application sidebar. */
public final class MainController {

    private static final String ACTIVE_STYLE_CLASS = "nav-button-active";
    private static final String DARK_THEME_STYLE_CLASS = "theme-dark";
    private static final String COMPACT_VIEWPORT_STYLE_CLASS = "viewport-compact";
    private static final String WIDE_VIEWPORT_STYLE_CLASS = "viewport-wide";
    private static final String EXTRA_WIDE_VIEWPORT_STYLE_CLASS = "viewport-extra-wide";
    private static final double COMPACT_VIEWPORT_MAX_WIDTH = 1100;
    private static final double WIDE_VIEWPORT_MIN_WIDTH = 1440;
    private static final double EXTRA_WIDE_VIEWPORT_MIN_WIDTH = 2100;

    private final ApplicationContext applicationContext;
    private final ThemeService themeService;
    private final Map<ViewType, Button> navigationButtons = new EnumMap<>(ViewType.class);
    private final ChangeListener<Number> viewportWidthListener = (ignored, oldWidth, newWidth) ->
            updateViewportStyleClasses(newWidth.doubleValue());

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
    private Button ingredientsButton;
    @FXML
    private Button inventoryButton;
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
        navigationButtons.put(ViewType.INGREDIENTS, ingredientsButton);
        navigationButtons.put(ViewType.INVENTORY, inventoryButton);
        navigationButtons.put(ViewType.SHOPPING, shoppingButton);
        applyTheme(themeService.getMode());
        configureResponsiveViewportStyles();

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
    private void showIngredients() {
        navigator.navigateTo(ViewType.INGREDIENTS);
    }

    @FXML
    private void showInventory() {
        navigator.navigateTo(ViewType.INVENTORY);
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

    private void configureResponsiveViewportStyles() {
        rootShell.sceneProperty().addListener((ignored, previousScene, currentScene) -> {
            if (previousScene != null) {
                previousScene.widthProperty().removeListener(viewportWidthListener);
            }
            if (currentScene != null) {
                currentScene.widthProperty().addListener(viewportWidthListener);
                updateViewportStyleClasses(currentScene.getWidth());
            }
        });

        Scene scene = rootShell.getScene();
        if (scene != null) {
            scene.widthProperty().addListener(viewportWidthListener);
            updateViewportStyleClasses(scene.getWidth());
        }
    }

    private void updateViewportStyleClasses(double sceneWidth) {
        rootShell.getStyleClass().removeAll(
                COMPACT_VIEWPORT_STYLE_CLASS, WIDE_VIEWPORT_STYLE_CLASS,
                EXTRA_WIDE_VIEWPORT_STYLE_CLASS);
        rootShell.getStyleClass().addAll(viewportStyleClassesFor(sceneWidth));
    }

    static List<String> viewportStyleClassesFor(double sceneWidth) {
        if (sceneWidth < COMPACT_VIEWPORT_MAX_WIDTH) {
            return List.of(COMPACT_VIEWPORT_STYLE_CLASS);
        }
        if (sceneWidth >= EXTRA_WIDE_VIEWPORT_MIN_WIDTH) {
            return List.of(WIDE_VIEWPORT_STYLE_CLASS, EXTRA_WIDE_VIEWPORT_STYLE_CLASS);
        }
        if (sceneWidth >= WIDE_VIEWPORT_MIN_WIDTH) {
            return List.of(WIDE_VIEWPORT_STYLE_CLASS);
        }
        return List.of();
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
