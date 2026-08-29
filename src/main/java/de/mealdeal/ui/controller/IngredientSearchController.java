package de.mealdeal.ui.controller;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.Recipe;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.repository.IngredientRepository;
import de.mealdeal.persistence.repository.RecipeRepository;
import de.mealdeal.service.IngredientSearchResult;
import de.mealdeal.service.MatchQuality;
import de.mealdeal.service.RecipeSearchService;
import de.mealdeal.ui.navigation.NavigationAware;
import de.mealdeal.ui.navigation.ViewNavigator;
import de.mealdeal.ui.search.IngredientSearchModel;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** Controls ingredient selection and renders existing RecipeSearchService results. */
public final class IngredientSearchController implements NavigationAware {

    private static final System.Logger LOGGER =
            System.getLogger(IngredientSearchController.class.getName());

    private final IngredientSearchModel searchModel;
    private final List<Ingredient> availableIngredients = new ArrayList<>();
    private Consumer<Recipe> detailNavigation;

    @FXML
    private TextField ingredientFilterField;
    @FXML
    private FlowPane availableIngredientsContainer;
    @FXML
    private FlowPane selectedIngredientsContainer;
    @FXML
    private Label selectionCountLabel;
    @FXML
    private Label searchMessage;
    @FXML
    private Button searchButton;
    @FXML
    private VBox resultsContainer;
    @FXML
    private VBox initialState;
    @FXML
    private VBox emptyState;
    @FXML
    private VBox errorState;

    /** Creates the controller with the repositories and existing search service. */
    public IngredientSearchController(IngredientRepository ingredientRepository,
                                      RecipeRepository recipeRepository,
                                      RecipeSearchService recipeSearchService) {
        this(new IngredientSearchModel(
                ingredientRepository, recipeRepository, recipeSearchService),
                ignored -> {
                    throw new IllegalStateException("Navigator has not been set.");
                });
    }

    IngredientSearchController(IngredientSearchModel searchModel,
                               Consumer<Recipe> detailNavigation) {
        this.searchModel = Objects.requireNonNull(searchModel, "Search model must not be null.");
        this.detailNavigation = Objects.requireNonNull(
                detailNavigation, "Detail navigation must not be null.");
    }

    @Override
    public void setNavigator(ViewNavigator navigator) {
        Objects.requireNonNull(navigator, "Navigator must not be null.");
        detailNavigation = navigator::navigateToRecipeDetail;
    }

    @FXML
    private void initialize() {
        ingredientFilterField.textProperty().addListener(
                (ignored, previous, current) -> renderAvailableIngredients());
        boolean ingredientsLoaded = loadIngredients();
        renderSelection();
        if (ingredientsLoaded) {
            setResultState(initialState);
        }
    }

    @FXML
    private void search() {
        clearMessage();
        try {
            showResults(searchModel.search());
        } catch (IllegalArgumentException exception) {
            showMessage("Wähle mindestens eine Zutat aus.");
        } catch (PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not search recipes.", exception);
            setResultState(errorState);
        }
    }

    void openRecipe(Recipe recipe) {
        detailNavigation.accept(Objects.requireNonNull(recipe, "Recipe must not be null."));
    }

    private boolean loadIngredients() {
        try {
            availableIngredients.clear();
            availableIngredients.addAll(searchModel.loadAvailableIngredients());
            renderAvailableIngredients();
            return true;
        } catch (PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not load ingredients.", exception);
            ingredientFilterField.setDisable(true);
            searchButton.setDisable(true);
            showMessage("Die Zutaten konnten nicht geladen werden.");
            setResultState(errorState);
            return false;
        }
    }

    private void selectIngredient(Ingredient ingredient) {
        IngredientSearchModel.SelectionResult result = searchModel.select(ingredient);
        if (result == IngredientSearchModel.SelectionResult.LIMIT_REACHED) {
            showMessage("Du kannst höchstens 10 Zutaten auswählen.");
            return;
        }
        clearMessage();
        renderSelection();
        renderAvailableIngredients();
    }

    private void removeIngredient(Ingredient ingredient) {
        searchModel.remove(ingredient);
        clearMessage();
        renderSelection();
        renderAvailableIngredients();
        setResultState(initialState);
    }

    private void renderAvailableIngredients() {
        availableIngredientsContainer.getChildren().clear();
        String filter = normalized(ingredientFilterField.getText());
        List<Ingredient> selected = searchModel.getSelectedIngredients();
        List<Ingredient> visible = availableIngredients.stream()
                .filter(ingredient -> !selected.contains(ingredient))
                .filter(ingredient -> normalized(ingredient.getName()).contains(filter))
                .toList();
        if (visible.isEmpty()) {
            Label noIngredients = new Label(filter.isEmpty()
                    ? "Keine weiteren Zutaten verfügbar."
                    : "Keine passende Zutat gefunden.");
            noIngredients.getStyleClass().add("card-text");
            availableIngredientsContainer.getChildren().add(noIngredients);
            return;
        }
        visible.forEach(ingredient -> {
            Button option = new Button(ingredient.getName());
            option.setOnAction(ignored -> selectIngredient(ingredient));
            option.getStyleClass().add("ingredient-option");
            availableIngredientsContainer.getChildren().add(option);
        });
    }

    private void renderSelection() {
        selectedIngredientsContainer.getChildren().clear();
        List<Ingredient> selected = searchModel.getSelectedIngredients();
        selectionCountLabel.setText(selected.size() + "/"
                + IngredientSearchModel.MAX_SELECTED_INGREDIENTS + " ausgewählt");
        if (selected.isEmpty()) {
            Label instruction = new Label("Noch keine Zutaten ausgewählt.");
            instruction.getStyleClass().add("card-text");
            selectedIngredientsContainer.getChildren().add(instruction);
            return;
        }
        selected.forEach(ingredient -> {
            Button chip = new Button(ingredient.getName() + "  ×");
            chip.setAccessibleText(ingredient.getName() + " aus Auswahl entfernen");
            chip.setOnAction(ignored -> removeIngredient(ingredient));
            chip.getStyleClass().add("selected-ingredient-chip");
            selectedIngredientsContainer.getChildren().add(chip);
        });
    }

    private void showResults(List<IngredientSearchResult> results) {
        resultsContainer.getChildren().clear();
        if (results.isEmpty()) {
            setResultState(emptyState);
            return;
        }
        results.forEach(result -> resultsContainer.getChildren().add(resultEntry(result)));
        setResultState(resultsContainer);
    }

    private Button resultEntry(IngredientSearchResult result) {
        Label name = new Label(result.getRecipe().getName());
        name.getStyleClass().add("recipe-name");

        Label quality = new Label(result.getMatchQuality().name());
        quality.getStyleClass().addAll("match-quality", qualityStyle(result.getMatchQuality()));
        Label count = new Label(result.getMatchedCount() + "/" + result.getSelectedCount()
                + " Zutaten vorhanden");
        count.getStyleClass().add("recipe-facts");
        HBox match = new HBox(10, quality, count);
        match.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(8, name, match);
        if (!result.getMissingIngredients().isEmpty()) {
            Label missing = new Label("Fehlt: " + result.getMissingIngredients().stream()
                    .map(Ingredient::getName)
                    .reduce((first, second) -> first + ", " + second)
                    .orElse(""));
            missing.setWrapText(true);
            missing.getStyleClass().add("search-missing");
            content.getChildren().add(missing);
        }
        content.setAlignment(Pos.CENTER_LEFT);
        content.setMaxHeight(Region.USE_PREF_SIZE);

        Button entry = new Button();
        entry.setGraphic(content);
        entry.setMaxWidth(Double.MAX_VALUE);
        entry.setAlignment(Pos.CENTER_LEFT);
        entry.setAccessibleText("Gericht " + result.getRecipe().getName() + " öffnen");
        entry.setOnAction(ignored -> openRecipe(result.getRecipe()));
        entry.getStyleClass().add("search-result-item");
        return entry;
    }

    private void setResultState(VBox visibleState) {
        for (VBox state : List.of(resultsContainer, initialState, emptyState, errorState)) {
            boolean visible = state == visibleState;
            state.setManaged(visible);
            state.setVisible(visible);
        }
    }

    private void showMessage(String message) {
        searchMessage.setText(message);
        searchMessage.setManaged(true);
        searchMessage.setVisible(true);
    }

    private void clearMessage() {
        searchMessage.setText("");
        searchMessage.setManaged(false);
        searchMessage.setVisible(false);
    }

    private static String qualityStyle(MatchQuality quality) {
        return switch (quality) {
            case PERFECT -> "match-perfect";
            case GOOD -> "match-good";
            case PARTIAL -> "match-partial";
        };
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
