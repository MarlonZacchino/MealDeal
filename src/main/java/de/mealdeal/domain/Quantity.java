package de.mealdeal.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable combination of a positive decimal amount and its unit.
 *
 * <p>This value object supports calculations without changing the established
 * {@link RecipeIngredient} API or its persistence mapping.</p>
 */
public final class Quantity {

    private final BigDecimal amount;
    private final Unit unit;

    public Quantity(BigDecimal amount, Unit unit) {
        Objects.requireNonNull(amount, "Amount must not be null.");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        this.amount = amount;
        this.unit = Objects.requireNonNull(unit, "Unit must not be null.");
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Unit getUnit() {
        return unit;
    }

    /**
     * Adds another quantity after converting it to this quantity's unit.
     *
     * <p>The first operand's unit is deliberately retained. Presentation code
     * may later choose a prettier display unit without hiding that choice in
     * the business calculation.</p>
     *
     * @param other quantity to add
     * @return sum expressed in this quantity's unit
     */
    public Quantity add(Quantity other) {
        Objects.requireNonNull(other, "Quantity to add must not be null.");
        BigDecimal convertedAmount = UnitConverter.convert(other.amount, other.unit, unit);
        return new Quantity(amount.add(convertedAmount), unit);
    }
}
