package de.mealdeal.ui.controller;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.IngredientCategory;
import de.mealdeal.domain.InventoryItem;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.DuplicateInventoryItemException;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.service.InventoryCategoryGroup;
import de.mealdeal.service.InventoryService;
import de.mealdeal.service.InventoryConsumptionService;
import de.mealdeal.service.IngredientCategoryService;
import de.mealdeal.service.IngredientManagementService;
import de.mealdeal.ui.form.DecimalInputParser;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
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
    private final IngredientCategoryService categoryService;
    private final IngredientManagementService ingredientManagementService;
    private final InventoryConsumptionService consumptionService;

    @FXML private ComboBox<Ingredient> ingredientBox;
    @FXML private TextField quantityField;
    @FXML private ComboBox<Unit> unitBox;
    @FXML private Label formError;
    @FXML private VBox categoryContainer;
    @FXML private VBox emptyState;
    @FXML private VBox loadErrorState;
    @FXML private Label loadErrorMessage;
    @FXML private TitledPane categoryManagementSection;
    @FXML private TextField categoryNameField;
    @FXML private Label categoryFormError;
    @FXML private VBox categoryManagementContainer;
    @FXML private TitledPane ingredientManagementSection;
    @FXML private Label ingredientManagementError;
    @FXML private VBox ingredientManagementContainer;

    public InventoryController(InventoryService inventoryService) {
        this(inventoryService, null, null, null);
    }

    /** Creates the inventory view with safe reconciliation before every reload. */
    public InventoryController(InventoryService inventoryService,
                               InventoryConsumptionService consumptionService) {
        this(inventoryService, null, null, consumptionService);
    }

    /** Creates the complete inventory view including central category management. */
    public InventoryController(InventoryService inventoryService,
                               IngredientCategoryService categoryService,
                               InventoryConsumptionService consumptionService) {
        this(inventoryService, categoryService, null, consumptionService);
    }

    /** Creates the complete inventory view including ingredient and category management. */
    public InventoryController(InventoryService inventoryService,
                               IngredientCategoryService categoryService,
                               IngredientManagementService ingredientManagementService,
                               InventoryConsumptionService consumptionService) {
        this.inventoryService = Objects.requireNonNull(
                inventoryService, "Inventory service must not be null.");
        this.categoryService = categoryService;
        this.ingredientManagementService = ingredientManagementService;
        this.consumptionService = consumptionService;
    }

    @FXML
    private void initialize() {
        ingredientBox.setConverter(new IngredientStringConverter());
        unitBox.setConverter(new GermanUnitStringConverter());
        unitBox.setItems(FXCollections.observableArrayList(Unit.values()));
        unitBox.setValue(Unit.GRAM);
        if (categoryService == null) {
            categoryManagementSection.setManaged(false);
            categoryManagementSection.setVisible(false);
        }
        if (ingredientManagementService == null) {
            ingredientManagementSection.setManaged(false);
            ingredientManagementSection.setVisible(false);
        }
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
            if (categoryService != null) {
                List<IngredientCategory> categories = categoryService.loadCategories();
                renderCategoryManagement(categories);
                if (ingredientManagementService != null) {
                    renderIngredientManagement(
                            ingredientManagementService.loadIngredients(), categories);
                }
            }
            render(inventoryService.loadGroupedInventory());
            showLoadContent();
        } catch (PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not load inventory.", exception);
            showLoadError();
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

    private void renderCategoryManagement(List<IngredientCategory> categories) {
        categoryManagementContainer.getChildren().clear();
        for (int index = 0; index < categories.size(); index++) {
            categoryManagementContainer.getChildren().add(
                    categoryManagementRow(categories.get(index), index, categories.size()));
        }
    }

    private HBox categoryManagementRow(IngredientCategory category, int index, int size) {
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

        Button up = categoryOrderButton("↑", "Kategorie nach oben verschieben");
        Button down = categoryOrderButton("↓", "Kategorie nach unten verschieben");
        up.setDisable(index == 0);
        down.setDisable(index == size - 1);
        up.setOnAction(ignored -> changeCategoryOrder(category, true));
        down.setOnAction(ignored -> changeCategoryOrder(category, false));

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

    private static Button categoryOrderButton(String text, String accessibleText) {
        Button button = new Button(text);
        button.setAccessibleText(accessibleText);
        button.getStyleClass().addAll("secondary-button", "inventory-category-order-button");
        return button;
    }

    private void changeCategoryOrder(IngredientCategory category, boolean upward) {
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

    private void showCategoryError(String message) {
        categoryFormError.setText(message);
        categoryFormError.setManaged(true);
        categoryFormError.setVisible(true);
    }

    private void clearCategoryError() {
        categoryFormError.setText("");
        categoryFormError.setManaged(false);
        categoryFormError.setVisible(false);
    }

    private void renderIngredientManagement(List<Ingredient> ingredients,
                                            List<IngredientCategory> categories) {
        ingredientManagementContainer.getChildren().clear();
        if (ingredients.isEmpty()) {
            Label empty = new Label("Noch keine zentralen Zutaten vorhanden.");
            empty.getStyleClass().add("card-text");
            ingredientManagementContainer.getChildren().add(empty);
            return;
        }
        ingredients.stream().map(ingredient -> ingredientManagementRow(ingredient, categories))
                .forEach(ingredientManagementContainer.getChildren()::add);
    }

    private HBox ingredientManagementRow(Ingredient ingredient,
                                         List<IngredientCategory> categories) {
        Label name = new Label(ingredient.getName());
        name.setMaxWidth(Double.MAX_VALUE);
        name.getStyleClass().add("inventory-ingredient-management-name");
        HBox.setHgrow(name, Priority.ALWAYS);
        Label category = new Label(ingredient.getCategory().getName());
        category.getStyleClass().add("inventory-ingredient-category-badge");
        Button edit = new Button("Bearbeiten");
        edit.getStyleClass().add("secondary-button");
        HBox row = new HBox(10, name, category, edit);
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
        ComboBox<IngredientCategory> category = new ComboBox<>(
                FXCollections.observableArrayList(categories));
        category.setConverter(new IngredientCategoryStringConverter());
        category.getSelectionModel().select(categories.stream()
                .filter(candidate -> candidate.getId().equals(
                        ingredient.getCategory().getId()))
                .findFirst().orElse(null));
        Button save = new Button("Speichern");
        save.getStyleClass().add("primary-button");
        Button cancel = new Button("Abbrechen");
        cancel.getStyleClass().add("secondary-button");
        row.getChildren().setAll(name, category, save, cancel);
        save.setOnAction(ignored -> {
            try {
                IngredientCategory selected = Objects.requireNonNull(category.getValue(),
                        "Bitte eine Kategorie auswählen.");
                ingredientManagementService.update(
                        ingredient.getId(), name.getText(), selected.getId());
                clearIngredientManagementError();
                refresh();
            } catch (NullPointerException | IllegalArgumentException | PersistenceException exception) {
                showIngredientManagementError(messageOf(exception));
            }
        });
        cancel.setOnAction(ignored -> refresh());
    }

    private void showIngredientManagementError(String message) {
        ingredientManagementError.setText(message);
        ingredientManagementError.setManaged(true);
        ingredientManagementError.setVisible(true);
    }

    private void clearIngredientManagementError() {
        ingredientManagementError.setText("");
        ingredientManagementError.setManaged(false);
        ingredientManagementError.setVisible(false);
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

    private static final class IngredientCategoryStringConverter
            extends StringConverter<IngredientCategory> {
        @Override public String toString(IngredientCategory category) {
            return category == null ? "" : category.getName();
        }
        @Override public IngredientCategory fromString(String value) {
            throw new UnsupportedOperationException("Category selection is not editable.");
        }
    }
}
