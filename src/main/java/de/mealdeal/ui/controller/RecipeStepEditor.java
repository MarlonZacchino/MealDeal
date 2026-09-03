package de.mealdeal.ui.controller;

import de.mealdeal.domain.Recipe;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/** Manages the dynamic, automatically numbered preparation rows of a recipe form. */
final class RecipeStepEditor {

    private final VBox container;
    private final List<RecipeStepFormRow> rows = new ArrayList<>();

    RecipeStepEditor(VBox container) {
        this.container = container;
    }

    void addRow() {
        RecipeStepFormRow row = new RecipeStepFormRow(this::removeRow);
        rows.add(row);
        container.getChildren().add(row.container());
        renumber();
    }

    void fill(Recipe recipe) {
        rows.clear();
        container.getChildren().clear();
        recipe.getSteps().forEach(step -> {
            RecipeStepFormRow row = new RecipeStepFormRow(this::removeRow);
            row.setDescription(step.getDescription());
            rows.add(row);
            container.getChildren().add(row.container());
        });
        renumber();
    }

    List<String> descriptions() {
        return rows.stream().map(RecipeStepFormRow::description).toList();
    }

    private void removeRow(RecipeStepFormRow row) {
        rows.remove(row);
        container.getChildren().remove(row.container());
        renumber();
    }

    private void renumber() {
        for (int index = 0; index < rows.size(); index++) {
            rows.get(index).setPosition(index + 1);
        }
    }
}
