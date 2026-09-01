package de.mealdeal.service;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.InventoryItem;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.DuplicateInventoryItemException;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.repository.IngredientRepository;
import de.mealdeal.persistence.repository.InventoryRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Orchestrates manual inventory editing without exposing persistence details to JavaFX. */
public final class InventoryService {

    private static final Comparator<Ingredient> INGREDIENT_ORDER =
            Comparator.comparingInt((Ingredient ingredient) ->
                            ingredient.getCategory().getPosition())
                    .thenComparing(Ingredient::getName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Ingredient::getName)
                    .thenComparing(Ingredient::getId);

    private final InventoryRepository inventoryRepository;
    private final IngredientRepository ingredientRepository;

    public InventoryService(InventoryRepository inventoryRepository,
                            IngredientRepository ingredientRepository) {
        this.inventoryRepository = Objects.requireNonNull(
                inventoryRepository, "Inventory repository must not be null.");
        this.ingredientRepository = Objects.requireNonNull(
                ingredientRepository, "Ingredient repository must not be null.");
    }

    /** Returns all central ingredients in category and name order for selection controls. */
    public List<Ingredient> loadAvailableIngredients() {
        return ingredientRepository.findAll().stream().sorted(INGREDIENT_ORDER).toList();
    }

    /** Returns inventory items grouped by their ingredient category. */
    public List<InventoryCategoryGroup> loadGroupedInventory() {
        Map<UUID, MutableGroup> groups = new LinkedHashMap<>();
        for (InventoryItem item : inventoryRepository.findAll()) {
            var category = item.getIngredient().getCategory();
            groups.computeIfAbsent(category.getId(), ignored -> new MutableGroup(category))
                    .items.add(item);
        }
        return groups.values().stream()
                .sorted(Comparator.comparingInt(group -> group.category.getPosition()))
                .map(group -> new InventoryCategoryGroup(group.category, group.items))
                .toList();
    }

    /** Adds stock for an existing central ingredient. Zero is a valid amount. */
    public InventoryItem add(UUID ingredientId, BigDecimal quantity, Unit unit) {
        Ingredient ingredient = ingredientRepository.findById(Objects.requireNonNull(
                        ingredientId, "Ingredient ID must not be null."))
                .orElseThrow(() -> new PersistenceException("Ingredient does not exist."));
        InventoryItem item = new InventoryItem(ingredient, quantity, unit);
        rejectDuplicate(item);
        inventoryRepository.save(item);
        return item;
    }

    /** Updates quantity and unit while retaining item and ingredient identities. */
    public InventoryItem update(UUID itemId, BigDecimal quantity, Unit unit) {
        InventoryItem existing = inventoryRepository.findById(Objects.requireNonNull(
                        itemId, "Inventory item ID must not be null."))
                .orElseThrow(() -> new PersistenceException("Inventory item does not exist."));
        InventoryItem updated = new InventoryItem(existing.getId(), existing.getIngredient(),
                quantity, unit);
        rejectDuplicate(updated);
        inventoryRepository.save(updated);
        return updated;
    }

    /** Deletes only the inventory item; the central ingredient remains untouched. */
    public boolean delete(UUID itemId) {
        return inventoryRepository.deleteById(Objects.requireNonNull(
                itemId, "Inventory item ID must not be null."));
    }

    private void rejectDuplicate(InventoryItem candidate) {
        boolean duplicate = inventoryRepository.findAll().stream()
                .anyMatch(existing -> !existing.getId().equals(candidate.getId())
                        && existing.getIngredient().getId()
                        .equals(candidate.getIngredient().getId())
                        && existing.getUnit() == candidate.getUnit());
        if (duplicate) {
            throw new DuplicateInventoryItemException(
                    "Für diese Zutat und Einheit existiert bereits ein Inventareintrag.");
        }
    }

    private static final class MutableGroup {
        private final de.mealdeal.domain.IngredientCategory category;
        private final List<InventoryItem> items = new ArrayList<>();

        private MutableGroup(de.mealdeal.domain.IngredientCategory category) {
            this.category = category;
        }
    }
}
