package de.mealdeal.ui.controller;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.IngredientCategory;
import de.mealdeal.domain.RecipeIngredientGroup;
import de.mealdeal.ui.form.IngredientGroupFormInput;
import de.mealdeal.ui.form.IngredientGroupFormState;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** One compact ingredient group with one or more alternative option rows. */
final class IngredientGroupFormRow {

    private final List<Ingredient> availableIngredients;
    private final List<IngredientCategory> availableCategories;
    private final Consumer<IngredientGroupFormRow> removalHandler;
    private final IngredientGroupFormState state;
    private final VBox container = new VBox(10);
    private final VBox optionContainer = new VBox(8);
    private final ToggleGroup standardGroup = new ToggleGroup();
    private final List<IngredientFormRow> optionRows = new ArrayList<>();
    private final Button removeGroupButton = new Button("Zutatengruppe entfernen");

    IngredientGroupFormRow(List<Ingredient> availableIngredients,
                           List<IngredientCategory> availableCategories,
                           Consumer<IngredientGroupFormRow> removalHandler) {
        this(availableIngredients, availableCategories, removalHandler,
                new IngredientGroupFormState(), null);
    }

    IngredientGroupFormRow(List<Ingredient> availableIngredients,
                           List<IngredientCategory> availableCategories,
                           Consumer<IngredientGroupFormRow> removalHandler,
                           RecipeIngredientGroup group) {
        this(availableIngredients, availableCategories, removalHandler,
                new IngredientGroupFormState(group), group);
    }

    private IngredientGroupFormRow(List<Ingredient> availableIngredients,
                                   List<IngredientCategory> availableCategories,
                                   Consumer<IngredientGroupFormRow> removalHandler,
                                   IngredientGroupFormState state,
                                   RecipeIngredientGroup group) {
        this.availableIngredients = availableIngredients;
        this.availableCategories = availableCategories;
        this.removalHandler = removalHandler;
        this.state = state;
        container.getStyleClass().add("ingredient-group-form");

        Label title = new Label("Zutat");
        title.getStyleClass().add("form-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        removeGroupButton.getStyleClass().add("danger-button");
        removeGroupButton.setOnAction(event -> removalHandler.accept(this));
        HBox header = new HBox(12, title, spacer, removeGroupButton);
        header.setAlignment(Pos.CENTER_LEFT);

        Button addAlternative = new Button("+ Alternative hinzufügen");
        addAlternative.getStyleClass().add("secondary-button");
        addAlternative.setOnAction(event -> addAlternative());
        container.getChildren().addAll(header, optionContainer, addAlternative);

        if (group == null) {
            addOptionRow(state.getOptionIds().getFirst(), null);
        } else {
            group.getOptions().forEach(option -> addOptionRow(option.getId(), option));
        }
        selectStandard(state.getStandardOptionId());
        updateOptionButtons();
    }

    void addAlternative() {
        addOptionRow(state.addOption(), null);
        updateOptionButtons();
    }

    private void addOptionRow(UUID optionId,
                              de.mealdeal.domain.RecipeIngredientOption option) {
        IngredientFormRow row = new IngredientFormRow(optionId, availableIngredients,
                availableCategories,
                standardGroup, this::removeOption);
        row.standardButton().setOnAction(event -> state.selectStandard(row.optionId()));
        if (option != null) {
            row.setValue(option);
        }
        optionRows.add(row);
        optionContainer.getChildren().add(row.container());
    }

    private void removeOption(IngredientFormRow row) {
        state.removeOption(row.optionId());
        optionRows.remove(row);
        optionContainer.getChildren().remove(row.container());
        selectStandard(state.getStandardOptionId());
        updateOptionButtons();
    }

    private void selectStandard(UUID optionId) {
        optionRows.stream().filter(row -> row.optionId().equals(optionId)).findFirst()
                .ifPresent(row -> row.standardButton().setSelected(true));
    }

    IngredientGroupFormInput toInput() {
        for (IngredientFormRow row : optionRows) {
            if (row.standardButton().isSelected()) {
                state.selectStandard(row.optionId());
            }
        }
        var options = java.util.stream.IntStream.range(0, optionRows.size())
                .mapToObj(index -> optionRows.get(index).toInput(index)).toList();
        return new IngredientGroupFormInput(state.getGroupId(), options,
                state.getStandardOptionId());
    }

    void refreshIngredients() {
        optionRows.forEach(IngredientFormRow::refreshIngredients);
    }

    void refreshCategories() {
        optionRows.forEach(IngredientFormRow::refreshCategories);
    }

    VBox container() {
        return container;
    }

    void setGroupRemovalDisabled(boolean disabled) {
        removeGroupButton.setDisable(disabled);
    }

    private void updateOptionButtons() {
        boolean disable = optionRows.size() == 1;
        optionRows.forEach(row -> row.setRemovalDisabled(disable));
    }
}
