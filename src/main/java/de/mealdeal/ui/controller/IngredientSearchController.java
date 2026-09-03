package de.mealdeal.ui.controller;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.Taste;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.repository.IngredientRepository;
import de.mealdeal.persistence.repository.RecipeRepository;
import de.mealdeal.persistence.repository.TasteRepository;
import de.mealdeal.service.CombinedRecipeSearchService;
import de.mealdeal.service.RecipeSearchService;
import de.mealdeal.service.TasteFilterMode;
import de.mealdeal.ui.navigation.NavigationAware;
import de.mealdeal.ui.navigation.ViewNavigator;
import de.mealdeal.ui.search.IngredientSearchModel;
import de.mealdeal.ui.search.TasteSearchModel;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.util.Objects;
import java.util.function.Consumer;

/** Coordinates recipe-search state, services, navigation and UI presentation components. */
public final class IngredientSearchController implements NavigationAware {

    private static final System.Logger LOGGER =
            System.getLogger(IngredientSearchController.class.getName());

    private final IngredientSearchModel searchModel;
    private final TasteSearchModel tasteSearchModel;
    private final RecipeRepository recipeRepository;
    private final CombinedRecipeSearchService combinedSearchService;
    private Consumer<Recipe> detailNavigation;
    private IngredientSelectionView ingredientSelectionView;
    private TasteSelectionView tasteSelectionView;
    private RecipeSearchResultsView resultsView;

    @FXML
    private TextField ingredientFilterField;
    @FXML
    private VBox availableIngredientsContainer;
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
        ingredientSelectionView = new IngredientSelectionView(
                ingredientFilterField, availableIngredientsContainer,
                selectedIngredientsContainer, selectionCountLabel,
                IngredientSearchModel.MAX_SELECTED_INGREDIENTS,
                this::selectIngredient, this::removeIngredient);
        tasteSelectionView = new TasteSelectionView(
                tasteFilterField, availableTastesContainer, selectedTastesContainer,
                tasteSelectionCountLabel, this::selectTaste, this::removeTaste);
        resultsView = new RecipeSearchResultsView(
                resultsContainer, initialState, emptyState, errorState, this::openRecipe);
        boolean ingredientsLoaded = loadIngredients();
        boolean tastesLoaded = loadTastes();
        ingredientSelectionView.showSelection(searchModel.getSelectedIngredients());
        tasteSelectionView.showSelection(tasteSearchModel.getSelectedTastes());
        if (ingredientsLoaded && tastesLoaded) {
            resultsView.showInitial();
        }
    }

    @FXML
    private void search() {
        clearMessage();
        try {
            resultsView.showResults(combinedSearchService.search(
                    recipeRepository.findAll(),
                    searchModel.getSelectedIngredients(),
                    tasteSearchModel.getSelectedTastes(),
                    selectedTasteMode()));
        } catch (IllegalArgumentException exception) {
            showMessage("Wähle mindestens eine Zutat oder Geschmacksrichtung aus.");
        } catch (PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not search recipes.", exception);
            resultsView.showError();
        }
    }

    @FXML
    private void resetFilters() {
        searchModel.clear();
        tasteSearchModel.clear();
        ingredientSelectionView.clearFilter();
        tasteSelectionView.clearFilter();
        tasteRankingMode.setSelected(true);
        clearMessage();
        ingredientSelectionView.showSelection(searchModel.getSelectedIngredients());
        tasteSelectionView.showSelection(tasteSearchModel.getSelectedTastes());
        resultsView.showInitial();
    }

    void openRecipe(Recipe recipe) {
        detailNavigation.accept(Objects.requireNonNull(recipe, "Recipe must not be null."));
    }

    private boolean loadIngredients() {
        try {
            ingredientSelectionView.setAvailableIngredients(
                    searchModel.loadAvailableIngredients());
            return true;
        } catch (PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not load ingredients.", exception);
            ingredientSelectionView.setFilterDisabled(true);
            showMessage("Die Zutaten konnten nicht geladen werden.");
            resultsView.showError();
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
        ingredientSelectionView.showSelection(searchModel.getSelectedIngredients());
        resultsView.showInitial();
    }

    private boolean loadTastes() {
        try {
            tasteSelectionView.setAvailableTastes(tasteSearchModel.loadAvailableTastes());
            return true;
        } catch (PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not load tastes.", exception);
            tasteSelectionView.setFilterDisabled(true);
            showMessage("Die Geschmacksrichtungen konnten nicht geladen werden.");
            resultsView.showError();
            return false;
        }
    }

    private void selectTaste(Taste taste) {
        tasteSearchModel.select(taste);
        clearMessage();
        tasteSelectionView.showSelection(tasteSearchModel.getSelectedTastes());
        resultsView.showInitial();
    }

    private void removeTaste(Taste taste) {
        tasteSearchModel.remove(taste);
        clearMessage();
        tasteSelectionView.showSelection(tasteSearchModel.getSelectedTastes());
        resultsView.showInitial();
    }

    private void removeIngredient(Ingredient ingredient) {
        searchModel.remove(ingredient);
        clearMessage();
        ingredientSelectionView.showSelection(searchModel.getSelectedIngredients());
        resultsView.showInitial();
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

}
