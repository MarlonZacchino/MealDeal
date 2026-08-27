package de.mealdeal.domain;

import java.util.List;
import java.util.Objects;

/**
 * Immutable shopping list derived from current meal-plan and recipe data.
 *
 * <p>The list is calculated rather than persisted so recipe or plan changes
 * cannot leave a second, outdated copy of the same information.</p>
 */
public final class ShoppingList {

    private final List<ShoppingListItem> items;

    public ShoppingList(List<ShoppingListItem> items) {
        Objects.requireNonNull(items, "Shopping list items must not be null.");
        if (items.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Shopping list items must not contain null values.");
        }
        this.items = List.copyOf(items);
    }

    public List<ShoppingListItem> getItems() {
        return items;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
