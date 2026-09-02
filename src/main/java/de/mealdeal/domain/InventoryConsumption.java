package de.mealdeal.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Historical snapshot of one meal-plan entry processed against inventory. */
public final class InventoryConsumption {

    private final UUID id;
    private final UUID mealPlanEntryId;
    private final LocalDate plannedDate;
    private final Instant processedAt;
    private final List<ConsumptionItem> items;

    public InventoryConsumption(UUID mealPlanEntryId, LocalDate plannedDate,
                                Instant processedAt, List<ConsumptionItem> items) {
        this(UUID.randomUUID(), mealPlanEntryId, plannedDate, processedAt, items);
    }

    /** Recreates a persisted historical snapshot with its stable identity. */
    public InventoryConsumption(UUID id, UUID mealPlanEntryId, LocalDate plannedDate,
                                Instant processedAt, List<ConsumptionItem> items) {
        this.id = Objects.requireNonNull(id, "Consumption ID must not be null.");
        this.mealPlanEntryId = Objects.requireNonNull(
                mealPlanEntryId, "Meal-plan entry ID must not be null.");
        this.plannedDate = Objects.requireNonNull(plannedDate, "Planned date must not be null.");
        this.processedAt = Objects.requireNonNull(
                processedAt, "Consumption timestamp must not be null.");
        Objects.requireNonNull(items, "Consumption items must not be null.");
        if (items.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Consumption items must not contain null values.");
        }
        this.items = List.copyOf(items);
    }

    public UUID getId() { return id; }
    public UUID getMealPlanEntryId() { return mealPlanEntryId; }
    public LocalDate getPlannedDate() { return plannedDate; }
    public Instant getProcessedAt() { return processedAt; }
    public List<ConsumptionItem> getItems() { return items; }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof InventoryConsumption consumption
                && id.equals(consumption.id);
    }

    @Override public int hashCode() { return id.hashCode(); }
}
