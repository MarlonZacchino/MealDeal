package de.mealdeal.ui.controller;

import de.mealdeal.domain.ShoppingList;
import de.mealdeal.domain.ShoppingListItem;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.service.ShoppingListService;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Supplier;

/** Displays the shopping list derived for today or the remaining current week. */
public final class ShoppingListController {

    private static final System.Logger LOGGER =
            System.getLogger(ShoppingListController.class.getName());

    enum ViewMode {
        TODAY,
        CURRENT_WEEK
    }

    private final Supplier<ShoppingList> todayList;
    private final Supplier<ShoppingList> currentWeekList;
    private ViewMode viewMode = ViewMode.TODAY;

    @FXML
    private ToggleButton todayMode;
    @FXML
    private ToggleButton weekMode;
    @FXML
    private Label modeDescription;
    @FXML
    private Label itemCountLabel;
    @FXML
    private VBox itemsContainer;
    @FXML
    private VBox emptyState;
    @FXML
    private Label emptyTitle;
    @FXML
    private Label emptyMessage;
    @FXML
    private VBox errorState;
    @FXML
    private Label errorMessage;

    /** Creates the controller using the existing shopping-list calculation service. */
    public ShoppingListController(ShoppingListService shoppingListService) {
        this(Objects.requireNonNull(shoppingListService,
                        "Shopping list service must not be null.")::buildForToday,
                shoppingListService::buildForCurrentWeek);
    }

    ShoppingListController(Supplier<ShoppingList> todayList,
                           Supplier<ShoppingList> currentWeekList) {
        this.todayList = Objects.requireNonNull(todayList, "Today list must not be null.");
        this.currentWeekList = Objects.requireNonNull(
                currentWeekList, "Current-week list must not be null.");
    }

    @FXML
    private void initialize() {
        ToggleGroup modeGroup = new ToggleGroup();
        todayMode.setToggleGroup(modeGroup);
        weekMode.setToggleGroup(modeGroup);
        selectMode(ViewMode.TODAY);
    }

    @FXML
    private void showToday() {
        selectMode(ViewMode.TODAY);
    }

    @FXML
    private void showCurrentWeek() {
        selectMode(ViewMode.CURRENT_WEEK);
    }

    /** Reloads the selected derived list without retaining a second copy in the UI. */
    @FXML
    public void refresh() {
        try {
            render(loadForMode(viewMode));
        } catch (PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not load shopping list.", exception);
            showLoadError();
        }
    }

    ShoppingList loadForMode(ViewMode mode) {
        return switch (Objects.requireNonNull(mode, "View mode must not be null.")) {
            case TODAY -> todayList.get();
            case CURRENT_WEEK -> currentWeekList.get();
        };
    }

    private void selectMode(ViewMode selectedMode) {
        viewMode = Objects.requireNonNull(selectedMode, "View mode must not be null.");
        boolean todaySelected = viewMode == ViewMode.TODAY;
        todayMode.setSelected(todaySelected);
        weekMode.setSelected(!todaySelected);
        modeDescription.setText(todaySelected
                ? "Zutaten aus deiner Planung für heute."
                : "Zutaten aus allen Planungen von heute bis einschließlich Sonntag.");
        refresh();
    }

    private void render(ShoppingList shoppingList) {
        Objects.requireNonNull(shoppingList, "Shopping list must not be null.");
        itemsContainer.getChildren().clear();
        errorState.setManaged(false);
        errorState.setVisible(false);

        if (shoppingList.isEmpty()) {
            showEmptyState();
            return;
        }

        itemCountLabel.setText(itemCountText(shoppingList.getItems().size()));
        itemCountLabel.setManaged(true);
        itemCountLabel.setVisible(true);
        emptyState.setManaged(false);
        emptyState.setVisible(false);
        itemsContainer.getChildren().add(createHeader());
        shoppingList.getItems().stream()
                .map(this::createItemRow)
                .forEach(itemsContainer.getChildren()::add);
        itemsContainer.setManaged(true);
        itemsContainer.setVisible(true);
    }

    private HBox createHeader() {
        Label ingredient = columnLabel("Zutat", "shopping-list-header-ingredient");
        Label amount = columnLabel("Menge", "shopping-list-amount");
        Label unit = columnLabel("Einheit", "shopping-list-unit");
        HBox.setHgrow(ingredient, Priority.ALWAYS);

        HBox header = new HBox(16, ingredient, amount, unit);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("shopping-list-header");
        return header;
    }

    private HBox createItemRow(ShoppingListItem item) {
        Label ingredient = new Label(item.getIngredient().getName());
        ingredient.setMaxWidth(Double.MAX_VALUE);
        ingredient.setWrapText(true);
        ingredient.getStyleClass().add("shopping-list-ingredient");
        HBox.setHgrow(ingredient, Priority.ALWAYS);

        Label amount = columnLabel(displayAmount(item.getQuantity().getAmount()),
                "shopping-list-amount");
        Label unit = columnLabel(displayUnit(item.getQuantity().getAmount(),
                        item.getQuantity().getUnit()),
                "shopping-list-unit");

        HBox row = new HBox(16, ingredient, amount, unit);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("shopping-list-row");
        return row;
    }

    private void showEmptyState() {
        boolean todaySelected = viewMode == ViewMode.TODAY;
        emptyTitle.setText(todaySelected
                ? "Für heute ist nichts einzukaufen."
                : "Für den Rest der Woche ist nichts einzukaufen.");
        emptyMessage.setText(todaySelected
                ? "Plane für heute ein Gericht, damit hier die Zutaten erscheinen."
                : "Plane ein Gericht zwischen heute und Sonntag, damit hier die Zutaten erscheinen.");
        itemCountLabel.setManaged(false);
        itemCountLabel.setVisible(false);
        itemsContainer.setManaged(false);
        itemsContainer.setVisible(false);
        emptyState.setManaged(true);
        emptyState.setVisible(true);
    }

    private void showLoadError() {
        itemCountLabel.setManaged(false);
        itemCountLabel.setVisible(false);
        itemsContainer.setManaged(false);
        itemsContainer.setVisible(false);
        emptyState.setManaged(false);
        emptyState.setVisible(false);
        errorMessage.setText(
                "Die Einkaufsliste konnte nicht geladen werden. Bitte versuche es erneut.");
        errorState.setManaged(true);
        errorState.setVisible(true);
    }

    private static Label columnLabel(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    static String displayAmount(BigDecimal amount) {
        return GermanRecipeDisplay.decimal(amount);
    }

    static String displayUnit(BigDecimal amount, Unit unit) {
        return GermanRecipeDisplay.unit(amount, unit);
    }

    private static String itemCountText(int itemCount) {
        return itemCount + (itemCount == 1 ? " Position" : " Positionen");
    }
}
