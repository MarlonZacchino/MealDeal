package de.mealdeal.persistence.repository;

import de.mealdeal.domain.IngredientCategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stores and retrieves the ordered central ingredient categories. */
public interface IngredientCategoryRepository {

    void save(IngredientCategory category);

    /** Atomically stores the complete catalog with its normalized positions. */
    void replaceAll(List<IngredientCategory> categories);

    Optional<IngredientCategory> findById(UUID id);

    List<IngredientCategory> findAll();

    /** Returns how many central ingredients currently belong to the category. */
    int countIngredients(UUID categoryId);

    /**
     * Atomically reassigns affected ingredients, deletes one category and stores the new order.
     */
    void deleteAndReassign(UUID categoryId, UUID fallbackCategoryId,
                           List<IngredientCategory> remainingCategories);
}
