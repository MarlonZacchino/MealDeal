package de.mealdeal.persistence.repository;

import de.mealdeal.domain.Taste;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stores and retrieves extensible tastes. */
public interface TasteRepository {
    void save(Taste taste);

    Optional<Taste> findById(UUID id);

    List<Taste> findAll();

    boolean deleteById(UUID id);
}
