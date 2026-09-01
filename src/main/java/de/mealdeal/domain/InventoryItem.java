package de.mealdeal.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** One locally stored amount of a central ingredient. */
public final class InventoryItem {

    private final UUID id;
    private final Ingredient ingredient;
    private final BigDecimal quantity;
    private final Unit unit;

    /** Creates an inventory item with a new stable identity. */
    public InventoryItem(Ingredient ingredient, BigDecimal quantity, Unit unit) {
        this(UUID.randomUUID(), ingredient, quantity, unit);
    }

    /** Recreates an inventory item with its persisted identity. */
    public InventoryItem(UUID id, Ingredient ingredient, BigDecimal quantity, Unit unit) {
        this.id = Objects.requireNonNull(id, "Inventory item ID must not be null.");
        this.ingredient = Objects.requireNonNull(
                ingredient, "Inventory item ingredient must not be null.");
        this.quantity = requireNonNegative(quantity);
        this.unit = Objects.requireNonNull(unit, "Inventory item unit must not be null.");
    }

    public UUID getId() {
        return id;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Unit getUnit() {
        return unit;
    }

    /** Returns this stock amount in a unit supported by the central converter. */
    public BigDecimal getQuantityIn(Unit targetUnit) {
        return UnitConverter.convert(quantity, unit,
                Objects.requireNonNull(targetUnit, "Target unit must not be null."));
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof InventoryItem item && id.equals(item.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    private static BigDecimal requireNonNegative(BigDecimal quantity) {
        Objects.requireNonNull(quantity, "Inventory item quantity must not be null.");
        if (quantity.signum() < 0) {
            throw new IllegalArgumentException(
                    "Inventory item quantity must not be negative.");
        }
        return quantity;
    }
}
