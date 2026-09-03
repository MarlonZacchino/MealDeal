package de.mealdeal.ui.controller;

import de.mealdeal.domain.Taste;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** Renders filtered taste options and the currently selected taste chips. */
final class TasteSelectionView {

    private final TextField filterField;
    private final FlowPane availableContainer;
    private final FlowPane selectedContainer;
    private final Label countLabel;
    private final Consumer<Taste> selectAction;
    private final Consumer<Taste> removeAction;
    private List<Taste> availableTastes = List.of();
    private List<Taste> selectedTastes = List.of();

    TasteSelectionView(TextField filterField,
                       FlowPane availableContainer,
                       FlowPane selectedContainer,
                       Label countLabel,
                       Consumer<Taste> selectAction,
                       Consumer<Taste> removeAction) {
        this.filterField = Objects.requireNonNull(filterField, "Filter field must not be null.");
        this.availableContainer = Objects.requireNonNull(
                availableContainer, "Available container must not be null.");
        this.selectedContainer = Objects.requireNonNull(
                selectedContainer, "Selected container must not be null.");
        this.countLabel = Objects.requireNonNull(countLabel, "Count label must not be null.");
        this.selectAction = Objects.requireNonNull(selectAction, "Select action must not be null.");
        this.removeAction = Objects.requireNonNull(removeAction, "Remove action must not be null.");
        filterField.textProperty().addListener(
                (ignored, previous, current) -> renderAvailableTastes());
    }

    void setAvailableTastes(List<Taste> tastes) {
        availableTastes = List.copyOf(Objects.requireNonNull(
                tastes, "Available tastes must not be null."));
        renderAvailableTastes();
    }

    void showSelection(List<Taste> tastes) {
        selectedTastes = List.copyOf(Objects.requireNonNull(
                tastes, "Selected tastes must not be null."));
        renderSelection();
        renderAvailableTastes();
    }

    void clearFilter() {
        filterField.clear();
    }

    void setFilterDisabled(boolean disabled) {
        filterField.setDisable(disabled);
    }

    private void renderAvailableTastes() {
        availableContainer.getChildren().clear();
        String filter = normalized(filterField.getText());
        List<Taste> visible = availableTastes.stream()
                .filter(taste -> !selectedTastes.contains(taste))
                .filter(taste -> normalized(taste.getName()).contains(filter))
                .toList();
        if (visible.isEmpty()) {
            Label noTastes = new Label(filter.isEmpty()
                    ? "Keine weiteren Geschmacksrichtungen verfügbar."
                    : "Keine passende Geschmacksrichtung gefunden.");
            noTastes.getStyleClass().add("card-text");
            availableContainer.getChildren().add(noTastes);
            return;
        }
        visible.forEach(taste -> {
            Button option = new Button(taste.getName());
            option.setOnAction(ignored -> selectAction.accept(taste));
            option.getStyleClass().add("taste-search-option");
            availableContainer.getChildren().add(option);
        });
    }

    private void renderSelection() {
        selectedContainer.getChildren().clear();
        countLabel.setText(selectedTastes.size() + " ausgewählt");
        if (selectedTastes.isEmpty()) {
            Label instruction = new Label("Noch keine Geschmacksrichtung ausgewählt.");
            instruction.getStyleClass().add("card-text");
            selectedContainer.getChildren().add(instruction);
            return;
        }
        selectedTastes.forEach(taste -> {
            Button chip = new Button(taste.getName() + "  ×");
            chip.setAccessibleText(taste.getName() + " aus Auswahl entfernen");
            chip.setOnAction(ignored -> removeAction.accept(taste));
            chip.getStyleClass().add("selected-taste-chip");
            selectedContainer.getChildren().add(chip);
        });
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
