package de.mealdeal.ui.controller;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.Unit;
import de.mealdeal.ui.form.IngredientFormInput;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;

import java.util.List;
import java.util.function.Consumer;

/** One dynamic ingredient row in the create-recipe form. */
final class IngredientFormRow {

    private final List<Ingredient> availableIngredients;
    private final HBox container = new HBox(12);
    private final ComboBox<Ingredient> ingredientInput = new ComboBox<>();
    private final TextField quantityInput = new TextField();
    private final ComboBox<Unit> unitInput = new ComboBox<>();
    private final Button removeButton = new Button("Entfernen");

    IngredientFormRow(List<Ingredient> availableIngredients,
                      Consumer<IngredientFormRow> removalHandler) {
        this.availableIngredients = availableIngredients;
        container.setAlignment(Pos.CENTER_LEFT);
        container.getStyleClass().add("form-row");

        ingredientInput.setEditable(true);
        ingredientInput.setPromptText("Zutat auswählen oder neu eingeben");
        ingredientInput.setMaxWidth(Double.MAX_VALUE);
        ingredientInput.setConverter(new IngredientStringConverter());
        HBox.setHgrow(ingredientInput, Priority.ALWAYS);

        quantityInput.setPromptText("Menge");
        quantityInput.setPrefColumnCount(8);
        unitInput.setPromptText("Einheit");
        unitInput.setItems(FXCollections.observableArrayList(Unit.values()));
        unitInput.setConverter(new GermanUnitStringConverter());

        removeButton.getStyleClass().add("danger-button");
        removeButton.setOnAction(event -> removalHandler.accept(this));
        container.getChildren().addAll(ingredientInput, quantityInput, unitInput, removeButton);
        refreshIngredients();
    }

    void refreshIngredients() {
        ingredientInput.setItems(FXCollections.observableArrayList(availableIngredients));
    }

    IngredientFormInput toInput() {
        return new IngredientFormInput(ingredientInput.getEditor().getText(),
                quantityInput.getText(), unitInput.getValue());
    }

    void setValue(RecipeIngredient recipeIngredient) {
        Ingredient selected = availableIngredients.stream()
                .filter(ingredient -> ingredient.getId().equals(
                        recipeIngredient.getIngredient().getId()))
                .findFirst()
                .orElse(recipeIngredient.getIngredient());
        ingredientInput.setValue(selected);
        ingredientInput.getEditor().setText(selected.getName());
        quantityInput.setText(GermanRecipeDisplay.decimal(recipeIngredient.getQuantity()));
        unitInput.setValue(recipeIngredient.getUnit());
    }

    HBox container() {
        return container;
    }

    void setRemovalDisabled(boolean disabled) {
        removeButton.setDisable(disabled);
    }

    private final class IngredientStringConverter extends StringConverter<Ingredient> {
        @Override
        public String toString(Ingredient ingredient) {
            return ingredient == null ? "" : ingredient.getName();
        }

        @Override
        public Ingredient fromString(String name) {
            if (name == null || name.isBlank()) {
                return null;
            }
            return availableIngredients.stream()
                    .filter(ingredient -> ingredient.getName().equalsIgnoreCase(name.strip()))
                    .findFirst().orElseGet(() -> new Ingredient(name));
        }
    }
}
