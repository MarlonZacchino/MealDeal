package de.mealdeal.persistence.repository;

import de.mealdeal.domain.Recipe;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stores and retrieves complete recipes together with their relationships. */
public interface RecipeRepository {
    void save(Recipe recipe);

    Optional<Recipe> findById(UUID id);

    List<Recipe> findAll();

    boolean deleteById(UUID id);
}
