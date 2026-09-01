package de.mealdeal.persistence.repository;

import de.mealdeal.domain.InventoryItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stores the local inventory without exposing persistence-specific types. */
public interface InventoryRepository {

    void save(InventoryItem item);

    Optional<InventoryItem> findById(UUID id);

    /** Returns stock ordered by category position, ingredient name and stable ID. */
    List<InventoryItem> findAll();

    boolean deleteById(UUID id);
}
