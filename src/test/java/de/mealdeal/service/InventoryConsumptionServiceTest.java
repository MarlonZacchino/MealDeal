package de.mealdeal.service;

import de.mealdeal.domain.ConsumptionItem;
import de.mealdeal.domain.DishType;
import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.InventoryConsumption;
import de.mealdeal.domain.InventoryItem;
import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.MealRole;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.RecipeIngredientGroup;
import de.mealdeal.domain.RecipeIngredientOption;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.repository.InventoryConsumptionRepository;
import de.mealdeal.persistence.repository.InventoryRepository;
import de.mealdeal.persistence.repository.MealPlanRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryConsumptionServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 10);

    @Test
    void processesOnlyUnprocessedPastEntriesAndIsIdempotentAcrossReloads() {
        Ingredient ingredient = new Ingredient("Reis");
        Recipe recipe = recipe("Reis", DishType.MAIN, 1, ingredient, "100", Unit.GRAM);
        MealPlanEntry twoDaysAgo = new MealPlanEntry(TODAY.minusDays(2), recipe, 1);
        MealPlanEntry yesterday = new MealPlanEntry(TODAY.minusDays(1), recipe, 2);
        MealPlanEntry today = new MealPlanEntry(TODAY, recipe, 3);
        MealPlanEntry tomorrow = new MealPlanEntry(TODAY.plusDays(1), recipe, 4);
        InMemoryInventoryRepository inventory = new InMemoryInventoryRepository(List.of(
                new InventoryItem(ingredient, new BigDecimal("1000"), Unit.GRAM)));
        InMemoryConsumptionRepository ledger = new InMemoryConsumptionRepository(inventory);
        InMemoryMealPlanRepository plans = new InMemoryMealPlanRepository(
                List.of(tomorrow, today, yesterday, twoDaysAgo));

        InventoryConsumptionService first = service(plans, inventory, ledger);
        InventoryConsumptionService restarted = service(plans, inventory, ledger);

        assertEquals(2, first.consumePastEntries());
        assertEquals(new BigDecimal("700"), inventory.findAll().getFirst().getQuantity());
        assertEquals(0, first.consumePastEntries());
        assertEquals(0, restarted.consumePastEntries());
        assertEquals(2, ledger.findAll().size());
        assertEquals(List.of(twoDaysAgo.getId(), yesterday.getId()), ledger.findAll().stream()
                .map(InventoryConsumption::getMealPlanEntryId).toList());
    }

    @Test
    void neverCreatesNegativeStockForInsufficientExactOrZeroInventory() {
        Ingredient eggs = new Ingredient("Eier");
        Ingredient garlic = new Ingredient("Knoblauch");
        Recipe recipe = new Recipe("Gericht", 1, List.of(
                new RecipeIngredient(eggs, new BigDecimal("6"), Unit.PIECE),
                new RecipeIngredient(garlic, new BigDecimal("4"), Unit.CLOVE)),
                List.of(), List.of(new Taste("Herzhaft")));
        MealPlanEntry entry = new MealPlanEntry(TODAY.minusDays(1), recipe, 1);
        InventoryItem eggsStock = new InventoryItem(eggs, new BigDecimal("4"), Unit.PIECE);
        InventoryItem garlicStock = new InventoryItem(garlic, BigDecimal.ZERO, Unit.CLOVE);
        InMemoryInventoryRepository inventory = new InMemoryInventoryRepository(
                List.of(eggsStock, garlicStock));
        InMemoryConsumptionRepository ledger = new InMemoryConsumptionRepository(inventory);

        service(new InMemoryMealPlanRepository(List.of(entry)), inventory, ledger)
                .consumePastEntries();

        assertEquals(BigDecimal.ZERO,
                inventory.findById(eggsStock.getId()).orElseThrow().getQuantity());
        assertEquals(BigDecimal.ZERO,
                inventory.findById(garlicStock.getId()).orElseThrow().getQuantity());
        assertEquals(List.of(new BigDecimal("6"), new BigDecimal("4")), ledger.findAll()
                .getFirst().getItems().stream().map(ConsumptionItem::quantity).toList());
    }

    @Test
    void convertsMassAndVolumeAndDistributesAcrossCompatibleItemsDeterministically() {
        Ingredient flour = new Ingredient("Mehl");
        Ingredient milk = new Ingredient("Milch");
        Recipe recipe = new Recipe("Teig", 1, List.of(
                new RecipeIngredient(flour, new BigDecimal("1"), Unit.KILOGRAM),
                new RecipeIngredient(milk, new BigDecimal("1500"), Unit.MILLILITER)),
                List.of(), List.of(new Taste("Mild")));
        MealPlanEntry entry = new MealPlanEntry(TODAY.minusDays(1), recipe, 1);
        InventoryItem firstFlour = item(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                flour, "300", Unit.GRAM);
        InventoryItem secondFlour = item(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                flour, "1", Unit.KILOGRAM);
        InventoryItem milkStock = item(UUID.randomUUID(), milk, "2", Unit.LITER);
        InMemoryInventoryRepository inventory = new InMemoryInventoryRepository(
                List.of(firstFlour, secondFlour, milkStock));
        InMemoryConsumptionRepository ledger = new InMemoryConsumptionRepository(inventory);

        service(new InMemoryMealPlanRepository(List.of(entry)), inventory, ledger)
                .consumePastEntries();

        assertEquals(0, BigDecimal.ZERO.compareTo(
                inventory.findById(firstFlour.getId()).orElseThrow().getQuantity()));
        assertEquals(new BigDecimal("0.3"),
                inventory.findById(secondFlour.getId()).orElseThrow().getQuantity());
        assertEquals(new BigDecimal("0.5"),
                inventory.findById(milkStock.getId()).orElseThrow().getQuantity());
    }

    @Test
    void leavesIncompatibleUnitsUntouched() {
        Ingredient ingredient = new Ingredient("Knoblauch");
        Recipe recipe = recipe("Gericht", DishType.MAIN, 1,
                ingredient, "4", Unit.CLOVE);
        MealPlanEntry entry = new MealPlanEntry(TODAY.minusDays(1), recipe, 1);
        InventoryItem pieces = new InventoryItem(ingredient, new BigDecimal("10"), Unit.PIECE);
        InventoryItem sprigs = new InventoryItem(ingredient, new BigDecimal("3"), Unit.SPRIG);
        InMemoryInventoryRepository inventory = new InMemoryInventoryRepository(
                List.of(pieces, sprigs));
        InMemoryConsumptionRepository ledger = new InMemoryConsumptionRepository(inventory);

        service(new InMemoryMealPlanRepository(List.of(entry)), inventory, ledger)
                .consumePastEntries();

        assertEquals(new BigDecimal("10"),
                inventory.findById(pieces.getId()).orElseThrow().getQuantity());
        assertEquals(new BigDecimal("3"),
                inventory.findById(sprigs.getId()).orElseThrow().getQuantity());
    }

    @Test
    void processesMainSideAndDessertWithIndependentServingsOnSameDay() {
        Ingredient ingredient = new Ingredient("Zutat");
        Recipe main = recipe("Main", DishType.MAIN, 1, ingredient, "10", Unit.GRAM);
        Recipe side = recipe("Side", DishType.SIDE, 1, ingredient, "10", Unit.GRAM);
        Recipe dessert = recipe("Dessert", DishType.DESSERT, 1, ingredient, "10", Unit.GRAM);
        LocalDate yesterday = TODAY.minusDays(1);
        List<MealPlanEntry> entries = List.of(
                new MealPlanEntry(yesterday, main, 1),
                new MealPlanEntry(yesterday, side, 2, MealRole.SIDE, 0),
                new MealPlanEntry(yesterday, dessert, 3, MealRole.DESSERT, 0));
        InventoryItem stock = new InventoryItem(ingredient, new BigDecimal("100"), Unit.GRAM);
        InMemoryInventoryRepository inventory = new InMemoryInventoryRepository(List.of(stock));
        InMemoryConsumptionRepository ledger = new InMemoryConsumptionRepository(inventory);

        assertEquals(3, service(new InMemoryMealPlanRepository(entries), inventory, ledger)
                .consumePastEntries());

        assertEquals(new BigDecimal("40"),
                inventory.findById(stock.getId()).orElseThrow().getQuantity());
        assertEquals(List.of(new BigDecimal("10"), new BigDecimal("20"),
                        new BigDecimal("30")),
                ledger.findAll().stream().map(consumption ->
                        consumption.getItems().getFirst().quantity()).toList());
    }

    @Test
    void usesStoredAlternativePerEntryAndDefaultWithoutSelection() {
        Ingredient pasta = new Ingredient("Pasta");
        Ingredient rice = new Ingredient("Reis");
        RecipeIngredientOption pastaOption = new RecipeIngredientOption(
                pasta, new BigDecimal("500"), Unit.GRAM, 0);
        RecipeIngredientOption riceOption = new RecipeIngredientOption(
                rice, new BigDecimal("300"), Unit.GRAM, 1);
        RecipeIngredientGroup group = new RecipeIngredientGroup(
                List.of(pastaOption, riceOption), pastaOption);
        Recipe recipe = Recipe.withIngredientGroups("Flexibel", 1, List.of(group),
                List.of(), List.of(new Taste("Herzhaft")), DishType.MAIN);
        MealPlanEntry defaultEntry = new MealPlanEntry(TODAY.minusDays(2), recipe, 1);
        MealPlanEntry selectedEntry = new MealPlanEntry(UUID.randomUUID(), TODAY.minusDays(1),
                recipe, 2, MealRole.MAIN, 0, Map.of(group.getId(), riceOption.getId()));
        InventoryItem pastaStock = new InventoryItem(pasta, new BigDecimal("1000"), Unit.GRAM);
        InventoryItem riceStock = new InventoryItem(rice, new BigDecimal("1000"), Unit.GRAM);
        InMemoryInventoryRepository inventory = new InMemoryInventoryRepository(
                List.of(pastaStock, riceStock));
        InMemoryConsumptionRepository ledger = new InMemoryConsumptionRepository(inventory);

        service(new InMemoryMealPlanRepository(List.of(defaultEntry, selectedEntry)),
                inventory, ledger).consumePastEntries();

        assertEquals(new BigDecimal("500"),
                inventory.findById(pastaStock.getId()).orElseThrow().getQuantity());
        assertEquals(new BigDecimal("400"),
                inventory.findById(riceStock.getId()).orElseThrow().getQuantity());
        assertEquals(List.of(pasta.getId(), rice.getId()), ledger.findAll().stream()
                .map(record -> record.getItems().getFirst().ingredientId()).toList());
    }

    @Test
    void inventoryAwareShoppingReconcilesOnceAndSubtractsOnlyCurrentRemainder() {
        Ingredient ingredient = new Ingredient("Reis");
        Recipe recipe = recipe("Reis", DishType.MAIN, 1, ingredient, "100", Unit.GRAM);
        MealPlanEntry yesterday = new MealPlanEntry(TODAY.minusDays(1), recipe, 1);
        MealPlanEntry today = new MealPlanEntry(TODAY, recipe, 2);
        InMemoryMealPlanRepository plans = new InMemoryMealPlanRepository(
                List.of(yesterday, today));
        InventoryItem stock = new InventoryItem(
                ingredient, new BigDecimal("250"), Unit.GRAM);
        InMemoryInventoryRepository inventory = new InMemoryInventoryRepository(List.of(stock));
        InMemoryConsumptionRepository ledger = new InMemoryConsumptionRepository(inventory);
        InventoryConsumptionService consumption = service(plans, inventory, ledger);
        ShoppingListService shopping = new ShoppingListService(
                plans, inventory, new RecipeScaler(), new WeekService(), clock(), consumption);

        assertEquals(new BigDecimal("200"), shopping.buildForToday()
                .getItems().getFirst().getQuantity().getAmount());
        assertEquals(new BigDecimal("250"), inventory.findById(stock.getId())
                .orElseThrow().getQuantity());

        assertEquals(new BigDecimal("50"), shopping.buildForTodayWithInventory()
                .getItems().getFirst().getQuantity().getAmount());
        assertEquals(new BigDecimal("50"), shopping.buildForTodayWithInventory()
                .getItems().getFirst().getQuantity().getAmount());
        assertEquals(new BigDecimal("150"), inventory.findById(stock.getId())
                .orElseThrow().getQuantity());
        assertEquals(1, ledger.findAll().size());
    }

    @Test
    void processedSnapshotSurvivesSourceChangesAndDeletedPlanWhileUnprocessedDeletionConsumesNothing() {
        Ingredient ingredient = new Ingredient("Kartoffel");
        Recipe recipe = recipe("Alt", DishType.MAIN, 1, ingredient, "100", Unit.GRAM);
        MealPlanEntry processedEntry = new MealPlanEntry(TODAY.minusDays(2), recipe, 2);
        MealPlanEntry deletedBeforeProcessing = new MealPlanEntry(
                TODAY.minusDays(1), recipe, 3);
        InMemoryMealPlanRepository plans = new InMemoryMealPlanRepository(
                new ArrayList<>(List.of(processedEntry)));
        InventoryItem stock = new InventoryItem(ingredient, new BigDecimal("1000"), Unit.GRAM);
        InMemoryInventoryRepository inventory = new InMemoryInventoryRepository(List.of(stock));
        InMemoryConsumptionRepository ledger = new InMemoryConsumptionRepository(inventory);
        InventoryConsumptionService service = service(plans, inventory, ledger);

        service.consumePastEntries();
        InventoryConsumption snapshot = ledger.findAll().getFirst();
        plans.entries.clear();
        plans.entries.add(new MealPlanEntry(processedEntry.getId(), TODAY.minusDays(2),
                recipe("Geändert", DishType.MAIN, 1, ingredient, "999", Unit.GRAM),
                9, MealRole.MAIN, 0));
        service.consumePastEntries();
        plans.entries.clear();
        service.consumePastEntries();

        assertEquals(new BigDecimal("200"), snapshot.getItems().getFirst().quantity());
        assertEquals(snapshot.getId(), ledger.findAll().getFirst().getId());
        assertEquals(1, ledger.findAll().size());
        assertTrue(ledger.findAll().stream().noneMatch(record -> record.getMealPlanEntryId()
                .equals(deletedBeforeProcessing.getId())));
    }

    private static InventoryConsumptionService service(
            InMemoryMealPlanRepository plans, InMemoryInventoryRepository inventory,
            InMemoryConsumptionRepository ledger) {
        return new InventoryConsumptionService(
                plans, inventory, ledger, new RecipeScaler(), clock());
    }

    private static Clock clock() {
        ZoneId zone = ZoneId.of("Europe/Berlin");
        return Clock.fixed(TODAY.atStartOfDay(zone).plusHours(8).toInstant(), zone);
    }

    private static InventoryItem item(UUID id, Ingredient ingredient, String quantity, Unit unit) {
        return new InventoryItem(id, ingredient, new BigDecimal(quantity), unit);
    }

    private static Recipe recipe(String name, DishType type, int standardServings,
                                 Ingredient ingredient, String quantity, Unit unit) {
        return new Recipe(name, standardServings,
                List.of(new RecipeIngredient(ingredient, new BigDecimal(quantity), unit)),
                List.of(), List.of(new Taste("Herzhaft")), type);
    }

    private static final class InMemoryMealPlanRepository implements MealPlanRepository {
        private final List<MealPlanEntry> entries;
        private InMemoryMealPlanRepository(List<MealPlanEntry> entries) {
            this.entries = new ArrayList<>(entries);
        }
        @Override public void save(MealPlanEntry entry) { entries.add(entry); }
        @Override public void applyChanges(List<MealPlanEntry> save, List<UUID> delete) {
            throw new UnsupportedOperationException();
        }
        @Override public Optional<MealPlanEntry> findById(UUID id) {
            return entries.stream().filter(entry -> entry.getId().equals(id)).findFirst();
        }
        @Override public Optional<MealPlanEntry> findByDate(LocalDate date) {
            return entries.stream().filter(entry -> entry.getDate().equals(date)).findFirst();
        }
        @Override public List<MealPlanEntry> findBetween(LocalDate start, LocalDate end) {
            return entries.stream().filter(entry -> !entry.getDate().isBefore(start))
                    .filter(entry -> !entry.getDate().isAfter(end))
                    .sorted(java.util.Comparator.comparing(MealPlanEntry::getDate)
                            .thenComparing(MealPlanEntry::getPosition)).toList();
        }
        @Override public List<MealPlanEntry> findBefore(LocalDate cutoff) {
            return entries.stream().filter(entry -> entry.getDate().isBefore(cutoff))
                    .sorted(java.util.Comparator.comparing(MealPlanEntry::getDate)
                            .thenComparing(entry -> entry.getMealRole().ordinal())
                            .thenComparing(MealPlanEntry::getPosition)).toList();
        }
        @Override public boolean deleteById(UUID id) {
            return entries.removeIf(entry -> entry.getId().equals(id));
        }
        @Override public int deleteBefore(LocalDate cutoff) { return 0; }
    }

    private static final class InMemoryInventoryRepository implements InventoryRepository {
        private final List<InventoryItem> entries;
        private InMemoryInventoryRepository(List<InventoryItem> entries) {
            this.entries = new ArrayList<>(entries);
            this.entries.sort(java.util.Comparator.comparing(InventoryItem::getId));
        }
        @Override public void save(InventoryItem item) {
            entries.removeIf(existing -> existing.getId().equals(item.getId()));
            entries.add(item);
            entries.sort(java.util.Comparator.comparing(InventoryItem::getId));
        }
        @Override public Optional<InventoryItem> findById(UUID id) {
            return entries.stream().filter(item -> item.getId().equals(id)).findFirst();
        }
        @Override public List<InventoryItem> findAll() { return List.copyOf(entries); }
        @Override public boolean deleteById(UUID id) {
            return entries.removeIf(item -> item.getId().equals(id));
        }
    }

    private static final class InMemoryConsumptionRepository
            implements InventoryConsumptionRepository {
        private final List<InventoryConsumption> entries = new ArrayList<>();
        private final InMemoryInventoryRepository inventory;
        private InMemoryConsumptionRepository(InMemoryInventoryRepository inventory) {
            this.inventory = inventory;
        }
        @Override public boolean existsByMealPlanEntryId(UUID entryId) {
            return entries.stream().anyMatch(record -> record.getMealPlanEntryId().equals(entryId));
        }
        @Override public List<InventoryConsumption> findAll() { return List.copyOf(entries); }
        @Override public void saveWithInventoryUpdates(
                InventoryConsumption consumption, List<InventoryItem> updates) {
            if (existsByMealPlanEntryId(consumption.getMealPlanEntryId())) {
                throw new IllegalStateException("Duplicate consumption.");
            }
            updates.forEach(inventory::save);
            entries.add(consumption);
        }
    }
}
