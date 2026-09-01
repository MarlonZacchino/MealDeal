package de.mealdeal.service;

import de.mealdeal.domain.DishType;
import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.InventoryItem;
import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.MealRole;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.RecipeIngredientGroup;
import de.mealdeal.domain.RecipeIngredientOption;
import de.mealdeal.domain.ShoppingList;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.repository.InventoryRepository;
import de.mealdeal.persistence.repository.MealPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShoppingListInventoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);
    private final Ingredient ingredient = new Ingredient("Zutat");

    @Test
    void noInventoryProducesExactlyTheExistingResult() {
        Recipe recipe = recipe("Gericht", DishType.MAIN, ingredient, "1000", Unit.GRAM);
        MealPlanEntry entry = new MealPlanEntry(TODAY, recipe, 1);
        TrackingInventoryRepository inventory = new TrackingInventoryRepository(List.of());
        ShoppingListService service = service(List.of(entry), inventory);

        ShoppingList without = service.buildForToday();
        ShoppingList with = service.buildForTodayWithInventory();

        assertEquals(without.getItems().getFirst().getIngredient(),
                with.getItems().getFirst().getIngredient());
        assertEquals(without.getItems().getFirst().getQuantity().getAmount(),
                with.getItems().getFirst().getQuantity().getAmount());
        assertEquals(without.getItems().getFirst().getQuantity().getUnit(),
                with.getItems().getFirst().getQuantity().getUnit());
    }

    @ParameterizedTest
    @MethodSource("compatibleInventoryCases")
    void subtractsCompatibleInventoryUsingUnitConverter(
            String requiredAmount, Unit requiredUnit, String stockAmount, Unit stockUnit,
            String expectedAmount) {
        ShoppingList required = required(requiredAmount, requiredUnit);
        TrackingInventoryRepository inventory = inventory(stockAmount, stockUnit);

        ShoppingList remaining = service(List.of(), inventory).subtractInventory(required);

        if (expectedAmount == null) {
            assertTrue(remaining.isEmpty());
        } else {
            assertEquals(new BigDecimal(expectedAmount),
                    remaining.getItems().getFirst().getQuantity().getAmount());
            assertEquals(requiredUnit, remaining.getItems().getFirst().getQuantity().getUnit());
        }
    }

    static Stream<Arguments> compatibleInventoryCases() {
        return Stream.of(
                Arguments.of("1000", Unit.GRAM, "400", Unit.GRAM, "600"),
                Arguments.of("1", Unit.KILOGRAM, "500", Unit.GRAM, "0.5"),
                Arguments.of("1000", Unit.GRAM, "0.4", Unit.KILOGRAM, "600.0"),
                Arguments.of("1000", Unit.MILLILITER, "0.25", Unit.LITER, "750.00"),
                Arguments.of("2", Unit.LITER, "500", Unit.MILLILITER, "1.5"),
                Arguments.of("6", Unit.PIECE, "6", Unit.PIECE, null),
                Arguments.of("6", Unit.PIECE, "10", Unit.PIECE, null),
                Arguments.of("6", Unit.SLICE, "2", Unit.SLICE, "4"),
                Arguments.of("4", Unit.CLOVE, "2", Unit.CLOVE, "2"),
                Arguments.of("3", Unit.SPRIG, "1", Unit.SPRIG, "2")
        );
    }

    @ParameterizedTest
    @MethodSource("incompatibleInventoryCases")
    void doesNotSubtractIncompatibleUnits(Unit requiredUnit, Unit stockUnit) {
        ShoppingList remaining = service(List.of(), inventory("4", stockUnit))
                .subtractInventory(required("4", requiredUnit));

        assertEquals(new BigDecimal("4"),
                remaining.getItems().getFirst().getQuantity().getAmount());
        assertEquals(requiredUnit, remaining.getItems().getFirst().getQuantity().getUnit());
    }

    static Stream<Arguments> incompatibleInventoryCases() {
        return Stream.of(
                Arguments.of(Unit.PIECE, Unit.SLICE),
                Arguments.of(Unit.PIECE, Unit.CLOVE),
                Arguments.of(Unit.CLOVE, Unit.SPRIG),
                Arguments.of(Unit.TABLESPOON, Unit.MILLILITER),
                Arguments.of(Unit.TEASPOON, Unit.TABLESPOON)
        );
    }

    @Test
    void combinesMultipleCompatibleInventoryItemsOnce() {
        TrackingInventoryRepository inventory = new TrackingInventoryRepository(List.of(
                new InventoryItem(ingredient, new BigDecimal("200"), Unit.GRAM),
                new InventoryItem(ingredient, new BigDecimal("0.3"), Unit.KILOGRAM)));

        ShoppingList remaining = service(List.of(), inventory)
                .subtractInventory(required("1000", Unit.GRAM));

        assertEquals(new BigDecimal("500.0"),
                remaining.getItems().getFirst().getQuantity().getAmount());
    }

    @Test
    void appliesInventoryAfterMainSideAndDessertAggregation() {
        Recipe main = recipe("Main", DishType.MAIN, ingredient, "100", Unit.GRAM);
        Recipe side = recipe("Side", DishType.SIDE, ingredient, "100", Unit.GRAM);
        Recipe dessert = recipe("Dessert", DishType.DESSERT, ingredient, "100", Unit.GRAM);
        List<MealPlanEntry> entries = List.of(
                new MealPlanEntry(TODAY, main, 1),
                new MealPlanEntry(TODAY, side, 2, MealRole.SIDE, 0),
                new MealPlanEntry(TODAY, dessert, 3, MealRole.DESSERT, 0));

        ShoppingList remaining = service(entries, inventory("100", Unit.GRAM))
                .buildForTodayWithInventory();

        assertEquals(new BigDecimal("500"),
                remaining.getItems().getFirst().getQuantity().getAmount());
    }

    @Test
    void subtractsStockFromSelectedAlternativeAndUsesDefaultWithoutSelection() {
        Ingredient pasta = new Ingredient("Pasta");
        Ingredient rice = new Ingredient("Reis");
        RecipeIngredientOption pastaOption = new RecipeIngredientOption(
                pasta, new BigDecimal("500"), Unit.GRAM, 0);
        RecipeIngredientOption riceOption = new RecipeIngredientOption(
                rice, new BigDecimal("300"), Unit.GRAM, 1);
        RecipeIngredientGroup group = new RecipeIngredientGroup(
                List.of(pastaOption, riceOption), pastaOption);
        Recipe flexible = Recipe.withIngredientGroups("Flexibel", 1, List.of(group),
                List.of(), List.of(new Taste("Herzhaft")), DishType.MAIN);
        MealPlanEntry defaultEntry = new MealPlanEntry(TODAY, flexible, 1);
        MealPlanEntry selectedEntry = new MealPlanEntry(UUID.randomUUID(), TODAY.plusDays(1),
                flexible, 1, MealRole.MAIN, 0, Map.of(group.getId(), riceOption.getId()));
        TrackingInventoryRepository inventory = new TrackingInventoryRepository(List.of(
                new InventoryItem(pasta, new BigDecimal("200"), Unit.GRAM),
                new InventoryItem(rice, new BigDecimal("100"), Unit.GRAM)));

        ShoppingList defaultRemaining = service(List.of(), inventory).subtractInventory(
                service(List.of(), inventory).buildFromEntries(List.of(defaultEntry)));
        ShoppingList selectedRemaining = service(List.of(), inventory).subtractInventory(
                service(List.of(), inventory).buildFromEntries(List.of(selectedEntry)));

        assertEquals(pasta.getId(), defaultRemaining.getItems().getFirst()
                .getIngredient().getId());
        assertEquals(new BigDecimal("300"), defaultRemaining.getItems().getFirst()
                .getQuantity().getAmount());
        assertEquals(rice.getId(), selectedRemaining.getItems().getFirst()
                .getIngredient().getId());
        assertEquals(new BigDecimal("200"), selectedRemaining.getItems().getFirst()
                .getQuantity().getAmount());
    }

    @Test
    void todayAndWeekKeepTheirRangesAndNeverMutateInventory() {
        Recipe recipe = recipe("Gericht", DishType.MAIN, ingredient, "100", Unit.GRAM);
        TrackingMealPlanRepository plans = new TrackingMealPlanRepository(List.of(
                new MealPlanEntry(TODAY.minusDays(1), recipe, 1),
                new MealPlanEntry(TODAY, recipe, 1),
                new MealPlanEntry(TODAY.plusDays(1), recipe, 1)));
        TrackingInventoryRepository inventory = inventory("50", Unit.GRAM);
        ShoppingListService service = new ShoppingListService(plans, inventory,
                new RecipeScaler(), new WeekService(), clock());

        assertEquals(new BigDecimal("50"), service.buildForTodayWithInventory()
                .getItems().getFirst().getQuantity().getAmount());
        assertEquals(TODAY, plans.lastStart);
        assertEquals(TODAY, plans.lastEnd);
        assertEquals(new BigDecimal("150"), service.buildForCurrentWeekWithInventory()
                .getItems().getFirst().getQuantity().getAmount());
        assertEquals(TODAY, plans.lastStart);
        assertEquals(LocalDate.of(2026, 9, 6), plans.lastEnd);
        assertEquals(0, inventory.saveCalls);
        assertEquals(0, inventory.deleteCalls);
        assertEquals(new BigDecimal("50"), inventory.findAll().getFirst().getQuantity());
    }

    private ShoppingList required(String amount, Unit unit) {
        return new ShoppingList(List.of(new de.mealdeal.domain.ShoppingListItem(
                ingredient, new de.mealdeal.domain.Quantity(new BigDecimal(amount), unit))));
    }

    private TrackingInventoryRepository inventory(String amount, Unit unit) {
        return new TrackingInventoryRepository(List.of(
                new InventoryItem(ingredient, new BigDecimal(amount), unit)));
    }

    private ShoppingListService service(List<MealPlanEntry> entries,
                                        TrackingInventoryRepository inventory) {
        return new ShoppingListService(new TrackingMealPlanRepository(entries), inventory,
                new RecipeScaler(), new WeekService(), clock());
    }

    private static Clock clock() {
        ZoneId zone = ZoneId.of("Europe/Berlin");
        return Clock.fixed(TODAY.atStartOfDay(zone).toInstant(), zone);
    }

    private static Recipe recipe(String name, DishType dishType, Ingredient ingredient,
                                 String amount, Unit unit) {
        return new Recipe(name, 1,
                List.of(new RecipeIngredient(ingredient, new BigDecimal(amount), unit)),
                List.of(), List.of(new Taste("Herzhaft")), dishType);
    }

    private static final class TrackingInventoryRepository implements InventoryRepository {
        private final List<InventoryItem> entries;
        private int saveCalls;
        private int deleteCalls;

        private TrackingInventoryRepository(List<InventoryItem> entries) {
            this.entries = new ArrayList<>(entries);
        }
        @Override public void save(InventoryItem item) { saveCalls++; }
        @Override public Optional<InventoryItem> findById(UUID id) {
            return entries.stream().filter(item -> item.getId().equals(id)).findFirst();
        }
        @Override public List<InventoryItem> findAll() { return List.copyOf(entries); }
        @Override public boolean deleteById(UUID id) { deleteCalls++; return false; }
    }

    private static final class TrackingMealPlanRepository implements MealPlanRepository {
        private final List<MealPlanEntry> entries;
        private LocalDate lastStart;
        private LocalDate lastEnd;

        private TrackingMealPlanRepository(List<MealPlanEntry> entries) {
            this.entries = new ArrayList<>(entries);
        }
        @Override public void save(MealPlanEntry entry) { throw new UnsupportedOperationException(); }
        @Override public void applyChanges(List<MealPlanEntry> entriesToSave,
                                           List<UUID> entryIdsToDelete) {
            throw new UnsupportedOperationException();
        }
        @Override public Optional<MealPlanEntry> findById(UUID id) { return Optional.empty(); }
        @Override public Optional<MealPlanEntry> findByDate(LocalDate date) {
            return Optional.empty();
        }
        @Override public List<MealPlanEntry> findBetween(LocalDate start, LocalDate end) {
            lastStart = start;
            lastEnd = end;
            return entries.stream().filter(entry -> !entry.getDate().isBefore(start))
                    .filter(entry -> !entry.getDate().isAfter(end)).toList();
        }
        @Override public boolean deleteById(UUID id) { return false; }
        @Override public int deleteBefore(LocalDate cutoffExclusive) { return 0; }
    }
}
