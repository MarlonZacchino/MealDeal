package de.mealdeal.domain;

import java.util.Objects;

/** One aggregated ingredient and quantity in a derived shopping list. */
public final class ShoppingListItem {

    private final Ingredient ingredient;
    private final Quantity quantity;

    public ShoppingListItem(Ingredient ingredient, Quantity quantity) {
        this.ingredient = Objects.requireNonNull(ingredient, "Ingredient must not be null.");
        this.quantity = Objects.requireNonNull(quantity, "Quantity must not be null.");
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public Quantity getQuantity() {
        return quantity;
    }
}
