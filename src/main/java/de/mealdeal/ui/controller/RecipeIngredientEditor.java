package de.mealdeal.ui.controller;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.IngredientCategories;
import de.mealdeal.domain.IngredientCategory;
import de.mealdeal.domain.Recipe;
import de.mealdeal.ui.form.IngredientGroupFormInput;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/** Manages the dynamic ingredient groups of the shared create/edit form. */
final class RecipeIngredientEditor {

    private final VBox container;
    private final List<IngredientGroupFormRow> groups = new ArrayList<>();
    private final List<Ingredient> availableIngredients = new ArrayList<>();
    private final List<IngredientCategory> availableCategories =
            new ArrayList<>(IngredientCategories.all());

    RecipeIngredientEditor(VBox container) {
        this.container = container;
    }

    void addGroup() {
        IngredientGroupFormRow group = new IngredientGroupFormRow(
                availableIngredients, availableCategories, this::removeGroup);
        groups.add(group);
        container.getChildren().add(group.container());
        updateRemoveButtons();
    }

    void fill(Recipe recipe) {
        groups.clear();
        container.getChildren().clear();
        recipe.getIngredientGroups().forEach(recipeGroup -> {
            IngredientGroupFormRow group = new IngredientGroupFormRow(
                    availableIngredients, availableCategories, this::removeGroup, recipeGroup);
            groups.add(group);
            container.getChildren().add(group.container());
        });
        if (groups.isEmpty()) {
            addGroup();
        } else {
            updateRemoveButtons();
        }
    }

    void replaceReferenceData(List<Ingredient> ingredients,
                              List<IngredientCategory> categories) {
        availableIngredients.clear();
        availableIngredients.addAll(ingredients);
        availableCategories.clear();
        availableCategories.addAll(categories);
        groups.forEach(IngredientGroupFormRow::refreshIngredients);
        groups.forEach(IngredientGroupFormRow::refreshCategories);
    }

    List<IngredientGroupFormInput> inputs() {
        return groups.stream().map(IngredientGroupFormRow::toInput).toList();
    }

    private void removeGroup(IngredientGroupFormRow group) {
        if (groups.size() <= 1) {
            return;
        }
        groups.remove(group);
        container.getChildren().remove(group.container());
        updateRemoveButtons();
    }

    private void updateRemoveButtons() {
        boolean disable = groups.size() == 1;
        groups.forEach(group -> group.setGroupRemovalDisabled(disable));
    }
}
