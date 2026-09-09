package de.mealdeal.ui.controller;

import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeFeedbackValue;
import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.Taste;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.repository.IngredientRepository;
import de.mealdeal.persistence.repository.TasteRepository;
import de.mealdeal.service.recommendation.RecommendationSession;
import de.mealdeal.service.recommendation.RecommendationWorkflowService;
import de.mealdeal.ui.navigation.NavigationAware;
import de.mealdeal.ui.navigation.ViewNavigator;
import de.mealdeal.ui.navigation.RecipeDetailContext;
import de.mealdeal.ui.search.IngredientSearchModel;
import javafx.collections.ListChangeListener;
import javafx.geometry.VPos;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Coordinates recommendation requests and explicit local feedback actions. */
public final class RecommendationController implements NavigationAware {

    private static final System.Logger LOGGER =
            System.getLogger(RecommendationController.class.getName());

    private final RecommendationWorkflowService workflowService;
    private final IngredientRepository ingredientRepository;
    private final TasteRepository tasteRepository;
    private final List<Ingredient> selectedIngredients = new ArrayList<>();
    private final List<Taste> selectedTastes = new ArrayList<>();
    private final ListChangeListener<String> viewportListener = ignored ->
            applyResponsiveOptionalLayout();
    private Consumer<RecipeDetailContext> detailNavigation = ignored -> {
        throw new IllegalStateException("Navigator has not been set.");
    };
    private IngredientSelectionView ingredientSelectionView;
    private TasteSelectionView tasteSelectionView;
    private RecommendationSession session;
    private Scene observedScene;

    @FXML private Spinner<Integer> servingsSpinner;
    @FXML private TextField maximumTimeField;
    @FXML private TextField ingredientFilterField;
    @FXML private VBox availableIngredientsContainer;
    @FXML private FlowPane selectedIngredientsContainer;
    @FXML private Label ingredientSelectionCountLabel;
    @FXML private TextField tasteFilterField;
    @FXML private FlowPane availableTastesContainer;
    @FXML private FlowPane selectedTastesContainer;
    @FXML private Label tasteSelectionCountLabel;
    @FXML private Label recommendationMessage;
    @FXML private Label inventoryNotice;
    @FXML private VBox resultsContainer;
    @FXML private VBox initialState;
    @FXML private VBox emptyState;
    @FXML private Label emptyTitle;
    @FXML private Label emptyMessage;
    @FXML private VBox errorState;
    @FXML private VBox optionalSelectionContent;
    @FXML private ToggleButton optionalSelectionButton;
    @FXML private GridPane optionalSelectionGrid;
    @FXML private VBox ingredientWishColumn;
    @FXML private VBox tasteWishColumn;

    public RecommendationController(
            RecommendationWorkflowService workflowService,
            IngredientRepository ingredientRepository,
            TasteRepository tasteRepository) {
        this.workflowService = Objects.requireNonNull(
                workflowService, "Recommendation workflow must not be null.");
        this.ingredientRepository = Objects.requireNonNull(
                ingredientRepository, "Ingredient repository must not be null.");
        this.tasteRepository = Objects.requireNonNull(
                tasteRepository, "Taste repository must not be null.");
    }

    @Override
    public void setNavigator(ViewNavigator navigator) {
        detailNavigation = Objects.requireNonNull(
                navigator, "Navigator must not be null.")::navigateToRecipeDetail;
    }

    @FXML
    private void toggleOptionalSelection() {
        boolean expanded = optionalSelectionButton.isSelected();
        optionalSelectionContent.setVisible(expanded);
        optionalSelectionContent.setManaged(expanded);
        updateOptionalSelectionButton();
    }

    private void updateOptionalSelectionButton() {
        int selectedCount = selectedIngredients.size() + selectedTastes.size();
        String selection = selectedCount == 0 ? "Optional auswählen"
                : selectedCount + " ausgewählt";
        boolean expanded = optionalSelectionButton.isSelected();
        optionalSelectionButton.setText(selection + (expanded ? " ▴" : " ▾"));
        optionalSelectionButton.setAccessibleText("Geschmack und Zutaten, " + selection
                + (expanded ? ", Auswahl schließen" : ", Auswahl öffnen"));
    }

    @Override
    public void onReturnFromDetail() {
        if (session != null) {
            try {
                workflowService.refreshFeedback(session);
                renderSession();
            } catch (RuntimeException exception) {
                handleActionError("Das aktuelle Feedback konnte nicht geladen werden.", exception);
            }
        }
    }

    @FXML
    private void initialize() {
        servingsSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, 2));
        servingsSpinner.setEditable(true);
        optionalSelectionGrid.sceneProperty().addListener((ignored, previous, current) -> {
            if (previous != null) {
                previous.getRoot().getStyleClass().removeListener(viewportListener);
            }
            observedScene = current;
            if (current != null) {
                current.getRoot().getStyleClass().addListener(viewportListener);
            }
            applyResponsiveOptionalLayout();
        });
        ingredientSelectionView = new IngredientSelectionView(
                ingredientFilterField, availableIngredientsContainer,
                selectedIngredientsContainer, ingredientSelectionCountLabel,
                IngredientSearchModel.MAX_SELECTED_INGREDIENTS,
                this::selectIngredient, this::removeIngredient);
        tasteSelectionView = new TasteSelectionView(
                tasteFilterField, availableTastesContainer, selectedTastesContainer,
                tasteSelectionCountLabel, this::selectTaste, this::removeTaste);
        ingredientSelectionView.showSelection(selectedIngredients);
        tasteSelectionView.showSelection(selectedTastes);
        showOnly(initialState);
        setInventoryNotice(false);
        try {
            List<Ingredient> ingredients = ingredientRepository.findAll().stream()
                    .sorted(Comparator.comparing(Ingredient::getName,
                                    String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(Ingredient::getId))
                    .toList();
            ingredientSelectionView.setAvailableIngredients(ingredients);
            List<Taste> tastes = tasteRepository.findAll().stream()
                    .sorted(Comparator.comparing(Taste::getName, String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(Taste::getId))
                    .toList();
            tasteSelectionView.setAvailableTastes(tastes);
        } catch (PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not load optional wishes.", exception);
            ingredientSelectionView.setFilterDisabled(true);
            tasteSelectionView.setFilterDisabled(true);
            showMessage("Geschmack und Zutaten konnten nicht geladen werden.");
        }
    }

    @FXML
    private void startRecommendation() {
        clearMessage();
        try {
            int servings = parseServings(servingsSpinner.getEditor().getText());
            servingsSpinner.getValueFactory().setValue(servings);
            Optional<Duration> maximumTime = parseMaximumTime(maximumTimeField.getText());
            session = workflowService.start(
                    servingsSpinner.getValue(), maximumTime, selectedTastes,
                    selectedIngredients);
            renderSession();
        } catch (IllegalArgumentException exception) {
            showMessage(exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not calculate recommendations.", exception);
            setInventoryNotice(false);
            showOnly(errorState);
        }
    }

    private void renderSession() {
        List<de.mealdeal.service.recommendation.RecipeRecommendation> visible =
                session.visibleRecommendations();
        setInventoryNotice(session.inventoryEmpty());
        resultsContainer.getChildren().clear();
        if (visible.isEmpty()) {
            boolean hadRecommendations = !session.recommendations().isEmpty();
            boolean cooked = session.hasCookedRecommendation();
            emptyTitle.setText(cooked ? "Keine weiteren Vorschläge für heute"
                    : hadRecommendations ? "Alle Vorschläge für diesmal ausgeblendet"
                    : "Keine geeigneten Rezepte vorhanden");
            emptyMessage.setText(cooked
                    ? "Als gekocht markierte Gerichte werden morgen wieder berücksichtigt."
                    : hadRecommendations
                            ? "Starte neue Vorschläge. Ausgeblendete Gerichte können wieder erscheinen."
                            : "Lege ein Rezept mit Zutaten an oder ergänze die Zutaten deiner Rezepte.");
            showOnly(emptyState);
            return;
        }
        RecommendationResultCardFactory cards = new RecommendationResultCardFactory(
                this::openRecipe, this::dismissRecipe, this::setFeedback,
                this::clearFeedback, this::markCooked);
        visible.stream().map(result -> cards.create(session, result))
                .forEach(resultsContainer.getChildren()::add);
        showOnly(resultsContainer);
        workflowService.recordShown(session);
    }

    private void openRecipe(Recipe recipe) {
        try {
            workflowService.select(session, recipe.getId());
            detailNavigation.accept(RecipeDetailContext.recommended(session, recipe.getId()));
        } catch (RuntimeException exception) {
            handleActionError("Das Gericht konnte nicht geöffnet werden.", exception);
        }
    }

    private void dismissRecipe(Recipe recipe) {
        clearMessage();
        try {
            workflowService.dismiss(session, recipe.getId());
            renderSession();
        } catch (RuntimeException exception) {
            handleActionError("Das Gericht konnte nicht ausgeblendet werden.", exception);
        }
    }

    private void setFeedback(Recipe recipe, RecipeFeedbackValue value) {
        clearMessage();
        try {
            if (value == RecipeFeedbackValue.LIKE) {
                workflowService.like(session, recipe.getId());
            } else {
                workflowService.dislike(session, recipe.getId());
            }
            renderSession();
        } catch (RuntimeException exception) {
            handleActionError("Das Feedback konnte nicht gespeichert werden.", exception);
        }
    }

    private void clearFeedback(Recipe recipe) {
        clearMessage();
        try {
            workflowService.clearFeedback(session, recipe.getId());
            renderSession();
        } catch (RuntimeException exception) {
            handleActionError("Das Feedback konnte nicht entfernt werden.", exception);
        }
    }

    private void markCooked(Recipe recipe) {
        clearMessage();
        try {
            workflowService.recordCooked(session, recipe.getId(), session.requestedServings());
            renderSession();
            showMessage("„" + recipe.getName() + "“ wurde als gekocht gespeichert.");
            recommendationMessage.getStyleClass().add("recommendation-success-message");
        } catch (RuntimeException exception) {
            handleActionError("Das Gericht konnte nicht als gekocht gespeichert werden.", exception);
        }
    }

    private void selectTaste(Taste taste) {
        if (!selectedTastes.contains(taste)) {
            selectedTastes.add(taste);
            selectedTastes.sort(Comparator.comparing(Taste::getName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Taste::getId));
        }
        tasteSelectionView.showSelection(selectedTastes);
        updateOptionalSelectionButton();
    }

    private void removeTaste(Taste taste) {
        selectedTastes.remove(taste);
        tasteSelectionView.showSelection(selectedTastes);
        updateOptionalSelectionButton();
    }

    private void selectIngredient(Ingredient ingredient) {
        if (selectedIngredients.contains(ingredient)) {
            return;
        }
        if (selectedIngredients.size() >= IngredientSearchModel.MAX_SELECTED_INGREDIENTS) {
            showMessage("Du kannst höchstens zehn Wunschzutaten auswählen.");
            return;
        }
        selectedIngredients.add(ingredient);
        selectedIngredients.sort(Comparator
                .comparing(Ingredient::getName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Ingredient::getId));
        ingredientSelectionView.showSelection(selectedIngredients);
        updateOptionalSelectionButton();
    }

    private void removeIngredient(Ingredient ingredient) {
        selectedIngredients.remove(ingredient);
        ingredientSelectionView.showSelection(selectedIngredients);
        updateOptionalSelectionButton();
    }

    private void applyResponsiveOptionalLayout() {
        if (optionalSelectionGrid == null) {
            return;
        }
        int columns = IngredientSearchController.searchSelectionColumnsFor(observedScene == null
                ? List.of() : observedScene.getRoot().getStyleClass());
        optionalSelectionGrid.getColumnConstraints().clear();
        for (int index = 0; index < columns; index++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / columns);
            optionalSelectionGrid.getColumnConstraints().add(column);
        }
        placeOptionalColumn(ingredientWishColumn, 0, 0);
        placeOptionalColumn(tasteWishColumn, columns == 1 ? 0 : 1, columns == 1 ? 1 : 0);
    }

    private static void placeOptionalColumn(VBox column, int columnIndex, int rowIndex) {
        GridPane.setColumnIndex(column, columnIndex);
        GridPane.setRowIndex(column, rowIndex);
        GridPane.setHgrow(column, Priority.ALWAYS);
        GridPane.setVgrow(column, Priority.NEVER);
        GridPane.setValignment(column, VPos.TOP);
    }

    static int parseServings(String value) {
        try {
            int servings = Integer.parseInt(value == null ? "" : value.strip());
            if (servings >= 1 && servings <= 999) {
                return servings;
            }
        } catch (NumberFormatException exception) {
            // Keep input errors in the user's language, including an empty editor.
        }
        throw new IllegalArgumentException("Bitte gib eine ganze Personenzahl von 1 bis 999 ein.");
    }

    static Optional<Duration> parseMaximumTime(String value) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        try {
            int minutes = Integer.parseInt(normalized);
            if (minutes <= 0) {
                throw new IllegalArgumentException("Die maximale Zeit muss positiv sein.");
            }
            return Optional.of(Duration.ofMinutes(minutes));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Die maximale Zeit muss eine positive ganze Minutenzahl sein.");
        }
    }

    private void showOnly(VBox visible) {
        for (VBox state : List.of(resultsContainer, initialState, emptyState, errorState)) {
            boolean selected = state == visible;
            state.setManaged(selected);
            state.setVisible(selected);
        }
    }

    private void setInventoryNotice(boolean visible) {
        inventoryNotice.setManaged(visible);
        inventoryNotice.setVisible(visible);
    }

    private void handleActionError(String message, RuntimeException exception) {
        LOGGER.log(System.Logger.Level.ERROR, message, exception);
        clearMessage();
        showMessage(message);
    }

    private void showMessage(String message) {
        recommendationMessage.setText(message);
        recommendationMessage.setManaged(true);
        recommendationMessage.setVisible(true);
    }

    private void clearMessage() {
        recommendationMessage.getStyleClass().remove("recommendation-success-message");
        recommendationMessage.setText("");
        recommendationMessage.setManaged(false);
        recommendationMessage.setVisible(false);
    }
}
