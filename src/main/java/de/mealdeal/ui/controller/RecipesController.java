package de.mealdeal.ui.controller;

import de.mealdeal.domain.Recipe;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.repository.RecipeRepository;
import de.mealdeal.ui.navigation.NavigationAware;
import de.mealdeal.ui.navigation.ViewNavigator;
import de.mealdeal.ui.navigation.ViewType;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Region;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Loads recipes through the repository and renders their compact list state. */
public final class RecipesController implements NavigationAware {

    private static final System.Logger LOGGER = System.getLogger(RecipesController.class.getName());
    private static final double RECIPE_ENTRY_MAX_HEIGHT = 140;
    private static final Comparator<Recipe> RECIPE_ORDER = Comparator
            .comparing(Recipe::getName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Recipe::getName)
            .thenComparing(Recipe::getId);

    private final RecipeRepository recipeRepository;
    private final ToggleGroup selection = new ToggleGroup();
    private ViewNavigator navigator;

    @FXML
    private VBox recipeListContainer;
    @FXML
    private VBox emptyState;
    @FXML
    private VBox errorState;
    @FXML
    private Label errorMessage;

    /** Creates the controller with the repository used whenever the view is opened. */
    public RecipesController(RecipeRepository recipeRepository) {
        this.recipeRepository = Objects.requireNonNull(
                recipeRepository, "Recipe repository must not be null.");
    }

    @Override
    public void setNavigator(ViewNavigator navigator) {
        this.navigator = Objects.requireNonNull(navigator, "Navigator must not be null.");
    }

    @FXML
    private void initialize() {
        refresh();
    }

    /** Reloads the current repository data and updates the visible list state. */
    @FXML
    public void refresh() {
        try {
            showRecipes(loadSortedRecipes());
        } catch (PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not load recipes.", exception);
            showLoadError();
        }
    }

    @FXML
    private void openCreateRecipe() {
        navigator.navigateTo(ViewType.CREATE_RECIPE);
    }

    List<Recipe> loadSortedRecipes() {
        return recipeRepository.findAll().stream()
                .sorted(RECIPE_ORDER)
                .toList();
    }

    private void showRecipes(List<Recipe> recipes) {
        recipeListContainer.getChildren().clear();
        selection.selectToggle(null);

        if (recipes.isEmpty()) {
            setVisibleState(emptyState);
            return;
        }

        for (Recipe recipe : recipes) {
            recipeListContainer.getChildren().add(createRecipeEntry(recipe));
        }
        setVisibleState(recipeListContainer);
    }

    private ToggleButton createRecipeEntry(Recipe recipe) {
        Label name = new Label(recipe.getName());
        name.getStyleClass().add("recipe-name");
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);

        Label dishType = new Label(GermanRecipeDisplay.dishType(recipe.getDishType()));
        dishType.getStyleClass().add("recipe-type-badge");

        HBox heading = new HBox(12, name, dishType);
        heading.setAlignment(Pos.CENTER_LEFT);

        Label facts = new Label(recipeFacts(recipe));
        facts.getStyleClass().add("recipe-facts");
        facts.setWrapText(true);

        Label tastes = new Label(tasteSummary(recipe));
        tastes.getStyleClass().add("recipe-tastes");
        tastes.setWrapText(true);

        VBox summary = new VBox(6, heading, facts, tastes);
        summary.setAlignment(Pos.CENTER_LEFT);
        summary.setMaxHeight(Region.USE_PREF_SIZE);

        ToggleButton entry = new ToggleButton();
        entry.setGraphic(summary);
        entry.setToggleGroup(selection);
        entry.setMaxWidth(Double.MAX_VALUE);
        entry.setMaxHeight(RECIPE_ENTRY_MAX_HEIGHT);
        entry.setAlignment(Pos.CENTER_LEFT);
        entry.setUserData(recipe.getId());
        entry.setAccessibleText("Gericht " + recipe.getName());
        entry.getStyleClass().add("recipe-list-item");
        entry.setOnAction(ignored -> navigator.navigateToRecipeDetail(recipe));
        return entry;
    }

    private void showLoadError() {
        errorMessage.setText(
                "Die gespeicherten Gerichte konnten nicht geladen werden. Bitte versuche es erneut.");
        setVisibleState(errorState);
    }

    private void setVisibleState(VBox visibleState) {
        for (VBox state : List.of(recipeListContainer, emptyState, errorState)) {
            boolean visible = state == visibleState;
            state.setManaged(visible);
            state.setVisible(visible);
        }
    }

    private static String recipeFacts(Recipe recipe) {
        return servingText(recipe.getStandardServingCount())
                + "  ·  " + countText(recipe.getIngredients().size(), "Zutat", "Zutaten")
                + "  ·  " + countText(recipe.getSteps().size(), "Schritt", "Schritte");
    }

    private static String tasteSummary(Recipe recipe) {
        String names = recipe.getTastes().stream()
                .map(taste -> taste.getName())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .reduce((first, second) -> first + ", " + second)
                .orElse("Keine Angabe");
        return "Geschmack: " + names;
    }

    private static String servingText(int count) {
        return countText(count, "Person", "Personen");
    }

    private static String countText(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }
}
