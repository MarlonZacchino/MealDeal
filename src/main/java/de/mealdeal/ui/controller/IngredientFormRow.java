package de.mealdeal.ui.controller;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.IngredientCategories;
import de.mealdeal.domain.IngredientCategory;
import de.mealdeal.domain.RecipeIngredientOption;
import de.mealdeal.domain.Unit;
import de.mealdeal.ui.control.SearchableComboBoxSupport;
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
    private final List<IngredientCategory> availableCategories;
    private final UUID optionId;
    private final HBox container = new HBox(12);
    private final ComboBox<Ingredient> ingredientInput = new ComboBox<>();
    private final TextField quantityInput = new TextField();
    private final ComboBox<Unit> unitInput = new ComboBox<>();
    private final ComboBox<IngredientCategory> categoryInput = new ComboBox<>();
    private final SearchableComboBoxSupport<Ingredient> ingredientSearch;
    private final SearchableComboBoxSupport<IngredientCategory> categorySearch;
    private final RadioButton standardButton = new RadioButton("Standard");
    private final Button removeButton = new Button("Alternative entfernen");

    IngredientFormRow(UUID optionId, List<Ingredient> availableIngredients,
                      List<IngredientCategory> availableCategories,
                      ToggleGroup standardGroup,
                      Consumer<IngredientFormRow> removalHandler) {
        this.optionId = optionId;
        this.availableIngredients = availableIngredients;
        this.availableCategories = availableCategories;
        container.setAlignment(Pos.CENTER_LEFT);
        container.getStyleClass().add("form-row");

        ingredientInput.setPromptText("Zutat auswählen oder neu eingeben");
        ingredientInput.setMaxWidth(Double.MAX_VALUE);
        ingredientSearch = SearchableComboBoxSupport.allowingCustomText(
                ingredientInput, availableIngredients, new IngredientStringConverter());
        HBox.setHgrow(ingredientInput, Priority.ALWAYS);

        quantityInput.setPromptText("Menge");
        quantityInput.setPrefColumnCount(8);
        unitInput.setPromptText("Einheit");
        unitInput.setItems(FXCollections.observableArrayList(availableUnits()));
        unitInput.setConverter(new GermanUnitStringConverter());

        categoryInput.setPromptText("Kategorie");
        categoryInput.setPrefWidth(210);
        categorySearch = SearchableComboBoxSupport.forValidValues(
                categoryInput, availableCategories, IngredientCategory::getName);
        refreshCategories();
        categoryInput.setValue(IngredientCategories.OTHER);
        ingredientInput.getEditor().textProperty().addListener(
                (ignored, previous, current) -> updateCategoryForName(current));

        standardButton.setToggleGroup(standardGroup);
        standardButton.getStyleClass().add("ingredient-standard-choice");

        removeButton.getStyleClass().add("danger-button");
        removeButton.setOnAction(event -> removalHandler.accept(this));
        container.getChildren().addAll(ingredientInput, categoryInput, quantityInput, unitInput,
                standardButton, removeButton);
        refreshIngredients();
    }

    void refreshIngredients() {
        ingredientSearch.setOptions(availableIngredients);
        updateCategoryForName(ingredientInput.getEditor().getText());
    }

    void refreshCategories() {
        IngredientCategory selected = categoryInput.getValue();
        categorySearch.setOptions(availableCategories);
        if (selected != null) {
            categoryInput.setValue(availableCategories.stream()
                    .filter(category -> category.getId().equals(selected.getId()))
                    .findFirst().orElse(selected));
        }
    }

    IngredientOptionFormInput toInput(int position) {
        return new IngredientOptionFormInput(optionId, ingredientInput.getEditor().getText(),
                quantityInput.getText(), unitInput.getValue(), position,
                categoryInput.getValue());
    }

    void setValue(RecipeIngredientOption option) {
        Ingredient selected = availableIngredients.stream()
                .filter(ingredient -> ingredient.getId().equals(
                        option.getIngredient().getId()))
                .findFirst()
                .orElse(option.getIngredient());
        ingredientInput.setValue(selected);
        ingredientInput.getEditor().setText(selected.getName());
        categoryInput.setValue(selected.getCategory());
        categoryInput.setDisable(true);
        quantityInput.setText(GermanRecipeDisplay.editableDecimal(option.getQuantity()));
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

    private void updateCategoryForName(String name) {
        Ingredient existing = availableIngredients.stream()
                .filter(ingredient -> name != null
                        && ingredient.getName().equalsIgnoreCase(name.strip()))
                .findFirst().orElse(null);
        if (existing != null) {
            categoryInput.setValue(existing.getCategory());
            categoryInput.setDisable(true);
        } else {
            if (categoryInput.isDisabled() || categoryInput.getValue() == null) {
                categoryInput.setValue(IngredientCategories.OTHER);
            }
            categoryInput.setDisable(false);
        }
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
                    .findFirst().orElseGet(() -> new Ingredient(name,
                            categoryInput.getValue() == null
                                    ? IngredientCategories.OTHER : categoryInput.getValue()));
        }
    }

}
