package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.IngredientCategories;
import de.mealdeal.domain.InventoryItem;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteInventoryRepositoryIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    private SqliteIngredientRepository ingredientRepository;
    private SqliteInventoryRepository inventoryRepository;

    @BeforeEach
    void setUp() {
        SqliteDatabase database = new SqliteDatabase(
                temporaryDirectory.resolve("inventory.db"));
        ingredientRepository = new SqliteIngredientRepository(database);
        inventoryRepository = new SqliteInventoryRepository(database);
    }

    @Test
    void persistsZeroAndPositiveQuantitiesWithStableUuid() {
        Ingredient flour = saveIngredient("Mehl", IngredientCategories.BAKING);
        InventoryItem zero = new InventoryItem(flour, BigDecimal.ZERO, Unit.GRAM);
        InventoryItem positive = new InventoryItem(
                flour, new BigDecimal("1250.50"), Unit.GRAM);

        inventoryRepository.save(zero);
        inventoryRepository.save(positive);

        InventoryItem loadedZero = inventoryRepository.findById(zero.getId()).orElseThrow();
        InventoryItem loadedPositive = inventoryRepository.findById(
                positive.getId()).orElseThrow();
        assertEquals(zero.getId(), loadedZero.getId());
        assertEquals(BigDecimal.ZERO, loadedZero.getQuantity());
        assertEquals(new BigDecimal("1250.50"), loadedPositive.getQuantity());
        assertEquals(Unit.GRAM, loadedPositive.getUnit());
        assertEquals(IngredientCategories.BAKING,
                loadedPositive.getIngredient().getCategory());
    }

    @Test
    void updatesExistingItemWithoutChangingItsUuid() {
        Ingredient milk = saveIngredient("Milch", IngredientCategories.DAIRY);
        UUID id = UUID.randomUUID();
        inventoryRepository.save(new InventoryItem(
                id, milk, new BigDecimal("500"), Unit.MILLILITER));

        inventoryRepository.save(new InventoryItem(
                id, milk, new BigDecimal("1.5"), Unit.LITER));

        InventoryItem loaded = inventoryRepository.findById(id).orElseThrow();
        assertEquals(id, loaded.getId());
        assertEquals(new BigDecimal("1.5"), loaded.getQuantity());
        assertEquals(Unit.LITER, loaded.getUnit());
        assertEquals(1, inventoryRepository.findAll().size());
    }

    @Test
    void persistsSeparateNonConvertibleUnitsWithoutChangingThem() {
        Ingredient bread = saveIngredient(
                "Brot", IngredientCategories.GRAINS_RICE_AND_PASTA);
        inventoryRepository.save(new InventoryItem(
                bread, new BigDecimal("2"), Unit.PIECE));
        inventoryRepository.save(new InventoryItem(
                bread, new BigDecimal("6"), Unit.SLICE));

        assertEquals(Set.of(Unit.PIECE, Unit.SLICE), inventoryRepository.findAll().stream()
                .map(InventoryItem::getUnit).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void loadsByCategoryPositionThenIngredientName() {
        Ingredient zucchini = saveIngredient("Zucchini", IngredientCategories.VEGETABLES);
        Ingredient apple = saveIngredient("Apfel", IngredientCategories.FRUIT);
        Ingredient carrot = saveIngredient("Karotte", IngredientCategories.VEGETABLES);
        inventoryRepository.save(new InventoryItem(zucchini, BigDecimal.ONE, Unit.PIECE));
        inventoryRepository.save(new InventoryItem(carrot, BigDecimal.ONE, Unit.PIECE));
        inventoryRepository.save(new InventoryItem(apple, BigDecimal.ONE, Unit.PIECE));

        assertEquals(List.of("Apfel", "Karotte", "Zucchini"),
                inventoryRepository.findAll().stream()
                        .map(item -> item.getIngredient().getName()).toList());
    }

    @Test
    void deletesItemAndReportsUnknownId() {
        Ingredient ingredient = saveIngredient("Reis",
                IngredientCategories.GRAINS_RICE_AND_PASTA);
        InventoryItem item = new InventoryItem(ingredient, BigDecimal.ONE, Unit.KILOGRAM);
        inventoryRepository.save(item);

        assertTrue(inventoryRepository.deleteById(item.getId()));
        assertFalse(inventoryRepository.findById(item.getId()).isPresent());
        assertFalse(inventoryRepository.deleteById(UUID.randomUUID()));
    }

    @Test
    void enforcesCentralIngredientForeignKey() {
        Ingredient missing = new Ingredient("Nicht gespeichert", IngredientCategories.OTHER);

        assertThrows(PersistenceException.class, () -> inventoryRepository.save(
                new InventoryItem(missing, BigDecimal.ZERO, Unit.PIECE)));

        Ingredient saved = saveIngredient("Gespeichert", IngredientCategories.OTHER);
        inventoryRepository.save(new InventoryItem(saved, BigDecimal.ZERO, Unit.PIECE));
        assertThrows(PersistenceException.class,
                () -> ingredientRepository.deleteById(saved.getId()));
    }

    private Ingredient saveIngredient(String name,
                                      de.mealdeal.domain.IngredientCategory category) {
        Ingredient ingredient = new Ingredient(name, category);
        ingredientRepository.save(ingredient);
        return ingredient;
    }
}
