package de.mealdeal.ui.controller;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.IngredientCategory;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.service.IngredientCategoryService;
import de.mealdeal.service.IngredientManagementService;
import de.mealdeal.ui.IngredientCategoryGrouping;
import de.mealdeal.ui.control.SearchableComboBoxSupport;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Displays and edits central ingredients and their shared category catalog. */
public final class IngredientsController {

    private static final System.Logger LOGGER =
            System.getLogger(IngredientsController.class.getName());

    private final IngredientCategoryService categoryService;
    private final IngredientManagementService ingredientService;
    private final IngredientManagementViewState ingredientViewState =
            new IngredientManagementViewState();
    private final List<Button> ingredientCategoryTiles = new ArrayList<>();
    private final ListChangeListener<String> viewportListener = ignored ->
            layoutIngredientCategoryGrid();
    private SearchableComboBoxSupport<IngredientCategory> ingredientCategorySearch;
    private List<IngredientCategory> availableCategories = List.of();
    private Scene observedScene;

    @FXML private VBox managementContent;
    @FXML private TitledPane ingredientManagementSection;
    @FXML private TitledPane categoryManagementSection;
    @FXML private Button showAddIngredientButton;
    @FXML private VBox addIngredientForm;
    @FXML private TextField ingredientNameField;
    @FXML private ComboBox<IngredientCategory> ingredientCategoryBox;
    @FXML private Label addIngredientError;
    @FXML private TextField categoryNameField;
    @FXML private Label categoryFormError;
    @FXML private VBox categoryManagementContainer;
    @FXML private Label ingredientManagementError;
    @FXML private VBox ingredientManagementContainer;
    @FXML private Label ingredientManagementEmptyState;
    @FXML private GridPane ingredientCategoryGrid;
    @FXML private VBox selectedIngredientCategoryContent;
    @FXML private Label selectedIngredientCategoryTitle;
    @FXML private VBox selectedIngredientRows;
    @FXML private VBox loadErrorState;
    @FXML private Label loadErrorMessage;

    public IngredientsController(IngredientCategoryService categoryService,
                                 IngredientManagementService ingredientService) {
        this.categoryService = Objects.requireNonNull(
                categoryService, "Ingredient category service must not be null.");
        this.ingredientService = Objects.requireNonNull(
                ingredientService, "Ingredient management service must not be null.");
    }

    @FXML
    private void initialize() {
        ingredientCategorySearch = SearchableComboBoxSupport.forValidValues(
                ingredientCategoryBox, List.of(), IngredientCategory::getName);
        ingredientCategoryGrid.sceneProperty().addListener((ignored, previous, current) -> {
            if (previous != null) {
                previous.getRoot().getStyleClass().removeListener(viewportListener);
            }
            observedScene = current;
            if (current != null) {
                current.getRoot().getStyleClass().addListener(viewportListener);
            }
            layoutIngredientCategoryGrid();
        });
        refresh();
    }

    /** Reloads both central catalogs so renames and ordering changes appear immediately. */
    @FXML
    public void refresh() {
        try {
            List<IngredientCategory> categories = categoryService.loadCategories();
            ingredientCategorySearch.setOptions(categories);
            renderIngredients(ingredientService.loadIngredients(), categories);
            renderCategories(categories);
            managementContent.setManaged(true);
            managementContent.setVisible(true);
            loadErrorState.setManaged(false);
            loadErrorState.setVisible(false);
        } catch (PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not load ingredient management.",
                    exception);
            managementContent.setManaged(false);
            managementContent.setVisible(false);
            loadErrorMessage.setText(
                    "Zutaten und Kategorien konnten nicht geladen werden. Bitte versuche es erneut.");
            loadErrorState.setManaged(true);
            loadErrorState.setVisible(true);
        }
    }

    @FXML
    private void addCategory() {
        try {
            categoryService.create(categoryNameField.getText());
            categoryNameField.clear();
            clearCategoryError();
            refresh();
        } catch (IllegalArgumentException | PersistenceException exception) {
            showCategoryError(messageOf(exception));
        }
    }

    @FXML
    private void showAddIngredientForm() {
        ingredientNameField.clear();
        ingredientCategorySearch.clearSelection();
        clearError(addIngredientError);
        addIngredientForm.setManaged(true);
        addIngredientForm.setVisible(true);
        showAddIngredientButton.setDisable(true);
        ingredientNameField.requestFocus();
    }

    @FXML
    private void addIngredient() {
        try {
            IngredientCategory category = ingredientCategoryBox.getValue();
            Ingredient created = ingredientService.create(
                    ingredientNameField.getText(), category == null ? null : category.getId());
            hideAddIngredientForm();
            refresh();
            selectIngredientCategoryIfPresent(created.getCategory().getId());
        } catch (IllegalArgumentException | PersistenceException exception) {
            showError(addIngredientError, messageOf(exception));
        }
    }

    @FXML
    private void cancelAddIngredient() {
        hideAddIngredientForm();
    }

    private void hideAddIngredientForm() {
        ingredientNameField.clear();
        ingredientCategorySearch.clearSelection();
        clearError(addIngredientError);
        addIngredientForm.setManaged(false);
        addIngredientForm.setVisible(false);
        showAddIngredientButton.setDisable(false);
    }

    private void selectIngredientCategoryIfPresent(UUID categoryId) {
        if (ingredientViewState.groups().stream()
                .noneMatch(group -> group.category().getId().equals(categoryId))) {
            return;
        }
        ingredientViewState.selectCategory(categoryId);
        renderIngredientCategoryTiles();
        renderSelectedIngredientCategory();
    }

    private void renderCategories(List<IngredientCategory> categories) {
        categoryManagementContainer.getChildren().clear();
        for (int index = 0; index < categories.size(); index++) {
            categoryManagementContainer.getChildren().add(
                    categoryRow(categories.get(index), index, categories.size()));
        }
    }

    private HBox categoryRow(IngredientCategory category, int index, int size) {
        Label name = new Label(category.getName());
        name.setMaxWidth(Double.MAX_VALUE);
        name.getStyleClass().add("inventory-category-management-name");
        HBox.setHgrow(name, Priority.ALWAYS);

        HBox row = new HBox(8, name);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("inventory-category-management-row");
        if (categoryService.isFallback(category)) {
            Label fallback = new Label("Fallback");
            fallback.getStyleClass().add("inventory-category-fallback-badge");
            row.getChildren().add(fallback);
        }

        Button up = orderButton("↑", "Kategorie nach oben verschieben");
        Button down = orderButton("↓", "Kategorie nach unten verschieben");
        up.setDisable(index == 0);
        down.setDisable(index == size - 1);
        up.setOnAction(ignored -> changeOrder(category, true));
        down.setOnAction(ignored -> changeOrder(category, false));

        Button rename = new Button("Umbenennen");
        rename.getStyleClass().add("secondary-button");
        rename.setDisable(categoryService.isFallback(category));
        rename.setOnAction(ignored -> showCategoryRenameEditor(row, category));

        Button delete = new Button("Löschen");
        delete.getStyleClass().add("danger-button");
        delete.setDisable(categoryService.isFallback(category));
        delete.setOnAction(ignored -> confirmCategoryDeletion(category));
        row.getChildren().addAll(up, down, rename, delete);
        return row;
    }

    private static Button orderButton(String text, String accessibleText) {
        Button button = new Button(text);
        button.setAccessibleText(accessibleText);
        button.getStyleClass().addAll("secondary-button", "inventory-category-order-button");
        return button;
    }

    private void changeOrder(IngredientCategory category, boolean upward) {
        try {
            if (upward) {
                categoryService.moveUp(category.getId());
            } else {
                categoryService.moveDown(category.getId());
            }
            clearCategoryError();
            refresh();
        } catch (IllegalArgumentException | PersistenceException exception) {
            showCategoryError(messageOf(exception));
        }
    }

    private void showCategoryRenameEditor(HBox row, IngredientCategory category) {
        TextField name = new TextField(category.getName());
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);
        Button save = new Button("Speichern");
        save.getStyleClass().add("primary-button");
        Button cancel = new Button("Abbrechen");
        cancel.getStyleClass().add("secondary-button");
        row.getChildren().setAll(name, save, cancel);
        save.setOnAction(ignored -> {
            try {
                categoryService.rename(category.getId(), name.getText());
                clearCategoryError();
                refresh();
            } catch (IllegalArgumentException | PersistenceException exception) {
                showCategoryError(messageOf(exception));
            }
        });
        cancel.setOnAction(ignored -> refresh());
    }

    private void confirmCategoryDeletion(IngredientCategory category) {
        try {
            int ingredientCount = categoryService.countIngredients(category.getId());
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Kategorie löschen");
            confirmation.setHeaderText("„" + category.getName() + "“ wirklich löschen?");
            confirmation.setContentText(ingredientCount == 0
                    ? "Die leere Kategorie wird gelöscht."
                    : ingredientCount + (ingredientCount == 1 ? " Zutat wird" : " Zutaten werden")
                            + " dabei nach „Sonstiges“ verschoben. Keine Zutat wird gelöscht.");
            if (categoryManagementSection.getScene() != null) {
                confirmation.initOwner(categoryManagementSection.getScene().getWindow());
            }
            if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty()) {
                return;
            }
            categoryService.delete(category.getId());
            clearCategoryError();
            refresh();
        } catch (IllegalArgumentException | PersistenceException exception) {
            showCategoryError(messageOf(exception));
        }
    }

    private void renderIngredients(List<Ingredient> ingredients,
                                   List<IngredientCategory> categories) {
        availableCategories = List.copyOf(categories);
        ingredientViewState.updateIngredients(ingredients);
        ingredientManagementEmptyState.setManaged(ingredients.isEmpty());
        ingredientManagementEmptyState.setVisible(ingredients.isEmpty());
        ingredientCategoryGrid.setManaged(!ingredients.isEmpty());
        ingredientCategoryGrid.setVisible(!ingredients.isEmpty());
        renderIngredientCategoryTiles();
        renderSelectedIngredientCategory();
    }

    private void renderIngredientCategoryTiles() {
        ingredientCategoryTiles.clear();
        for (IngredientCategoryGrouping.Group group : ingredientViewState.groups()) {
            Button tile = new Button(group.category().getName());
            tile.setMinWidth(0);
            tile.setMaxWidth(Double.MAX_VALUE);
            tile.setWrapText(true);
            tile.getStyleClass().addAll("secondary-button", "ingredient-category-tile");
            if (ingredientViewState.isSelected(group.category().getId())) {
                tile.getStyleClass().add("ingredient-category-tile-active");
            }
            tile.setOnAction(ignored -> {
                ingredientViewState.selectCategory(group.category().getId());
                renderIngredientCategoryTiles();
                renderSelectedIngredientCategory();
            });
            ingredientCategoryTiles.add(tile);
        }
        layoutIngredientCategoryGrid();
    }

    private void renderSelectedIngredientCategory() {
        var selectedGroup = ingredientViewState.selectedGroup();
        if (selectedGroup.isEmpty()) {
            selectedIngredientRows.getChildren().clear();
            selectedIngredientCategoryContent.setManaged(false);
            selectedIngredientCategoryContent.setVisible(false);
            return;
        }
        IngredientCategoryGrouping.Group group = selectedGroup.orElseThrow();
        selectedIngredientCategoryTitle.setText(group.category().getName());
        selectedIngredientRows.getChildren().setAll(group.ingredients().stream()
                .map(ingredient -> ingredientRow(ingredient, availableCategories))
                .toList());
        selectedIngredientCategoryContent.setManaged(true);
        selectedIngredientCategoryContent.setVisible(true);
    }

    private void layoutIngredientCategoryGrid() {
        int columns = ingredientCategoryColumnsFor(observedScene == null
                ? List.of() : observedScene.getRoot().getStyleClass());
        ingredientCategoryGrid.getChildren().clear();
        ingredientCategoryGrid.getColumnConstraints().clear();
        for (int columnIndex = 0; columnIndex < columns; columnIndex++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / columns);
            column.setHgrow(Priority.ALWAYS);
            ingredientCategoryGrid.getColumnConstraints().add(column);
        }
        for (int index = 0; index < ingredientCategoryTiles.size(); index++) {
            Button tile = ingredientCategoryTiles.get(index);
            ingredientCategoryGrid.add(tile, index % columns, index / columns);
            GridPane.setHgrow(tile, Priority.ALWAYS);
            GridPane.setFillWidth(tile, true);
        }
    }

    static int ingredientCategoryColumnsFor(List<String> viewportStyleClasses) {
        if (viewportStyleClasses.contains("viewport-compact")) {
            return 2;
        }
        if (viewportStyleClasses.contains("viewport-extra-wide")) {
            return 6;
        }
        if (viewportStyleClasses.contains("viewport-wide")) {
            return 5;
        }
        return 4;
    }

    private HBox ingredientRow(Ingredient ingredient, List<IngredientCategory> categories) {
        Label name = new Label(ingredient.getName());
        name.setMaxWidth(Double.MAX_VALUE);
        name.getStyleClass().add("inventory-ingredient-management-name");
        HBox.setHgrow(name, Priority.ALWAYS);
        Button edit = new Button("Bearbeiten");
        edit.getStyleClass().add("secondary-button");
        HBox row = new HBox(10, name, edit);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("inventory-ingredient-management-row");
        edit.setOnAction(ignored -> showIngredientEditor(row, ingredient, categories));
        return row;
    }

    private void showIngredientEditor(HBox row, Ingredient ingredient,
                                      List<IngredientCategory> categories) {
        TextField name = new TextField(ingredient.getName());
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);
        ComboBox<IngredientCategory> category = new ComboBox<>();
        SearchableComboBoxSupport.forValidValues(
                category, categories, IngredientCategory::getName);
        category.getSelectionModel().select(categories.stream()
                .filter(candidate -> candidate.getId().equals(ingredient.getCategory().getId()))
                .findFirst().orElse(null));
        Button save = new Button("Speichern");
        save.getStyleClass().add("primary-button");
        Button cancel = new Button("Abbrechen");
        cancel.getStyleClass().add("secondary-button");
        row.getChildren().setAll(name, category, save, cancel);
        save.setOnAction(ignored -> {
            try {
                IngredientCategory selected = category.getValue();
                if (selected == null) {
                    throw new IllegalArgumentException("Bitte eine Kategorie auswählen.");
                }
                ingredientService.update(ingredient.getId(), name.getText(), selected.getId());
                clearIngredientError();
                refresh();
            } catch (IllegalArgumentException | PersistenceException exception) {
                showIngredientError(messageOf(exception));
            }
        });
        cancel.setOnAction(ignored -> refresh());
    }

    private void showCategoryError(String message) {
        showError(categoryFormError, message);
    }

    private void clearCategoryError() {
        clearError(categoryFormError);
    }

    private void showIngredientError(String message) {
        showError(ingredientManagementError, message);
    }

    private void clearIngredientError() {
        clearError(ingredientManagementError);
    }

    private static void showError(Label label, String message) {
        label.setText(message);
        label.setManaged(true);
        label.setVisible(true);
    }

    private static void clearError(Label label) {
        label.setText("");
        label.setManaged(false);
        label.setVisible(false);
    }

    private static String messageOf(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Die Eingabe ist ungültig." : exception.getMessage();
    }
}
