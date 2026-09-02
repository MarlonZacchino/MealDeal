package de.mealdeal.persistence.repository;

import de.mealdeal.domain.InventoryConsumption;
import de.mealdeal.domain.InventoryItem;

import java.util.List;
import java.util.UUID;

/** Stores immutable consumption history and inventory changes atomically. */
public interface InventoryConsumptionRepository {

    boolean existsByMealPlanEntryId(UUID mealPlanEntryId);

    List<InventoryConsumption> findAll();

    /** Atomically persists the snapshot and every supplied inventory update. */
    void saveWithInventoryUpdates(InventoryConsumption consumption,
                                  List<InventoryItem> inventoryUpdates);
}
