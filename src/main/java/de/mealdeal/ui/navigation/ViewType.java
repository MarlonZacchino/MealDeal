package de.mealdeal.ui.navigation;

/** Fixed navigation destinations of the MealDeal application shell. */
public enum ViewType {
    HOME("/de/mealdeal/ui/home-view.fxml"),
    RECIPES("/de/mealdeal/ui/recipes-view.fxml"),
    SEARCH("/de/mealdeal/ui/search-view.fxml"),
    WEEK_PLAN("/de/mealdeal/ui/week-plan-view.fxml"),
    SHOPPING("/de/mealdeal/ui/shopping-view.fxml");

    private final String resourcePath;

    ViewType(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    /** Returns the classpath location of this destination's FXML file. */
    public String getResourcePath() {
        return resourcePath;
    }
}
