package de.mealdeal.ui.controller;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.InventoryItem;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.DuplicateInventoryItemException;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.service.InventoryCategoryGroup;
import de.mealdeal.service.InventoryService;
import de.mealdeal.service.InventoryConsumptionService;
import de.mealdeal.ui.form.DecimalInputParser;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Displays and manually edits local inventory grouped by ingredient category. */
public final class InventoryController {

    private static final System.Logger LOGGER =
            System.getLogger(InventoryController.class.getName());

    private final InventoryService inventoryService;
    private final InventoryConsumptionService consumptionService;

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
        ingredientBox.setConverter(new IngredientStringConverter());
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
            List<Ingredient> ingredients = inventoryService.loadAvailableIngredients();
            Ingredient selected = ingredientBox.getValue();
            ingredientBox.setItems(FXCollections.observableArrayList(ingredients));
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
        VBox rows = new VBox(8);
        group.items().stream().map(this::itemRow).forEach(rows.getChildren()::add);
        VBox card = new VBox(14, title, rows);
        card.getStyleClass().addAll("card", "wide-card", "inventory-category-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private HBox itemRow(InventoryItem item) {
        Label ingredient = new Label(item.getIngredient().getName());
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

        HBox row = new HBox(14, ingredient, quantity, unit, edit, delete);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("inventory-row");
        edit.setOnAction(ignored -> showEditor(row, item));
        delete.setOnAction(ignored -> delete(item));
        return row;
    }

    private void showEditor(HBox row, InventoryItem item) {
        Label ingredient = new Label(item.getIngredient().getName());
        ingredient.setMaxWidth(Double.MAX_VALUE);
        ingredient.getStyleClass().add("inventory-ingredient");
        HBox.setHgrow(ingredient, Priority.ALWAYS);
        TextField quantity = new TextField(GermanRecipeDisplay.decimal(item.getQuantity()));
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
        VBox editor = new VBox(8, new HBox(14, ingredient, quantity, unit, actions), error);
        editor.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(editor, Priority.ALWAYS);
        row.getChildren().setAll(editor);

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

    private static final class IngredientStringConverter extends StringConverter<Ingredient> {
        @Override public String toString(Ingredient ingredient) {
            return ingredient == null ? "" : ingredient.getName() + " · "
                    + ingredient.getCategory().getName();
        }
        @Override public Ingredient fromString(String value) {
            throw new UnsupportedOperationException("Ingredient selection is not editable.");
        }
    }
}
