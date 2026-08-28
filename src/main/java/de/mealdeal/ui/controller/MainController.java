package de.mealdeal.ui.controller;

import de.mealdeal.ui.ApplicationContext;
import de.mealdeal.ui.navigation.ViewNavigator;
import de.mealdeal.ui.navigation.ViewType;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Handles navigation events for the persistent application sidebar. */
public final class MainController {

    private static final String ACTIVE_STYLE_CLASS = "nav-button-active";

    private final ApplicationContext applicationContext;
    private final Map<ViewType, Button> navigationButtons = new EnumMap<>(ViewType.class);

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

    private ViewNavigator navigator;

    public MainController(ApplicationContext applicationContext) {
        this.applicationContext = Objects.requireNonNull(
                applicationContext, "Application context must not be null.");
    }

    @FXML
    private void initialize() {
        navigationButtons.put(ViewType.HOME, homeButton);
        navigationButtons.put(ViewType.RECIPES, recipesButton);
        navigationButtons.put(ViewType.SEARCH, searchButton);
        navigationButtons.put(ViewType.WEEK_PLAN, weekPlanButton);
        navigationButtons.put(ViewType.SHOPPING, shoppingButton);

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

    private void markActiveView(ViewType activeView) {
        navigationButtons.forEach((viewType, button) -> {
            button.getStyleClass().remove(ACTIVE_STYLE_CLASS);
            if (viewType == activeView) {
                button.getStyleClass().add(ACTIVE_STYLE_CLASS);
            }
        });
    }
}
