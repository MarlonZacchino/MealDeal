package de.mealdeal.ui.controller;

import de.mealdeal.ui.navigation.NavigationAware;
import de.mealdeal.ui.navigation.ViewNavigator;
import de.mealdeal.ui.navigation.ViewType;
import javafx.fxml.FXML;

import java.util.Objects;

/** Routes the start-page shortcut buttons to existing main views. */
public final class HomeController implements NavigationAware {

    private ViewNavigator navigator;

    @Override
    public void setNavigator(ViewNavigator navigator) {
        this.navigator = Objects.requireNonNull(navigator, "Navigator must not be null.");
    }

    @FXML
    private void openSearch() {
        navigator.navigateTo(ViewType.SEARCH);
    }

    @FXML
    private void openRecipes() {
        navigator.navigateTo(ViewType.RECIPES);
    }

    @FXML
    private void openCreateRecipe() {
        navigator.navigateTo(ViewType.CREATE_RECIPE);
    }

    @FXML
    private void openWeekPlan() {
        navigator.navigateTo(ViewType.WEEK_PLAN);
    }

    @FXML
    private void openShopping() {
        navigator.navigateTo(ViewType.SHOPPING);
    }
}
