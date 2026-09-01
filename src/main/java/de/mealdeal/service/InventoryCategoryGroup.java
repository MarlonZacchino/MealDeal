package de.mealdeal.service;

import de.mealdeal.domain.IngredientCategory;
import de.mealdeal.domain.InventoryItem;

import java.util.List;
import java.util.Objects;

/** One category and its inventory items in deterministic display order. */
public record InventoryCategoryGroup(IngredientCategory category, List<InventoryItem> items) {

    public InventoryCategoryGroup {
        Objects.requireNonNull(category, "Inventory category must not be null.");
        items = List.copyOf(Objects.requireNonNull(items, "Inventory items must not be null."));
        if (items.stream().anyMatch(item -> !item.getIngredient().getCategory().equals(category))) {
            throw new IllegalArgumentException("Every inventory item must belong to its group.");
        }
    }
}
