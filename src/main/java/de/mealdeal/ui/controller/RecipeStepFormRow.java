package de.mealdeal.ui.controller;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.function.Consumer;

/** One automatically numbered preparation row in the create-recipe form. */
final class RecipeStepFormRow {

    private final HBox container = new HBox(12);
    private final Label positionLabel = new Label();
    private final TextArea descriptionInput = new TextArea();
    private final Button removeButton = new Button("Entfernen");

    RecipeStepFormRow(Consumer<RecipeStepFormRow> removalHandler) {
        container.setAlignment(Pos.TOP_LEFT);
        container.getStyleClass().add("form-row");
        positionLabel.getStyleClass().add("step-position");
        descriptionInput.setPromptText("Zubereitung beschreiben");
        descriptionInput.setWrapText(true);
        descriptionInput.setPrefRowCount(2);
        descriptionInput.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(descriptionInput, Priority.ALWAYS);
        removeButton.getStyleClass().add("danger-button");
        removeButton.setOnAction(event -> removalHandler.accept(this));
        container.getChildren().addAll(positionLabel, descriptionInput, removeButton);
    }

    String description() {
        return descriptionInput.getText();
    }

    void setDescription(String description) {
        descriptionInput.setText(description);
    }

    HBox container() {
        return container;
    }

    void setPosition(int position) {
        positionLabel.setText(position + ".");
    }

    void setRemovalDisabled(boolean disabled) {
        removeButton.setDisable(disabled);
    }
}
