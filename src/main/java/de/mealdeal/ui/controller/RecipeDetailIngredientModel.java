package de.mealdeal.ui.controller;

import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredientGroup;
import de.mealdeal.domain.RecipeIngredientOption;
import de.mealdeal.service.RecipeScaler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Holds non-persistent ingredient choices for one open recipe-detail view. */
final class RecipeDetailIngredientModel {

    private final Recipe recipe;
    private final RecipeScaler scaler;
    private final Map<UUID, UUID> selectedOptionIds = new LinkedHashMap<>();
    private int servingCount;

    RecipeDetailIngredientModel(Recipe recipe, RecipeScaler scaler) {
        this.recipe = Objects.requireNonNull(recipe, "Recipe must not be null.");
        this.scaler = Objects.requireNonNull(scaler, "Recipe scaler must not be null.");
        servingCount = recipe.getStandardServingCount();
        recipe.getIngredientGroups().forEach(group -> selectedOptionIds.put(
                group.getId(), group.getStandardOptionId()));
    }

    void setServingCount(int servingCount) {
        if (servingCount <= 0) {
            throw new IllegalArgumentException("Serving count must be positive.");
        }
        this.servingCount = servingCount;
    }

    void selectOption(UUID groupId, UUID optionId) {
        RecipeIngredientGroup group = recipe.getIngredientGroups().stream()
                .filter(candidate -> candidate.getId().equals(groupId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown ingredient group."));
        if (group.getOptions().stream().noneMatch(option -> option.getId().equals(optionId))) {
            throw new IllegalArgumentException("Ingredient option does not belong to its group.");
        }
        selectedOptionIds.put(groupId, optionId);
    }

    List<IngredientRow> rows() {
        return scaler.scaleIngredientGroups(recipe, servingCount).stream()
                .map(this::toRow)
                .toList();
    }

    private IngredientRow toRow(RecipeIngredientGroup scaledGroup) {
        UUID selectedId = selectedOptionIds.getOrDefault(
                scaledGroup.getId(), scaledGroup.getStandardOptionId());
        RecipeIngredientOption selected = scaledGroup.getOptions().stream()
                .filter(option -> option.getId().equals(selectedId))
                .findFirst()
                .orElse(scaledGroup.getStandardOption());
        List<Alternative> alternatives = scaledGroup.getOptions().stream()
                .map(option -> new Alternative(option.getId(), option.getIngredient().getName()))
                .toList();
        return new IngredientRow(scaledGroup.getId(), selected.getId(),
                selected.getIngredient().getName(),
                GermanRecipeDisplay.quantity(selected.getQuantity(), selected.getUnit()),
                alternatives);
    }

    record IngredientRow(UUID groupId, UUID selectedOptionId, String ingredientName,
                         String quantity, List<Alternative> alternatives) {
        IngredientRow {
            alternatives = List.copyOf(alternatives);
        }

        boolean hasAlternatives() {
            return alternatives.size() > 1;
        }
    }

    record Alternative(UUID id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }
}
