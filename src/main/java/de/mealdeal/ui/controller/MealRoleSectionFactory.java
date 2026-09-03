package de.mealdeal.ui.controller;

import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.Recipe;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Builds the MAIN, SIDE and DESSERT sections of one weekly-plan day. */
final class MealRoleSectionFactory {

    private final MealPlanEntryRowFactory entryRows;

    MealRoleSectionFactory(MealPlanEntryRowFactory entryRows) {
        this.entryRows = entryRows;
    }

    VBox mainSection(Optional<MealPlanEntry> entry, boolean editing,
                     List<Recipe> recipes, MainActions actions,
                     Consumer<Recipe> onOpenRecipe) {
        VBox section = section("Hauptgericht");
        if (entry.isEmpty()) {
            if (editing) {
                ComboBox<Recipe> selection = entryRows.recipeSelection(
                        recipes, "Hauptgericht auswählen");
                selection.valueProperty().addListener((ignored, previous, selected) -> {
                    if (selected != null) {
                        actions.onRecipeSelected().accept(selected);
                    }
                });
                section.getChildren().add(selection);
            } else {
                section.getChildren().add(emptyLabel("Kein Hauptgericht geplant."));
            }
            return section;
        }

        MealPlanEntry main = entry.orElseThrow();
        section.getChildren().add(editing
                ? entryRows.editRow(main, recipes, "Hauptgericht auswählen",
                        "meal-plan-main-row", actions.entryActions(), null)
                : entryRows.viewRow(main, "meal-plan-main-row",
                        actions.onRemove(), onOpenRecipe));
        return section;
    }

    VBox sideSection(List<MealPlanEntry> entries, boolean editing,
                     List<Recipe> recipes, OrderedRoleActions actions,
                     Consumer<Recipe> onOpenRecipe) {
        return orderedSection("Beilagen", "+ Beilage hinzufügen", "Beilage auswählen",
                "Keine Beilagen-Rezepte verfügbar.", "Keine Beilage geplant.", "Beilage",
                "meal-plan-side-row", entries, editing, recipes, actions, onOpenRecipe);
    }

    VBox dessertSection(List<MealPlanEntry> entries, boolean editing,
                        List<Recipe> recipes, OrderedRoleActions actions,
                        Consumer<Recipe> onOpenRecipe) {
        return orderedSection("Nachtische", "+ Nachtisch hinzufügen", "Nachtisch auswählen",
                "Keine Nachtisch-Rezepte verfügbar.", "Kein Nachtisch geplant.", "Nachtisch",
                "meal-plan-dessert-row", entries, editing, recipes, actions, onOpenRecipe);
    }

    private VBox orderedSection(String title, String addText, String promptText,
                                String noRecipesText, String emptyText, String roleName,
                                String roleStyleClass, List<MealPlanEntry> entries,
                                boolean editing, List<Recipe> recipes,
                                OrderedRoleActions actions, Consumer<Recipe> onOpenRecipe) {
        Button add = secondaryButton(addText, actions.onAdd());
        add.setDisable(recipes.isEmpty());
        VBox section = section(title, editing ? new Button[]{add} : new Button[]{});
        if (entries.isEmpty()) {
            section.getChildren().add(emptyLabel(recipes.isEmpty() ? noRecipesText : emptyText));
            return section;
        }

        VBox rows = new VBox(10);
        for (int index = 0; index < entries.size(); index++) {
            int currentIndex = index;
            MealPlanEntry entry = entries.get(index);
            if (editing) {
                MealPlanEntryRowFactory.EntryActions entryActions =
                        new MealPlanEntryRowFactory.EntryActions(
                                recipe -> actions.onRecipeSelected().accept(currentIndex, recipe),
                                servings -> actions.onServingChanged().accept(
                                        currentIndex, servings),
                                () -> actions.onRemove().accept(currentIndex),
                                (groupId, optionId) -> actions.onAlternativeSelected().accept(
                                        currentIndex, groupId, optionId));
                MealPlanEntryRowFactory.ReorderActions reorder =
                        new MealPlanEntryRowFactory.ReorderActions(
                                index == 0, index == entries.size() - 1,
                                roleName + " nach oben verschieben",
                                roleName + " nach unten verschieben",
                                () -> actions.onMoveUp().accept(currentIndex),
                                () -> actions.onMoveDown().accept(currentIndex));
                rows.getChildren().add(entryRows.editRow(entry, recipes, promptText,
                        roleStyleClass, entryActions, reorder));
            } else {
                rows.getChildren().add(entryRows.viewRow(entry, roleStyleClass,
                        () -> actions.onRemove().accept(currentIndex), onOpenRecipe));
            }
        }
        section.getChildren().add(rows);
        return section;
    }

    private static VBox section(String title, Button... actions) {
        VBox section = new VBox(10, roleHeader(title, actions));
        section.getStyleClass().add("meal-plan-role-section");
        return section;
    }

    private static HBox roleHeader(String titleText, Button... actions) {
        Label title = new Label(titleText);
        title.getStyleClass().add("section-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, title, spacer);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setMaxWidth(Double.MAX_VALUE);
        header.getStyleClass().add("meal-plan-role-header");
        header.getChildren().addAll(actions);
        return header;
    }

    private static Label emptyLabel(String text) {
        Label empty = new Label(text);
        empty.getStyleClass().add("card-text");
        return empty;
    }

    private static Button secondaryButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary-button");
        button.setOnAction(ignored -> action.run());
        return button;
    }

    record MainActions(Consumer<Recipe> onRecipeSelected,
                       Consumer<Integer> onServingChanged,
                       Runnable onRemove,
                       BiConsumer<UUID, UUID> onAlternativeSelected) {
        MealPlanEntryRowFactory.EntryActions entryActions() {
            return new MealPlanEntryRowFactory.EntryActions(onRecipeSelected,
                    onServingChanged, onRemove, onAlternativeSelected);
        }
    }

    record OrderedRoleActions(Runnable onAdd,
                              IndexedValueConsumer<Recipe> onRecipeSelected,
                              IndexedValueConsumer<Integer> onServingChanged,
                              IntConsumer onRemove,
                              IntConsumer onMoveUp,
                              IntConsumer onMoveDown,
                              IndexedAlternativeConsumer onAlternativeSelected) {
    }

    @FunctionalInterface
    interface IndexedValueConsumer<T> {
        void accept(int index, T value);
    }

    @FunctionalInterface
    interface IndexedAlternativeConsumer {
        void accept(int index, UUID groupId, UUID optionId);
    }
}
