package de.mealdeal.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * One concrete ingredient choice within a recipe ingredient group.
 *
 * <p>Its UUID stays stable independently of persistence. Quantity and unit
 * belong to this option so alternatives can use different amounts.</p>
 */
public final class RecipeIngredientOption {

    private final UUID id;
    private final Ingredient ingredient;
    private final BigDecimal quantity;
    private final Unit unit;
    private final int position;

    /** Creates an option with a new stable technical identity. */
    public RecipeIngredientOption(Ingredient ingredient, BigDecimal quantity, Unit unit,
                                  int position) {
        this(UUID.randomUUID(), ingredient, quantity, unit, position);
    }

    /** Recreates an option with an existing stable technical identity. */
    public RecipeIngredientOption(UUID id, Ingredient ingredient, BigDecimal quantity, Unit unit,
                                  int position) {
        this.id = Objects.requireNonNull(id, "Recipe ingredient option ID must not be null.");
        this.ingredient = Objects.requireNonNull(ingredient, "Ingredient must not be null.");
        this.quantity = requirePositive(quantity);
        this.unit = Objects.requireNonNull(unit, "Unit must not be null.");
        if (position < 0) {
            throw new IllegalArgumentException("Recipe ingredient option position must not be negative.");
        }
        this.position = position;
    }

    public UUID getId() { return id; }

    public Ingredient getIngredient() { return ingredient; }

    public BigDecimal getQuantity() { return quantity; }

    public Unit getUnit() { return unit; }

    public int getPosition() { return position; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof RecipeIngredientOption option
                && id.equals(option.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    private static BigDecimal requirePositive(BigDecimal quantity) {
        if (quantity == null) {
            throw new NullPointerException("Quantity must not be null.");
        }
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
        return quantity;
    }
}
