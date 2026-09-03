package de.mealdeal.ui.controller;

import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.Taste;
import de.mealdeal.service.CombinedSearchResult;
import de.mealdeal.service.IngredientSearchResult;
import de.mealdeal.service.MatchQuality;
import de.mealdeal.service.TasteSearchResult;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Renders search result cards and switches between result-area view states. */
final class RecipeSearchResultsView {

    private final VBox resultsContainer;
    private final VBox initialState;
    private final VBox emptyState;
    private final VBox errorState;
    private final Consumer<Recipe> openRecipeAction;

    RecipeSearchResultsView(VBox resultsContainer,
                            VBox initialState,
                            VBox emptyState,
                            VBox errorState,
                            Consumer<Recipe> openRecipeAction) {
        this.resultsContainer = Objects.requireNonNull(
                resultsContainer, "Results container must not be null.");
        this.initialState = Objects.requireNonNull(initialState, "Initial state must not be null.");
        this.emptyState = Objects.requireNonNull(emptyState, "Empty state must not be null.");
        this.errorState = Objects.requireNonNull(errorState, "Error state must not be null.");
        this.openRecipeAction = Objects.requireNonNull(
                openRecipeAction, "Open recipe action must not be null.");
    }

    void showInitial() {
        showOnly(initialState);
    }

    void showError() {
        showOnly(errorState);
    }

    void showResults(List<CombinedSearchResult> results) {
        Objects.requireNonNull(results, "Search results must not be null.");
        resultsContainer.getChildren().clear();
        resultsContainer.setAlignment(Pos.TOP_LEFT);
        resultsContainer.setMaxHeight(Region.USE_PREF_SIZE);
        VBox.setVgrow(resultsContainer, Priority.NEVER);
        if (results.isEmpty()) {
            showOnly(emptyState);
            return;
        }
        results.forEach(result -> resultsContainer.getChildren().add(resultEntry(result)));
        showOnly(resultsContainer);
    }

    private Button resultEntry(CombinedSearchResult result) {
        Recipe recipe = result.getRecipe();
        Label name = new Label(recipe.getName());
        name.getStyleClass().add("recipe-name");

        VBox content = new VBox(8, name);
        result.getIngredientResult().ifPresent(ingredientResult -> {
            content.getChildren().add(matchRow("Zutaten", ingredientResult.getMatchQuality(),
                    ingredientResult.getMatchedCount(), ingredientResult.getSelectedCount()));
            addMissingLabel(content, "Fehlende Zutaten", ingredientMissingText(ingredientResult));
        });
        result.getTasteResult().ifPresent(tasteResult -> {
            content.getChildren().add(matchRow("Geschmack", tasteResult.getMatchQuality(),
                    tasteResult.getMatchedCount(), tasteResult.getSelectedCount()));
            addMissingLabel(content, "Fehlende Geschmacksrichtungen",
                    tasteMissingText(tasteResult));
        });
        content.setAlignment(Pos.CENTER_LEFT);
        content.setMaxHeight(Region.USE_PREF_SIZE);

        Button entry = new Button();
        entry.setGraphic(content);
        entry.setMaxWidth(Double.MAX_VALUE);
        entry.prefHeightProperty().bind(content.heightProperty().add(44));
        entry.maxHeightProperty().bind(entry.prefHeightProperty());
        entry.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(entry, Priority.NEVER);
        entry.setAccessibleText("Gericht " + recipe.getName() + " öffnen");
        entry.setOnAction(ignored -> openRecipeAction.accept(recipe));
        entry.getStyleClass().add("search-result-item");
        return entry;
    }

    private static HBox matchRow(String filterName, MatchQuality quality,
                                 int matchedCount, int selectedCount) {
        Label filter = new Label(filterName + ":");
        filter.getStyleClass().add("search-filter-name");
        Label qualityLabel = new Label(quality.name());
        qualityLabel.getStyleClass().addAll("match-quality", qualityStyle(quality));
        Label count = new Label(matchedCount + "/" + selectedCount + " vorhanden");
        count.getStyleClass().add("recipe-facts");
        HBox row = new HBox(10, filter, qualityLabel, count);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static void addMissingLabel(VBox content, String label, String missingText) {
        if (missingText.isEmpty()) {
            return;
        }
        Label missing = new Label(label + ": " + missingText);
        missing.setWrapText(true);
        missing.getStyleClass().add("search-missing");
        content.getChildren().add(missing);
    }

    static String ingredientMissingText(IngredientSearchResult result) {
        return result.getMissingGroups().stream()
                .map(group -> group.getOptions().stream()
                        .map(option -> option.getIngredient().getName())
                        .reduce((first, second) -> first + " oder " + second)
                        .orElseThrow())
                .reduce((first, second) -> first + ", " + second)
                .orElse("");
    }

    private static String tasteMissingText(TasteSearchResult result) {
        return result.getMissingTastes().stream()
                .map(Taste::getName)
                .reduce((first, second) -> first + ", " + second)
                .orElse("");
    }

    private void showOnly(VBox visibleState) {
        for (VBox state : List.of(resultsContainer, initialState, emptyState, errorState)) {
            boolean visible = state == visibleState;
            state.setManaged(visible);
            state.setVisible(visible);
        }
    }

    private static String qualityStyle(MatchQuality quality) {
        return switch (quality) {
            case PERFECT -> "match-perfect";
            case GOOD -> "match-good";
            case PARTIAL -> "match-partial";
        };
    }
}
