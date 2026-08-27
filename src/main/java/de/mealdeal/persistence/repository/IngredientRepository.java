package de.mealdeal.persistence.repository;

import de.mealdeal.domain.Ingredient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stores and retrieves centrally managed ingredients. */
public interface IngredientRepository {
    void save(Ingredient ingredient);

    Optional<Ingredient> findById(UUID id);

    List<Ingredient> findAll();

    boolean deleteById(UUID id);
}
