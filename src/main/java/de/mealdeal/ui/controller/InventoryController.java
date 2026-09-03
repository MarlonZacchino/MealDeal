package de.mealdeal.ui.controller;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.InventoryItem;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.DuplicateInventoryItemException;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.service.InventoryCategoryGroup;
import de.mealdeal.service.InventoryConsumptionService;
import de.mealdeal.service.InventoryService;
import de.mealdeal.ui.IngredientCategoryGrouping;
import de.mealdeal.ui.control.SearchableComboBoxSupport;
import de.mealdeal.ui.form.DecimalInputParser;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Displays and manually edits local inventory grouped by ingredient category. */
public final class InventoryController {

    private static final System.Logger LOGGER =
            System.getLogger(InventoryController.class.getName());

    private final InventoryService inventoryService;
    private final InventoryConsumptionService consumptionService;
    private final List<InventoryGrid> inventoryGrids = new ArrayList<>();
    private final ListChangeListener<String> viewportListener = ignored ->
            layoutInventoryGrids();
    private SearchableComboBoxSupport<Ingredient> ingredientSearch;
    private Scene observedScene;

    @FXML private ComboBox<Ingredient> ingredientBox;
    @FXML private TextField quantityField;
    @FXML private ComboBox<Unit> unitBox;
    @FXML private Label formError;
    @FXML private VBox categoryContainer;
    @FXML private VBox emptyState;
    @FXML private VBox loadErrorState;
    @FXML private Label loadErrorMessage;

    public InventoryController(InventoryService inventoryService) {
        this(inventoryService, null);
    }

    /** Creates the inventory view with safe reconciliation before every reload. */
    public InventoryController(InventoryService inventoryService,
                               InventoryConsumptionService consumptionService) {
        this.inventoryService = Objects.requireNonNull(
                inventoryService, "Inventory service must not be null.");
        this.consumptionService = consumptionService;
    }

    @FXML
    private void initialize() {
        categoryContainer.sceneProperty().addListener((ignored, previous, current) -> {
            if (previous != null) {
                previous.getRoot().getStyleClass().removeListener(viewportListener);
            }
            observedScene = current;
            if (current != null) {
                current.getRoot().getStyleClass().addListener(viewportListener);
            }
            layoutInventoryGrids();
        });
        ingredientSearch = SearchableComboBoxSupport.forValidValuesInSourceOrder(
                ingredientBox, List.of(), ingredient -> ingredient.getName() + " · "
                        + ingredient.getCategory().getName());
        ingredientBox.setCellFactory(ignored -> new InventoryIngredientCell(ingredientBox));
        unitBox.setConverter(new GermanUnitStringConverter());
        unitBox.setItems(FXCollections.observableArrayList(Unit.values()));
        unitBox.setValue(Unit.GRAM);
        refresh();
    }

    /** Reloads central ingredients and the grouped persisted inventory. */
    @FXML
    public void refresh() {
        try {
            if (consumptionService != null) {
                consumptionService.consumePastEntries();
            }
            List<Ingredient> ingredients = inventoryPickerOrder(
                    inventoryService.loadAvailableIngredients());
            Ingredient selected = ingredientBox.getValue();
            ingredientSearch.setOptions(ingredients);
            ingredientBox.setValue(selected != null && ingredients.contains(selected)
                    ? selected : ingredients.stream().findFirst().orElse(null));
            render(inventoryService.loadGroupedInventory());
            showLoadContent();
        } catch (PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not load inventory.", exception);
            showLoadError();
        }
    }

    @FXML
    private void addItem() {
        try {
            Ingredient ingredient = Objects.requireNonNull(
                    ingredientBox.getValue(), "Bitte eine Zutat auswählen.");
            Unit unit = Objects.requireNonNull(unitBox.getValue(), "Bitte eine Einheit auswählen.");
            BigDecimal quantity = DecimalInputParser.parseNonNegative(quantityField.getText());
            inventoryService.add(ingredient.getId(), quantity, unit);
            quantityField.clear();
            clearFormError();
            refresh();
        } catch (NullPointerException | IllegalArgumentException exception) {
            showFormError(messageOf(exception));
        } catch (PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not add inventory item.", exception);
            showFormError(messageOf(exception));
        }
    }

    private void render(List<InventoryCategoryGroup> groups) {
        categoryContainer.getChildren().clear();
        inventoryGrids.clear();
        if (groups.isEmpty()) {
            categoryContainer.setManaged(false);
            categoryContainer.setVisible(false);
            emptyState.setManaged(true);
            emptyState.setVisible(true);
            return;
        }
        groups.stream().map(this::categoryCard)
                .forEach(categoryContainer.getChildren()::add);
        categoryContainer.setManaged(true);
        categoryContainer.setVisible(true);
        emptyState.setManaged(false);
        emptyState.setVisible(false);
    }

    private VBox categoryCard(InventoryCategoryGroup group) {
        Label title = new Label(group.category().getName());
        title.getStyleClass().add("section-title");
        GridPane grid = new GridPane();
        grid.getStyleClass().add("inventory-grid");
        List<VBox> items = group.items().stream().map(this::itemCard).toList();
        InventoryGrid inventoryGrid = new InventoryGrid(grid, items);
        inventoryGrids.add(inventoryGrid);
        layoutInventoryGrid(inventoryGrid, currentInventoryColumnCount());
        VBox card = new VBox(14, title, grid);
        card.getStyleClass().addAll("content-card", "inventory-category-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private VBox itemCard(InventoryItem item) {
        Label ingredient = new Label(item.getIngredient().getName());
        ingredient.setMinWidth(0);
        ingredient.setMaxWidth(Double.MAX_VALUE);
        ingredient.setWrapText(true);
        ingredient.getStyleClass().add("inventory-ingredient");
        HBox.setHgrow(ingredient, Priority.ALWAYS);

        Label quantity = new Label(GermanRecipeDisplay.decimal(item.getQuantity()));
        quantity.getStyleClass().add("inventory-quantity");
        Label unit = new Label(GermanRecipeDisplay.unit(item.getQuantity(), item.getUnit()));
        unit.getStyleClass().add("inventory-unit");
        Button edit = new Button("Bearbeiten");
        edit.getStyleClass().add("secondary-button");
        Button delete = new Button("Löschen");
        delete.getStyleClass().add("danger-button");

        HBox summary = new HBox(10, ingredient, quantity, unit);
        summary.setAlignment(Pos.CENTER_LEFT);
        FlowPane actions = new FlowPane(8, 8, edit, delete);
        actions.setAlignment(Pos.CENTER_RIGHT);
        VBox card = new VBox(10, summary, actions);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("inventory-row");
        edit.setOnAction(ignored -> showEditor(card, item));
        delete.setOnAction(ignored -> delete(item));
        return card;
    }

    private void showEditor(VBox card, InventoryItem item) {
        Label ingredient = new Label(item.getIngredient().getName());
        ingredient.setMinWidth(0);
        ingredient.setMaxWidth(Double.MAX_VALUE);
        ingredient.getStyleClass().add("inventory-ingredient");
        HBox.setHgrow(ingredient, Priority.ALWAYS);
        TextField quantity = new TextField(GermanRecipeDisplay.editableDecimal(item.getQuantity()));
        quantity.setPromptText("Menge");
        quantity.getStyleClass().add("inventory-edit-quantity");
        ComboBox<Unit> unit = new ComboBox<>(FXCollections.observableArrayList(Unit.values()));
        unit.setConverter(new GermanUnitStringConverter());
        unit.setValue(item.getUnit());
        unit.getStyleClass().add("inventory-edit-unit");
        Label error = new Label();
        error.setManaged(false);
        error.setVisible(false);
        error.setWrapText(true);
        error.getStyleClass().add("form-error");
        Button save = new Button("Speichern");
        save.getStyleClass().add("primary-button");
        Button cancel = new Button("Abbrechen");
        cancel.getStyleClass().add("secondary-button");
        FlowPane actions = new FlowPane(8, 8, save, cancel);
        actions.setAlignment(Pos.CENTER_RIGHT);
        FlowPane fields = new FlowPane(10, 10, quantity, unit);
        card.getChildren().setAll(ingredient, fields, error, actions);

        save.setOnAction(ignored -> {
            try {
                BigDecimal parsed = DecimalInputParser.parseNonNegative(quantity.getText());
                inventoryService.update(item.getId(), parsed,
                        Objects.requireNonNull(unit.getValue(), "Bitte eine Einheit auswählen."));
                refresh();
            } catch (NullPointerException | IllegalArgumentException exception) {
                showInlineError(error, messageOf(exception));
            } catch (PersistenceException exception) {
                LOGGER.log(System.Logger.Level.ERROR, "Could not update inventory item.", exception);
                showInlineError(error, messageOf(exception));
            }
        });
        cancel.setOnAction(ignored -> refresh());
    }

    private void layoutInventoryGrids() {
        int columns = currentInventoryColumnCount();
        inventoryGrids.forEach(grid -> layoutInventoryGrid(grid, columns));
    }

    private int currentInventoryColumnCount() {
        return inventoryColumnsFor(observedScene == null
                ? List.of() : observedScene.getRoot().getStyleClass());
    }

    static int inventoryColumnsFor(List<String> viewportStyleClasses) {
        if (viewportStyleClasses.contains("viewport-compact")) {
            return 1;
        }
        if (viewportStyleClasses.contains("viewport-extra-wide")) {
            return 4;
        }
        if (viewportStyleClasses.contains("viewport-wide")) {
            return 3;
        }
        return 2;
    }

    static List<Ingredient> inventoryPickerOrder(List<Ingredient> ingredients) {
        return IngredientCategoryGrouping.group(ingredients, "", List.of()).stream()
                .flatMap(group -> group.ingredients().stream())
                .toList();
    }

    private static void layoutInventoryGrid(InventoryGrid inventoryGrid, int columns) {
        GridPane grid = inventoryGrid.grid();
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        for (int columnIndex = 0; columnIndex < columns; columnIndex++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / columns);
            column.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(column);
        }
        for (int index = 0; index < inventoryGrid.items().size(); index++) {
            VBox item = inventoryGrid.items().get(index);
            grid.add(item, index % columns, index / columns);
            GridPane.setHgrow(item, Priority.ALWAYS);
            GridPane.setFillWidth(item, true);
        }
    }

    private void delete(InventoryItem item) {
        try {
            inventoryService.delete(item.getId());
            refresh();
        } catch (PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not delete inventory item.", exception);
            showFormError("Der Inventareintrag konnte nicht gelöscht werden.");
        }
    }

    private void showLoadContent() {
        loadErrorState.setManaged(false);
        loadErrorState.setVisible(false);
    }

    private void showLoadError() {
        categoryContainer.setManaged(false);
        categoryContainer.setVisible(false);
        emptyState.setManaged(false);
        emptyState.setVisible(false);
        loadErrorMessage.setText("Das Inventar konnte nicht geladen werden. Bitte versuche es erneut.");
        loadErrorState.setManaged(true);
        loadErrorState.setVisible(true);
    }

    private void showFormError(String message) {
        formError.setText(message);
        formError.setManaged(true);
        formError.setVisible(true);
    }

    private void clearFormError() {
        formError.setManaged(false);
        formError.setVisible(false);
    }

    private static void showInlineError(Label label, String message) {
        label.setText(message);
        label.setManaged(true);
        label.setVisible(true);
    }

    private static String messageOf(RuntimeException exception) {
        if (exception instanceof DuplicateInventoryItemException) {
            return "Für diese Zutat und Einheit existiert bereits ein Inventareintrag.";
        }
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Die Eingabe ist ungültig." : exception.getMessage();
    }

    private record InventoryGrid(GridPane grid, List<VBox> items) {
        private InventoryGrid {
            items = List.copyOf(items);
        }
    }

    private static final class InventoryIngredientCell extends ListCell<Ingredient> {
        private final ComboBox<Ingredient> owner;
        private final Label category = new Label();
        private final Label ingredient = new Label();
        private final VBox content = new VBox(2, category, ingredient);

        private InventoryIngredientCell(ComboBox<Ingredient> owner) {
            this.owner = owner;
            category.getStyleClass().add("inventory-picker-category");
        }

        @Override
        protected void updateItem(Ingredient item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            boolean categoryStart = startsCategory(item);
            category.setText(item.getCategory().getName());
            category.setManaged(categoryStart);
            category.setVisible(categoryStart);
            ingredient.setText(item.getName());
            setText(null);
            setGraphic(content);
        }

        private boolean startsCategory(Ingredient item) {
            int index = getIndex();
            if (index <= 0 || index > owner.getItems().size() - 1) {
                return true;
            }
            Ingredient previous = owner.getItems().get(index - 1);
            return !previous.getCategory().getId().equals(item.getCategory().getId());
        }
    }
}
