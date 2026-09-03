package de.mealdeal.ui.controller;

import de.mealdeal.domain.Recipe;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;

import java.util.List;
import java.util.Locale;

/** Manages selectable and newly entered tastes in the shared recipe form. */
final class RecipeTasteEditor {

    private final FlowPane optionsContainer;
    private final TextField newTasteField;

    RecipeTasteEditor(FlowPane optionsContainer, TextField newTasteField) {
        this.optionsContainer = optionsContainer;
        this.newTasteField = newTasteField;
    }

    boolean addEnteredTaste() {
        String name = newTasteField.getText() == null ? "" : newTasteField.getText().strip();
        if (name.isEmpty()) {
            return false;
        }
        CheckBox existing = find(name);
        if (existing != null) {
            existing.setSelected(true);
        } else {
            addOption(name, true);
        }
        newTasteField.clear();
        return true;
    }

    void addAvailableTaste(String name) {
        if (find(name) == null) {
            addOption(name, false);
        }
    }

    void fill(Recipe recipe) {
        recipe.getTastes().forEach(taste -> {
            CheckBox option = find(taste.getName());
            if (option == null) {
                addOption(taste.getName(), true);
            } else {
                option.setSelected(true);
            }
        });
    }

    List<String> selectedNames() {
        return optionsContainer.getChildren().stream()
                .filter(CheckBox.class::isInstance)
                .map(CheckBox.class::cast)
                .filter(CheckBox::isSelected)
                .map(CheckBox::getText)
                .toList();
    }

    private CheckBox find(String name) {
        String normalizedName = name.toLowerCase(Locale.ROOT);
        return optionsContainer.getChildren().stream()
                .filter(CheckBox.class::isInstance)
                .map(CheckBox.class::cast)
                .filter(checkBox -> checkBox.getText().strip().toLowerCase(Locale.ROOT)
                        .equals(normalizedName))
                .findFirst().orElse(null);
    }

    private void addOption(String name, boolean selected) {
        CheckBox checkBox = new CheckBox(name);
        checkBox.setSelected(selected);
        checkBox.getStyleClass().add("taste-option");
        optionsContainer.getChildren().add(checkBox);
    }
}
