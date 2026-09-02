package de.mealdeal.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** One resolved ingredient requirement stored as an immutable consumption snapshot. */
public record ConsumptionItem(UUID ingredientId, BigDecimal quantity, Unit unit) {

    public ConsumptionItem {
        Objects.requireNonNull(ingredientId, "Consumed ingredient ID must not be null.");
        Objects.requireNonNull(quantity, "Consumed quantity must not be null.");
        Objects.requireNonNull(unit, "Consumed unit must not be null.");
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("Consumed quantity must be positive.");
        }
    }
}
