package de.mealdeal.service;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.IngredientCategories;
import de.mealdeal.domain.InventoryItem;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.DuplicateInventoryItemException;
import de.mealdeal.persistence.repository.IngredientRepository;
import de.mealdeal.persistence.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryServiceTest {

    private Ingredient apple;
    private Ingredient carrot;
    private Ingredient zucchini;
    private InMemoryIngredientRepository ingredients;
    private InMemoryInventoryRepository inventory;
    private InventoryService service;

    @BeforeEach
    void setUp() {
        apple = new Ingredient("Apfel", IngredientCategories.FRUIT);
        carrot = new Ingredient("Karotte", IngredientCategories.VEGETABLES);
        zucchini = new Ingredient("Zucchini", IngredientCategories.VEGETABLES);
        ingredients = new InMemoryIngredientRepository(List.of(zucchini, apple, carrot));
        inventory = new InMemoryInventoryRepository();
        service = new InventoryService(inventory, ingredients);
    }

    @Test
    void groupsItemsByCategoryAndKeepsCategoryThenIngredientOrder() {
        inventory.save(new InventoryItem(zucchini, BigDecimal.ONE, Unit.PIECE));
        inventory.save(new InventoryItem(carrot, new BigDecimal("2"), Unit.PIECE));
        inventory.save(new InventoryItem(apple, new BigDecimal("3"), Unit.PIECE));

        List<InventoryCategoryGroup> groups = service.loadGroupedInventory();

        assertEquals(List.of("Obst", "Gemüse"), groups.stream()
                .map(group -> group.category().getName()).toList());
        assertEquals(List.of("Karotte", "Zucchini"), groups.get(1).items().stream()
                .map(item -> item.getIngredient().getName()).toList());
        assertEquals(List.of("Apfel", "Karotte", "Zucchini"),
                service.loadAvailableIngredients().stream().map(Ingredient::getName).toList());
    }

    @Test
    void addsZeroUpdatesQuantityAndUnitAndKeepsStableIdentities() {
        InventoryItem added = service.add(carrot.getId(), BigDecimal.ZERO, Unit.GRAM);
        InventoryItem updated = service.update(
                added.getId(), new BigDecimal("1.5"), Unit.KILOGRAM);

        assertEquals(added.getId(), updated.getId());
        assertEquals(carrot.getId(), updated.getIngredient().getId());
        assertEquals(new BigDecimal("1.5"), updated.getQuantity());
        assertEquals(Unit.KILOGRAM, updated.getUnit());
        assertEquals(updated, inventory.findById(added.getId()).orElseThrow());
    }

    @Test
    void rejectsNegativeAndDuplicateIngredientUnitButAllowsDifferentUnits() {
        service.add(carrot.getId(), BigDecimal.ZERO, Unit.GRAM);

        assertThrows(IllegalArgumentException.class,
                () -> service.add(apple.getId(), new BigDecimal("-1"), Unit.PIECE));
        assertThrows(DuplicateInventoryItemException.class,
                () -> service.add(carrot.getId(), BigDecimal.ONE, Unit.GRAM));
        assertEquals(Unit.KILOGRAM,
                service.add(carrot.getId(), BigDecimal.ONE, Unit.KILOGRAM).getUnit());
    }

    @Test
    void changingUnitRejectsCollisionWithAnotherExistingItem() {
        InventoryItem grams = service.add(carrot.getId(), BigDecimal.ONE, Unit.GRAM);
        InventoryItem kilograms = service.add(carrot.getId(), BigDecimal.ONE, Unit.KILOGRAM);

        assertThrows(DuplicateInventoryItemException.class,
                () -> service.update(kilograms.getId(), BigDecimal.TEN, Unit.GRAM));
        assertEquals(Unit.GRAM, inventory.findById(grams.getId()).orElseThrow().getUnit());
        assertEquals(Unit.KILOGRAM,
                inventory.findById(kilograms.getId()).orElseThrow().getUnit());
    }

    @Test
    void deletesOnlyInventoryItemAndKeepsCentralIngredient() {
        InventoryItem item = service.add(apple.getId(), BigDecimal.ONE, Unit.PIECE);

        assertTrue(service.delete(item.getId()));
        assertFalse(inventory.findById(item.getId()).isPresent());
        assertTrue(ingredients.findById(apple.getId()).isPresent());
    }

    private static final class InMemoryInventoryRepository implements InventoryRepository {
        private final List<InventoryItem> entries = new ArrayList<>();

        @Override public void save(InventoryItem item) {
            entries.removeIf(existing -> existing.getId().equals(item.getId()));
            entries.add(item);
            entries.sort(java.util.Comparator
                    .comparingInt((InventoryItem value) ->
                            value.getIngredient().getCategory().getPosition())
                    .thenComparing(value -> value.getIngredient().getName(),
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(InventoryItem::getId));
        }
        @Override public Optional<InventoryItem> findById(UUID id) {
            return entries.stream().filter(item -> item.getId().equals(id)).findFirst();
        }
        @Override public List<InventoryItem> findAll() { return List.copyOf(entries); }
        @Override public boolean deleteById(UUID id) {
            return entries.removeIf(item -> item.getId().equals(id));
        }
    }

    private static final class InMemoryIngredientRepository implements IngredientRepository {
        private final List<Ingredient> entries;
        private InMemoryIngredientRepository(List<Ingredient> entries) {
            this.entries = new ArrayList<>(entries);
        }
        @Override public void save(Ingredient ingredient) {
            entries.removeIf(existing -> existing.getId().equals(ingredient.getId()));
            entries.add(ingredient);
        }
        @Override public Optional<Ingredient> findById(UUID id) {
            return entries.stream().filter(ingredient -> ingredient.getId().equals(id)).findFirst();
        }
        @Override public List<Ingredient> findAll() { return List.copyOf(entries); }
        @Override public boolean deleteById(UUID id) {
            return entries.removeIf(ingredient -> ingredient.getId().equals(id));
        }
    }
}
