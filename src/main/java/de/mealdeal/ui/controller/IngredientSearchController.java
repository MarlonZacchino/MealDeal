package de.mealdeal.ui.controller;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.Taste;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.repository.IngredientRepository;
import de.mealdeal.persistence.repository.RecipeRepository;
import de.mealdeal.persistence.repository.TasteRepository;
import de.mealdeal.service.CombinedRecipeSearchService;
import de.mealdeal.service.CombinedSearchResult;
import de.mealdeal.service.IngredientSearchResult;
import de.mealdeal.service.MatchQuality;
import de.mealdeal.service.RecipeSearchService;
import de.mealdeal.service.TasteFilterMode;
import de.mealdeal.service.TasteSearchResult;
import de.mealdeal.ui.navigation.NavigationAware;
import de.mealdeal.ui.navigation.ViewNavigator;
import de.mealdeal.ui.search.IngredientSearchModel;
import de.mealdeal.ui.search.TasteSearchModel;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
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

/** Controls ingredient and taste selection and renders existing search-service results. */
public final class IngredientSearchController implements NavigationAware {

    private static final System.Logger LOGGER =
            System.getLogger(IngredientSearchController.class.getName());

    private final IngredientSearchModel searchModel;
    private final TasteSearchModel tasteSearchModel;
    private final RecipeRepository recipeRepository;
    private final CombinedRecipeSearchService combinedSearchService;
    private final List<Ingredient> availableIngredients = new ArrayList<>();
    private final List<Taste> availableTastes = new ArrayList<>();
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
    private TextField tasteFilterField;
    @FXML
    private FlowPane availableTastesContainer;
    @FXML
    private FlowPane selectedTastesContainer;
    @FXML
    private Label tasteSelectionCountLabel;
    @FXML
    private RadioButton tasteAndMode;
    @FXML
    private RadioButton tasteOrMode;
    @FXML
    private RadioButton tasteRankingMode;
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
                                      TasteRepository tasteRepository,
                                      RecipeRepository recipeRepository,
                                      RecipeSearchService recipeSearchService,
                                      CombinedRecipeSearchService combinedSearchService) {
        this(new IngredientSearchModel(
                ingredientRepository, recipeRepository, recipeSearchService),
                new TasteSearchModel(
                        tasteRepository, recipeRepository, recipeSearchService),
                recipeRepository, combinedSearchService,
                ignored -> {
                    throw new IllegalStateException("Navigator has not been set.");
                });
    }

    IngredientSearchController(IngredientSearchModel searchModel,
                               TasteSearchModel tasteSearchModel,
                               RecipeRepository recipeRepository,
                               CombinedRecipeSearchService combinedSearchService,
                               Consumer<Recipe> detailNavigation) {
        this.searchModel = Objects.requireNonNull(searchModel, "Search model must not be null.");
        this.tasteSearchModel = Objects.requireNonNull(
                tasteSearchModel, "Taste search model must not be null.");
        this.recipeRepository = Objects.requireNonNull(
                recipeRepository, "Recipe repository must not be null.");
        this.combinedSearchService = Objects.requireNonNull(
                combinedSearchService, "Combined search service must not be null.");
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
        tasteFilterField.textProperty().addListener(
                (ignored, previous, current) -> renderAvailableTastes());
        boolean ingredientsLoaded = loadIngredients();
        boolean tastesLoaded = loadTastes();
        renderSelection();
        renderTasteSelection();
        if (ingredientsLoaded && tastesLoaded) {
            setResultState(initialState);
        }
    }

    @FXML
    private void search() {
        clearMessage();
        try {
            showResults(combinedSearchService.search(
                    recipeRepository.findAll(),
                    searchModel.getSelectedIngredients(),
                    tasteSearchModel.getSelectedTastes(),
                    selectedTasteMode()));
        } catch (IllegalArgumentException exception) {
            showMessage("Wähle mindestens eine Zutat oder Geschmacksrichtung aus.");
        } catch (PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not search recipes.", exception);
            setResultState(errorState);
        }
    }

    @FXML
    private void resetFilters() {
        searchModel.clear();
        tasteSearchModel.clear();
        ingredientFilterField.clear();
        tasteFilterField.clear();
        tasteRankingMode.setSelected(true);
        clearMessage();
        renderSelection();
        renderTasteSelection();
        renderAvailableIngredients();
        renderAvailableTastes();
        setResultState(initialState);
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
        setResultState(initialState);
    }

    private boolean loadTastes() {
        try {
            availableTastes.clear();
            availableTastes.addAll(tasteSearchModel.loadAvailableTastes());
            renderAvailableTastes();
            return true;
        } catch (PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not load tastes.", exception);
            tasteFilterField.setDisable(true);
            showMessage("Die Geschmacksrichtungen konnten nicht geladen werden.");
            setResultState(errorState);
            return false;
        }
    }

    private void selectTaste(Taste taste) {
        tasteSearchModel.select(taste);
        clearMessage();
        renderTasteSelection();
        renderAvailableTastes();
        setResultState(initialState);
    }

    private void removeTaste(Taste taste) {
        tasteSearchModel.remove(taste);
        clearMessage();
        renderTasteSelection();
        renderAvailableTastes();
        setResultState(initialState);
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

    private void renderAvailableTastes() {
        availableTastesContainer.getChildren().clear();
        String filter = normalized(tasteFilterField.getText());
        List<Taste> selected = tasteSearchModel.getSelectedTastes();
        List<Taste> visible = availableTastes.stream()
                .filter(taste -> !selected.contains(taste))
                .filter(taste -> normalized(taste.getName()).contains(filter))
                .toList();
        if (visible.isEmpty()) {
            Label noTastes = new Label(filter.isEmpty()
                    ? "Keine weiteren Geschmacksrichtungen verfügbar."
                    : "Keine passende Geschmacksrichtung gefunden.");
            noTastes.getStyleClass().add("card-text");
            availableTastesContainer.getChildren().add(noTastes);
            return;
        }
        visible.forEach(taste -> {
            Button option = new Button(taste.getName());
            option.setOnAction(ignored -> selectTaste(taste));
            option.getStyleClass().add("taste-search-option");
            availableTastesContainer.getChildren().add(option);
        });
    }

    private void renderTasteSelection() {
        selectedTastesContainer.getChildren().clear();
        List<Taste> selected = tasteSearchModel.getSelectedTastes();
        tasteSelectionCountLabel.setText(selected.size() + " ausgewählt");
        if (selected.isEmpty()) {
            Label instruction = new Label("Noch keine Geschmacksrichtung ausgewählt.");
            instruction.getStyleClass().add("card-text");
            selectedTastesContainer.getChildren().add(instruction);
            return;
        }
        selected.forEach(taste -> {
            Button chip = new Button(taste.getName() + "  ×");
            chip.setAccessibleText(taste.getName() + " aus Auswahl entfernen");
            chip.setOnAction(ignored -> removeTaste(taste));
            chip.getStyleClass().add("selected-taste-chip");
            selectedTastesContainer.getChildren().add(chip);
        });
    }

    private void showResults(List<CombinedSearchResult> results) {
        resultsContainer.getChildren().clear();
        if (results.isEmpty()) {
            setResultState(emptyState);
            return;
        }
        results.forEach(result -> resultsContainer.getChildren().add(resultEntry(result)));
        setResultState(resultsContainer);
    }

    private Button resultEntry(CombinedSearchResult result) {
        Recipe recipe = result.getRecipe();
        Label name = new Label(recipe.getName());
        name.getStyleClass().add("recipe-name");

        VBox content = new VBox(8, name);
        result.getIngredientResult().ifPresent(ingredientResult -> {
            content.getChildren().add(matchRow("Zutaten", ingredientResult.getMatchQuality(),
                    ingredientResult.getMatchedCount(), ingredientResult.getSelectedCount()));
            addMissingLabel(content, "Fehlende Zutaten", ingredientMissingText(ingredientResult));
        });
        result.getTasteResult().ifPresent(tasteResult -> {
            content.getChildren().add(matchRow(
                    "Geschmack", tasteResult.getMatchQuality(),
                    tasteResult.getMatchedCount(), tasteResult.getSelectedCount()));
            addMissingLabel(content, "Fehlende Geschmacksrichtungen",
                    tasteMissingText(tasteResult));
        });
        content.setAlignment(Pos.CENTER_LEFT);
        content.setMaxHeight(Region.USE_PREF_SIZE);

        Button entry = new Button();
        entry.setGraphic(content);
        entry.setMaxWidth(Double.MAX_VALUE);
        entry.setAlignment(Pos.CENTER_LEFT);
        entry.setAccessibleText("Gericht " + recipe.getName() + " öffnen");
        entry.setOnAction(ignored -> openRecipe(recipe));
        entry.getStyleClass().add("search-result-item");
        return entry;
    }

    private static HBox matchRow(String filterName, MatchQuality quality,
                                 int matchedCount, int selectedCount) {
        Label filter = new Label(filterName + ":");
        filter.getStyleClass().add("search-filter-name");
        Label qualityLabel = new Label(quality.name());
        qualityLabel.getStyleClass().addAll("match-quality", qualityStyle(quality));
        Label count = new Label(matchedCount + "/" + selectedCount + " vorhanden");
        count.getStyleClass().add("recipe-facts");
        HBox row = new HBox(10, filter, qualityLabel, count);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static void addMissingLabel(VBox content, String label, String missingText) {
        if (missingText.isEmpty()) {
            return;
        }
        Label missing = new Label(label + ": " + missingText);
        missing.setWrapText(true);
        missing.getStyleClass().add("search-missing");
        content.getChildren().add(missing);
    }

    private TasteFilterMode selectedTasteMode() {
        if (tasteAndMode.isSelected()) {
            return TasteFilterMode.AND;
        }
        if (tasteOrMode.isSelected()) {
            return TasteFilterMode.OR;
        }
        if (tasteRankingMode.isSelected()) {
            return TasteFilterMode.RANKING;
        }
        throw new IllegalStateException("A taste filter mode must be selected.");
    }

    private static String ingredientMissingText(IngredientSearchResult result) {
        return result.getMissingIngredients().stream()
                .map(Ingredient::getName)
                .reduce((first, second) -> first + ", " + second)
                .orElse("");
    }

    private static String tasteMissingText(TasteSearchResult result) {
        return result.getMissingTastes().stream()
                .map(Taste::getName)
                .reduce((first, second) -> first + ", " + second)
                .orElse("");
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
