package de.mealdeal.ui.controller;

import de.mealdeal.ui.navigation.NavigationAware;
import de.mealdeal.ui.navigation.ViewNavigator;
import de.mealdeal.ui.navigation.ViewType;
import javafx.fxml.FXML;

import java.util.Objects;
import java.util.function.Consumer;

/** Routes the start-page shortcut buttons to existing main views. */
public final class HomeController implements NavigationAware {

    private Consumer<ViewType> navigation;

    public HomeController() {
        navigation = ignored -> {
            throw new IllegalStateException("Navigator has not been configured.");
        };
    }

    HomeController(Consumer<ViewType> navigation) {
        this.navigation = Objects.requireNonNull(navigation, "Navigation must not be null.");
    }

    @Override
    public void setNavigator(ViewNavigator navigator) {
        ViewNavigator configuredNavigator = Objects.requireNonNull(
                navigator, "Navigator must not be null.");
        navigation = configuredNavigator::navigateTo;
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
}
