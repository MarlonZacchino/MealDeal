package de.mealdeal.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Connects an ingredient to the quantity and unit used by one recipe.
 *
 * <p>This relationship is a separate object because quantity and unit belong
 * to a particular recipe, not to the centrally managed ingredient. Quantities
 * use {@link BigDecimal} so later calculations do not suffer from binary
 * floating-point rounding errors.</p>
 */
public final class RecipeIngredient {

    private final Ingredient ingredient;
    private final BigDecimal quantity;
    private final Unit unit;

    /**
     * Creates an ingredient entry for a recipe.
     *
     * @param ingredient the centrally managed ingredient
     * @param quantity a quantity greater than zero
     * @param unit the unit in which the quantity is expressed
     */
    public RecipeIngredient(Ingredient ingredient, BigDecimal quantity, Unit unit) {
        this.ingredient = Objects.requireNonNull(ingredient, "Ingredient must not be null.");
        this.quantity = requirePositive(quantity);
        this.unit = Objects.requireNonNull(unit, "Unit must not be null.");
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
