package de.mealdeal.persistence.repository;

import de.mealdeal.domain.IngredientCategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stores and retrieves the ordered central ingredient categories. */
public interface IngredientCategoryRepository {

    void save(IngredientCategory category);

    Optional<IngredientCategory> findById(UUID id);

    List<IngredientCategory> findAll();
}
