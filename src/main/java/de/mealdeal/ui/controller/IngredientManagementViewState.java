package de.mealdeal.ui.controller;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.ui.IngredientCategoryGrouping;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Keeps the transient category selection for the central ingredient presentation. */
final class IngredientManagementViewState {

    private List<IngredientCategoryGrouping.Group> groups = List.of();
    private UUID selectedCategoryId;

    void updateIngredients(List<Ingredient> ingredients) {
        groups = IngredientCategoryGrouping.group(
                Objects.requireNonNull(ingredients, "Ingredients must not be null."),
                "", List.of());
        if (selectedGroup().isEmpty()) {
            selectedCategoryId = null;
        }
    }

    List<IngredientCategoryGrouping.Group> groups() {
        return groups;
    }

    void selectCategory(UUID categoryId) {
        UUID requestedId = Objects.requireNonNull(
                categoryId, "Ingredient category ID must not be null.");
        if (groups.stream().noneMatch(group -> group.category().getId().equals(requestedId))) {
            throw new IllegalArgumentException("Unknown ingredient category.");
        }
        selectedCategoryId = requestedId;
    }

    Optional<IngredientCategoryGrouping.Group> selectedGroup() {
        if (selectedCategoryId == null) {
            return Optional.empty();
        }
        return groups.stream()
                .filter(group -> group.category().getId().equals(selectedCategoryId))
                .findFirst();
    }

    boolean isSelected(UUID categoryId) {
        return selectedCategoryId != null && selectedCategoryId.equals(categoryId);
    }
}
