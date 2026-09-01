package de.mealdeal.ui.controller;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.RecipeIngredientOption;
import de.mealdeal.domain.Unit;
import de.mealdeal.ui.form.IngredientOptionFormInput;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** One dynamic ingredient row in the create-recipe form. */
final class IngredientFormRow {

    private final List<Ingredient> availableIngredients;
    private final UUID optionId;
    private final HBox container = new HBox(12);
    private final ComboBox<Ingredient> ingredientInput = new ComboBox<>();
    private final TextField quantityInput = new TextField();
    private final ComboBox<Unit> unitInput = new ComboBox<>();
    private final RadioButton standardButton = new RadioButton("Standard");
    private final Button removeButton = new Button("Alternative entfernen");

    IngredientFormRow(UUID optionId, List<Ingredient> availableIngredients,
                      ToggleGroup standardGroup,
                      Consumer<IngredientFormRow> removalHandler) {
        this.optionId = optionId;
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
        unitInput.setItems(FXCollections.observableArrayList(availableUnits()));
        unitInput.setConverter(new GermanUnitStringConverter());

        standardButton.setToggleGroup(standardGroup);
        standardButton.getStyleClass().add("ingredient-standard-choice");

        removeButton.getStyleClass().add("danger-button");
        removeButton.setOnAction(event -> removalHandler.accept(this));
        container.getChildren().addAll(ingredientInput, quantityInput, unitInput,
                standardButton, removeButton);
        refreshIngredients();
    }

    void refreshIngredients() {
        ingredientInput.setItems(FXCollections.observableArrayList(availableIngredients));
    }

    IngredientOptionFormInput toInput(int position) {
        return new IngredientOptionFormInput(optionId, ingredientInput.getEditor().getText(),
                quantityInput.getText(), unitInput.getValue(), position);
    }

    void setValue(RecipeIngredientOption option) {
        Ingredient selected = availableIngredients.stream()
                .filter(ingredient -> ingredient.getId().equals(
                        option.getIngredient().getId()))
                .findFirst()
                .orElse(option.getIngredient());
        ingredientInput.setValue(selected);
        ingredientInput.getEditor().setText(selected.getName());
        quantityInput.setText(GermanRecipeDisplay.decimal(option.getQuantity()));
        unitInput.setValue(option.getUnit());
    }

    HBox container() {
        return container;
    }

    void setRemovalDisabled(boolean disabled) {
        removeButton.setDisable(disabled);
    }

    UUID optionId() {
        return optionId;
    }

    RadioButton standardButton() {
        return standardButton;
    }

    static List<Unit> availableUnits() {
        return List.of(Unit.values());
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
