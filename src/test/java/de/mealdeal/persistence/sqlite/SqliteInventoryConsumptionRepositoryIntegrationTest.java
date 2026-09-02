package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.ConsumptionItem;
import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.InventoryConsumption;
import de.mealdeal.domain.InventoryItem;
import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteInventoryConsumptionRepositoryIntegrationTest {

    @TempDir Path temporaryDirectory;

    private SqliteDatabase database;
    private SqliteIngredientRepository ingredientRepository;
    private SqliteInventoryRepository inventoryRepository;
    private SqliteInventoryConsumptionRepository consumptionRepository;
    private Ingredient flour;
    private InventoryItem stock;

    @BeforeEach
    void setUp() {
        database = new SqliteDatabase(temporaryDirectory.resolve("consumption.db"));
        ingredientRepository = new SqliteIngredientRepository(database);
        inventoryRepository = new SqliteInventoryRepository(database);
        consumptionRepository = new SqliteInventoryConsumptionRepository(database);
        flour = new Ingredient("Mehl");
        ingredientRepository.save(flour);
        stock = new InventoryItem(flour, new BigDecimal("1000"), Unit.GRAM);
        inventoryRepository.save(stock);
    }

    @Test
    void atomicallyPersistsSnapshotAndInventoryUpdateWithStableIdentity() {
        UUID entryId = UUID.randomUUID();
        UUID consumptionId = UUID.randomUUID();
        Instant processedAt = Instant.parse("2026-09-10T08:15:30Z");
        InventoryConsumption consumption = new InventoryConsumption(
                consumptionId, entryId, LocalDate.of(2026, 9, 9), processedAt,
                List.of(new ConsumptionItem(flour.getId(),
                        new BigDecimal("750"), Unit.GRAM)));
        InventoryItem update = new InventoryItem(
                stock.getId(), flour, new BigDecimal("250"), Unit.GRAM);

        consumptionRepository.saveWithInventoryUpdates(consumption, List.of(update));

        InventoryConsumption loaded = consumptionRepository.findAll().getFirst();
        assertEquals(consumptionId, loaded.getId());
        assertEquals(entryId, loaded.getMealPlanEntryId());
        assertEquals(LocalDate.of(2026, 9, 9), loaded.getPlannedDate());
        assertEquals(processedAt, loaded.getProcessedAt());
        assertEquals(new BigDecimal("750"), loaded.getItems().getFirst().quantity());
        assertEquals(Unit.GRAM, loaded.getItems().getFirst().unit());
        assertEquals(new BigDecimal("250"), inventoryRepository.findById(stock.getId())
                .orElseThrow().getQuantity());
        assertTrue(consumptionRepository.existsByMealPlanEntryId(entryId));
    }

    @Test
    void databaseRejectsDuplicateEntryWithoutApplyingSecondInventoryUpdate() {
        UUID entryId = UUID.randomUUID();
        InventoryConsumption first = consumption(entryId, "400");
        consumptionRepository.saveWithInventoryUpdates(first, List.of(new InventoryItem(
                stock.getId(), flour, new BigDecimal("600"), Unit.GRAM)));
        InventoryConsumption duplicate = consumption(entryId, "600");

        assertThrows(PersistenceException.class,
                () -> consumptionRepository.saveWithInventoryUpdates(duplicate,
                        List.of(new InventoryItem(
                                stock.getId(), flour, BigDecimal.ZERO, Unit.GRAM))));

        assertEquals(1, consumptionRepository.findAll().size());
        assertEquals(new BigDecimal("600"), inventoryRepository.findById(stock.getId())
                .orElseThrow().getQuantity());
    }

    @Test
    void ledgerSurvivesDeletionOfOriginalMealPlanEntry() {
        Taste taste = new Taste("Herzhaft");
        new SqliteTasteRepository(database).save(taste);
        Recipe recipe = new Recipe("Brot", 1,
                List.of(new RecipeIngredient(flour, BigDecimal.ONE, Unit.GRAM)),
                List.of(), List.of(taste));
        new SqliteRecipeRepository(database).save(recipe);
        MealPlanEntry entry = new MealPlanEntry(LocalDate.of(2026, 9, 9), recipe, 1);
        SqliteMealPlanRepository mealPlans = new SqliteMealPlanRepository(database);
        mealPlans.save(entry);
        InventoryConsumption consumption = consumption(entry.getId(), "1");
        consumptionRepository.saveWithInventoryUpdates(consumption, List.of());

        assertTrue(mealPlans.deleteById(entry.getId()));

        assertEquals(entry.getId(), consumptionRepository.findAll().getFirst()
                .getMealPlanEntryId());
    }

    @Test
    void ledgerItemFailureRollsBackHeaderAndInventory() throws Exception {
        createFailureTrigger("""
                CREATE TRIGGER fail_consumption_item
                BEFORE INSERT ON inventory_consumption_items
                BEGIN SELECT RAISE(ABORT, 'item failure'); END
                """);

        assertThrows(PersistenceException.class,
                () -> consumptionRepository.saveWithInventoryUpdates(
                        consumption(UUID.randomUUID(), "500"), List.of(new InventoryItem(
                                stock.getId(), flour, new BigDecimal("500"), Unit.GRAM))));

        assertTrue(consumptionRepository.findAll().isEmpty());
        assertEquals(new BigDecimal("1000"), inventoryRepository.findById(stock.getId())
                .orElseThrow().getQuantity());
    }

    @Test
    void inventoryUpdateFailureRollsBackCompleteLedger() throws Exception {
        createFailureTrigger("""
                CREATE TRIGGER fail_inventory_update
                BEFORE UPDATE ON inventory_items
                BEGIN SELECT RAISE(ABORT, 'inventory failure'); END
                """);

        assertThrows(PersistenceException.class,
                () -> consumptionRepository.saveWithInventoryUpdates(
                        consumption(UUID.randomUUID(), "500"), List.of(new InventoryItem(
                                stock.getId(), flour, new BigDecimal("500"), Unit.GRAM))));

        assertTrue(consumptionRepository.findAll().isEmpty());
        assertEquals(new BigDecimal("1000"), inventoryRepository.findById(stock.getId())
                .orElseThrow().getQuantity());
    }

    private InventoryConsumption consumption(UUID entryId, String quantity) {
        return new InventoryConsumption(entryId, LocalDate.of(2026, 9, 9),
                Instant.parse("2026-09-10T08:15:30Z"),
                List.of(new ConsumptionItem(
                        flour.getId(), new BigDecimal(quantity), Unit.GRAM)));
    }

    private void createFailureTrigger(String sql) throws Exception {
        try (var connection = database.openConnection();
             var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
