package de.mealdeal.ui.controller;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.ui.IngredientCategoryGrouping;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Renders the filtered category catalog and selected ingredient chips. */
final class IngredientSelectionView {

    private final TextField filterField;
    private final VBox availableContainer;
    private final FlowPane selectedContainer;
    private final Label countLabel;
    private final int maximumSelectionCount;
    private final Consumer<Ingredient> selectAction;
    private final Consumer<Ingredient> removeAction;
    private List<Ingredient> availableIngredients = List.of();
    private List<Ingredient> selectedIngredients = List.of();

    IngredientSelectionView(TextField filterField,
                            VBox availableContainer,
                            FlowPane selectedContainer,
                            Label countLabel,
                            int maximumSelectionCount,
                            Consumer<Ingredient> selectAction,
                            Consumer<Ingredient> removeAction) {
        this.filterField = Objects.requireNonNull(filterField, "Filter field must not be null.");
        this.availableContainer = Objects.requireNonNull(
                availableContainer, "Available container must not be null.");
        this.selectedContainer = Objects.requireNonNull(
                selectedContainer, "Selected container must not be null.");
        this.countLabel = Objects.requireNonNull(countLabel, "Count label must not be null.");
        if (maximumSelectionCount <= 0) {
            throw new IllegalArgumentException("Maximum selection count must be positive.");
        }
        this.maximumSelectionCount = maximumSelectionCount;
        this.selectAction = Objects.requireNonNull(selectAction, "Select action must not be null.");
        this.removeAction = Objects.requireNonNull(removeAction, "Remove action must not be null.");
        filterField.textProperty().addListener(
                (ignored, previous, current) -> renderAvailableIngredients());
    }

    void setAvailableIngredients(List<Ingredient> ingredients) {
        availableIngredients = List.copyOf(Objects.requireNonNull(
                ingredients, "Available ingredients must not be null."));
        renderAvailableIngredients();
    }

    void showSelection(List<Ingredient> ingredients) {
        selectedIngredients = List.copyOf(Objects.requireNonNull(
                ingredients, "Selected ingredients must not be null."));
        renderSelection();
        renderAvailableIngredients();
    }

    void clearFilter() {
        filterField.clear();
    }

    void setFilterDisabled(boolean disabled) {
        filterField.setDisable(disabled);
    }

    private void renderAvailableIngredients() {
        availableContainer.getChildren().clear();
        String filter = filterField.getText();
        List<UUID> selectedIds = selectedIngredients.stream().map(Ingredient::getId).toList();
        List<IngredientCategoryGrouping.Group> groups = IngredientCategoryGrouping.group(
                availableIngredients, filter, selectedIds);
        if (groups.isEmpty()) {
            Label noIngredients = new Label(isBlank(filter)
                    ? "Keine weiteren Zutaten verfügbar."
                    : "Keine passende Zutat gefunden.");
            noIngredients.getStyleClass().add("card-text");
            availableContainer.getChildren().add(noIngredients);
            return;
        }
        groups.forEach(group -> availableContainer.getChildren().add(
                categoryPane(group, filter)));
    }

    private TitledPane categoryPane(IngredientCategoryGrouping.Group group, String filter) {
        FlowPane ingredientOptions = new FlowPane(10, 10);
        ingredientOptions.getStyleClass().add("ingredient-category-options");
        group.ingredients().forEach(ingredient -> {
            Button option = new Button(ingredient.getName());
            option.setOnAction(ignored -> selectAction.accept(ingredient));
            option.getStyleClass().add("ingredient-option");
            ingredientOptions.getChildren().add(option);
        });
        TitledPane categoryPane = new TitledPane(group.category().getName(), ingredientOptions);
        categoryPane.setAnimated(true);
        categoryPane.setCollapsible(true);
        categoryPane.setExpanded(IngredientCategoryGrouping.shouldExpandForFilter(filter));
        categoryPane.setMaxWidth(Double.MAX_VALUE);
        categoryPane.getStyleClass().addAll("expandable-card", "ingredient-category-pane");
        return categoryPane;
    }

    private void renderSelection() {
        selectedContainer.getChildren().clear();
        countLabel.setText(selectedIngredients.size() + "/" + maximumSelectionCount
                + " ausgewählt");
        if (selectedIngredients.isEmpty()) {
            Label instruction = new Label("Noch keine Zutaten ausgewählt.");
            instruction.getStyleClass().add("card-text");
            selectedContainer.getChildren().add(instruction);
            return;
        }
        selectedIngredients.forEach(ingredient -> {
            Button chip = new Button(ingredient.getName() + "  ×");
            chip.setAccessibleText(ingredient.getName() + " aus Auswahl entfernen");
            chip.setOnAction(ignored -> removeAction.accept(ingredient));
            chip.getStyleClass().add("selected-ingredient-chip");
            selectedContainer.getChildren().add(chip);
        });
    }

    private static boolean isBlank(String value) {
        return value == null || value.strip().isEmpty();
    }
}
