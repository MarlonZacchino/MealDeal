package de.mealdeal.ui.controller;

import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredientOption;
import de.mealdeal.ui.control.SearchableComboBoxSupport;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Builds compact view and edit rows for one planned meal entry. */
final class MealPlanEntryRowFactory {

    private static final int MAX_SERVING_COUNT = 999;

    ComboBox<Recipe> recipeSelection(List<Recipe> recipes, String promptText) {
        ComboBox<Recipe> selection = new ComboBox<>();
        SearchableComboBoxSupport.forValidValues(selection, recipes, Recipe::getName);
        selection.setPromptText(recipes.isEmpty() ? "Keine Gerichte verfügbar" : promptText);
        selection.setDisable(recipes.isEmpty());
        selection.setMinWidth(0);
        selection.setMaxWidth(Double.MAX_VALUE);
        selection.getStyleClass().add("meal-plan-recipe-picker");
        return selection;
    }

    HBox viewRow(MealPlanEntry entry, String roleStyleClass, Runnable onRemove,
                 Consumer<Recipe> onOpenRecipe) {
        Button recipeLink = recipeLink(entry, onOpenRecipe);
        recipeLink.setMinWidth(0);
        recipeLink.setMaxWidth(Double.MAX_VALUE);
        recipeLink.setWrapText(true);
        HBox.setHgrow(recipeLink, Priority.ALWAYS);

        Label servings = new Label(servingCountText(entry.getServingCount()));
        servings.setMinWidth(Region.USE_PREF_SIZE);
        servings.getStyleClass().add("meal-plan-serving-text");
        Button remove = removeButton(onRemove);
        remove.setMinWidth(Region.USE_PREF_SIZE);
        HBox actions = new HBox(12, servings, remove);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setMinWidth(Region.USE_PREF_SIZE);
        actions.getStyleClass().add("meal-plan-view-actions");

        HBox row = new HBox(16, recipeLink, actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);
        row.getStyleClass().addAll(roleStyleClass, "meal-plan-view-row");
        return row;
    }

    VBox editRow(MealPlanEntry entry, List<Recipe> recipes, String promptText,
                 String roleStyleClass, EntryActions actions, ReorderActions reorder) {
        ComboBox<Recipe> selection = recipeSelection(recipes, promptText);
        selection.setValue(entry.getRecipe());
        selection.valueProperty().addListener((ignored, previous, selected) -> {
            if (selected != null) {
                actions.onRecipeSelected().accept(selected);
            }
        });

        Spinner<Integer> servings = servingSpinner(entry.getServingCount());
        servings.valueProperty().addListener((ignored, previous, selected) ->
                actions.onServingChanged().accept(selected));

        VBox recipeField = labeledControl("Gericht", selection);
        recipeField.getStyleClass().add("meal-plan-recipe-field");
        VBox servingField = labeledControl("Personen", servings);

        FlowPane entryActions = controls(servingField);
        entryActions.getStyleClass().add("meal-plan-entry-actions");
        if (reorder != null) {
            entryActions.getChildren().addAll(orderButton("↑", reorder.upAccessibleText(),
                            reorder.upDisabled(), reorder.onMoveUp()),
                    orderButton("↓", reorder.downAccessibleText(),
                            reorder.downDisabled(), reorder.onMoveDown()));
        }
        entryActions.getChildren().add(removeButton(actions.onRemove()));

        FlowPane controls = controls(recipeField, entryActions);

        VBox alternatives = alternativeSelections(entry, actions.onAlternativeSelected());
        VBox row = new VBox(7, controls);
        if (!alternatives.getChildren().isEmpty()) {
            row.getChildren().add(alternatives);
        }
        row.getStyleClass().addAll(roleStyleClass, "meal-plan-edit-row");
        return row;
    }

    static String servingCountText(int servingCount) {
        return servingCount + (servingCount == 1 ? " Person" : " Personen");
    }

    private static VBox alternativeSelections(
            MealPlanEntry entry, BiConsumer<UUID, UUID> onAlternativeSelected) {
        VBox container = new VBox(8);
        container.getStyleClass().add("meal-plan-ingredient-selections");
        entry.getRecipe().getIngredientGroups().stream()
                .filter(group -> group.getOptions().size() > 1)
                .forEach(group -> {
                    ComboBox<RecipeIngredientOption> selection = new ComboBox<>();
                    SearchableComboBoxSupport.forValidValues(selection, group.getOptions(),
                            MealPlanEntryRowFactory::optionText);
                    selection.setValue(entry.getSelectedOption(group));
                    selection.setMaxWidth(Double.MAX_VALUE);
                    selection.getStyleClass().add("meal-plan-ingredient-picker");
                    selection.valueProperty().addListener((ignored, previous, selected) -> {
                        if (selected != null) {
                            onAlternativeSelected.accept(group.getId(), selected.getId());
                        }
                    });
                    String groupName = group.getOptions().stream()
                            .map(option -> option.getIngredient().getName())
                            .reduce((first, second) -> first + " oder " + second)
                            .orElseThrow();
                    container.getChildren().add(labeledControl(groupName, selection));
                });
        return container;
    }

    private static String optionText(RecipeIngredientOption option) {
        return option == null ? "" : GermanRecipeDisplay.quantity(
                option.getQuantity(), option.getUnit()) + " "
                + option.getIngredient().getName();
    }

    private static Spinner<Integer> servingSpinner(int value) {
        Spinner<Integer> spinner = new Spinner<>();
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                1, MAX_SERVING_COUNT, value));
        spinner.setEditable(false);
        spinner.getStyleClass().add("meal-plan-serving-spinner");
        return spinner;
    }

    private static Button recipeLink(MealPlanEntry entry, Consumer<Recipe> onOpenRecipe) {
        Button link = new Button(entry.getRecipe().getName());
        link.setAccessibleText("Details zu " + entry.getRecipe().getName() + " öffnen");
        link.setOnAction(ignored -> onOpenRecipe.accept(entry.getRecipe()));
        link.getStyleClass().add("meal-plan-recipe-link");
        return link;
    }

    private static FlowPane controls(Node... nodes) {
        FlowPane controls = new FlowPane(12, 10);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setRowValignment(VPos.BOTTOM);
        controls.setMaxWidth(Double.MAX_VALUE);
        controls.getStyleClass().add("meal-plan-controls");
        controls.getChildren().addAll(nodes);
        return controls;
    }

    private static VBox labeledControl(String text, Node control) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        VBox field = new VBox(6, label, control);
        field.setMinWidth(0);
        field.setMaxWidth(Double.MAX_VALUE);
        field.getStyleClass().add("meal-plan-control-field");
        return field;
    }

    private static Button orderButton(String text, String accessibleText, boolean disabled,
                                      Runnable action) {
        Button button = new Button(text);
        button.setAccessibleText(accessibleText);
        button.setDisable(disabled);
        button.setOnAction(ignored -> action.run());
        return button;
    }

    private static Button removeButton(Runnable action) {
        Button button = new Button("Entfernen");
        button.getStyleClass().add("danger-button");
        button.setOnAction(ignored -> action.run());
        return button;
    }

    record EntryActions(Consumer<Recipe> onRecipeSelected,
                        Consumer<Integer> onServingChanged,
                        Runnable onRemove,
                        BiConsumer<UUID, UUID> onAlternativeSelected) {
    }

    record ReorderActions(boolean upDisabled, boolean downDisabled,
                          String upAccessibleText, String downAccessibleText,
                          Runnable onMoveUp, Runnable onMoveDown) {
    }
}
