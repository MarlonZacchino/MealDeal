package de.mealdeal.ui.controller;

import de.mealdeal.service.MealPlanDay;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** Builds one complete weekly-plan day card without owning its mutable state. */
final class WeekPlanDayCardFactory {

    private static final DateTimeFormatter DAY_NAME =
            DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN);
    private static final DateTimeFormatter FULL_DATE =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN);

    TitledPane create(MealPlanDay day, String summaryText, boolean expanded,
                      boolean editing, boolean changed, Node mainSection,
                      Node sideSection, Node dessertSection, Runnable onToggleEditing,
                      Consumer<Boolean> onExpandedChanged) {
        Objects.requireNonNull(day, "Meal plan day must not be null.");
        Objects.requireNonNull(summaryText, "Day summary must not be null.");
        Objects.requireNonNull(onToggleEditing, "Edit callback must not be null.");
        Objects.requireNonNull(onExpandedChanged, "Expansion callback must not be null.");

        Label localChangeNote = new Label(changed ? "Ungespeicherte Änderungen." : "");
        localChangeNote.setManaged(changed);
        localChangeNote.setVisible(changed);
        localChangeNote.setWrapText(true);
        localChangeNote.getStyleClass().add("meal-plan-unsaved-note");

        VBox content = new VBox(16, mainSection, sideSection, dessertSection, localChangeNote);
        content.setMaxWidth(Double.MAX_VALUE);
        content.getStyleClass().add("meal-plan-day-content");

        VBox title = dayHeader(day, summaryText, editing, onToggleEditing);
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);
        title.getStyleClass().add("meal-plan-day-title");

        TitledPane card = new TitledPane();
        card.setGraphic(title);
        card.setContent(content);
        card.setExpanded(expanded);
        card.setAnimated(true);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().addAll("expandable-card", "meal-plan-day-card");
        if (day.today()) {
            card.getStyleClass().add("meal-plan-day-today");
        }
        card.expandedProperty().addListener((ignored, previous, current) ->
                onExpandedChanged.accept(current));
        return card;
    }

    private static VBox dayHeader(MealPlanDay day, String summaryText, boolean editing,
                                  Runnable onToggleEditing) {
        Label dayName = new Label(titleCase(DAY_NAME.format(day.date())));
        dayName.getStyleClass().add("meal-plan-day-name");
        Label date = new Label(FULL_DATE.format(day.date()));
        date.getStyleClass().add("meal-plan-date");

        Label summary = new Label(summaryText);
        summary.setMinWidth(0);
        summary.setMaxWidth(Double.MAX_VALUE);
        summary.setWrapText(true);
        summary.getStyleClass().add("meal-plan-day-summary");
        HBox.setHgrow(summary, Priority.ALWAYS);

        HBox topRow = new HBox(20, dayName, summary);
        topRow.setAlignment(Pos.BASELINE_LEFT);
        topRow.setMinWidth(0);
        topRow.setMaxWidth(Double.MAX_VALUE);
        topRow.getStyleClass().add("meal-plan-day-top-row");
        if (day.today()) {
            Label today = new Label("Heute");
            today.getStyleClass().add("meal-plan-today-badge");
            topRow.getChildren().add(today);
        }
        Button edit = secondaryButton(editing ? "Fertig" : "Bearbeiten", onToggleEditing);
        edit.getStyleClass().add("meal-plan-day-edit-button");
        topRow.getChildren().add(edit);
        return new VBox(3, topRow, date);
    }

    private static Button secondaryButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary-button");
        button.setOnAction(ignored -> action.run());
        return button;
    }

    static String formatDate(java.time.LocalDate date) {
        return FULL_DATE.format(date);
    }

    private static String titleCase(String value) {
        return value.isEmpty() ? value
                : value.substring(0, 1).toUpperCase(Locale.GERMAN) + value.substring(1);
    }
}
