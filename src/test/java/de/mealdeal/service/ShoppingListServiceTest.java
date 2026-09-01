package de.mealdeal.service;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.DishType;
import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.MealRole;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.RecipeIngredientGroup;
import de.mealdeal.domain.RecipeIngredientOption;
import de.mealdeal.domain.ShoppingList;
import de.mealdeal.domain.ShoppingListItem;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.repository.MealPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShoppingListServiceTest {

    private Ingredient pasta;
    private Ingredient sauce;
    private Ingredient onion;

    @BeforeEach
    void setUp() {
        pasta = new Ingredient("Pasta");
        sauce = new Ingredient("Sauce");
        onion = new Ingredient("Onion");
    }

    @Test
    void returnsEmptyListWhenNothingIsPlannedToday() {
        var repository = new InMemoryMealPlanRepository(List.of());

        ShoppingList shoppingList = service(repository, LocalDate.of(2026, 9, 1))
                .buildForToday();

        assertTrue(shoppingList.isEmpty());
        assertThrows(UnsupportedOperationException.class, shoppingList.getItems()::clear);
    }

    @Test
    void buildsTodayListAndScalesForPlannedServingCount() {
        Recipe recipe = recipe("Pasta", 2,
                amount(pasta, "500", Unit.GRAM), amount(sauce, "300", Unit.MILLILITER));
        LocalDate today = LocalDate.of(2026, 9, 1);
        var repository = new InMemoryMealPlanRepository(
                List.of(new MealPlanEntry(today, recipe, 4)));

        ShoppingList shoppingList = service(repository, today).buildForToday();

        assertItem(shoppingList.getItems().get(0), pasta, "1000", Unit.GRAM);
        assertItem(shoppingList.getItems().get(1), sauce, "600", Unit.MILLILITER);
    }

    @Test
    void shoppingListUsesOnlyTheStandardOptionOfAnAlternativeGroup() {
        RecipeIngredientOption pastaOption = new RecipeIngredientOption(
                pasta, new BigDecimal("500"), Unit.GRAM, 0);
        RecipeIngredientOption sauceOption = new RecipeIngredientOption(
                sauce, new BigDecimal("300"), Unit.MILLILITER, 1);
        RecipeIngredientGroup group = new RecipeIngredientGroup(
                List.of(pastaOption, sauceOption), sauceOption);
        Recipe recipe = Recipe.withIngredientGroups("Flexible", 2, List.of(group),
                List.of(), List.of(new Taste("Savory")), DishType.MAIN);
        LocalDate today = LocalDate.of(2026, 9, 1);

        ShoppingList shoppingList = service(new InMemoryMealPlanRepository(List.of()), today)
                .buildFromEntries(List.of(new MealPlanEntry(today, recipe, 4)));

        assertEquals(1, shoppingList.getItems().size());
        assertItem(shoppingList.getItems().getFirst(), sauce, "600", Unit.MILLILITER);
    }

    @Test
    void shoppingListUsesTheOptionSelectedForEachMealPlanEntry() {
        RecipeIngredientOption pastaOption = new RecipeIngredientOption(
                pasta, new BigDecimal("500"), Unit.GRAM, 0);
        RecipeIngredientOption sauceOption = new RecipeIngredientOption(
                sauce, new BigDecimal("300"), Unit.MILLILITER, 1);
        RecipeIngredientGroup group = new RecipeIngredientGroup(
                List.of(pastaOption, sauceOption), pastaOption);
        Recipe recipe = Recipe.withIngredientGroups("Flexible", 2, List.of(group),
                List.of(), List.of(new Taste("Savory")), DishType.MAIN);
        LocalDate today = LocalDate.of(2026, 9, 1);
        MealPlanEntry selected = new MealPlanEntry(UUID.randomUUID(), today, recipe, 4,
                MealRole.MAIN, 0, Map.of(group.getId(), sauceOption.getId()));

        ShoppingList shoppingList = service(new InMemoryMealPlanRepository(List.of()), today)
                .buildFromEntries(List.of(selected));

        assertEquals(1, shoppingList.getItems().size());
        assertItem(shoppingList.getItems().getFirst(), sauce, "600", Unit.MILLILITER);
    }

    @Test
    void todayIncludesMainAndAllSideDishesWithTheirIndividualServingCounts() {
        LocalDate today = LocalDate.of(2026, 9, 1);
        Recipe main = recipe("Main", 2, DishType.MAIN, amount(pasta, "500", Unit.GRAM));
        Recipe firstSide = recipe("First side", 4, DishType.SIDE, amount(pasta, "100", Unit.GRAM));
        Recipe secondSide = recipe("Second side", 1, DishType.SIDE, amount(sauce, "50", Unit.MILLILITER));
        var repository = new InMemoryMealPlanRepository(List.of(
                new MealPlanEntry(today, main, 4),
                sideEntry(today, firstSide, 2, 0),
                sideEntry(today, secondSide, 3, 1)));

        ShoppingList shoppingList = service(repository, today).buildForToday();

        assertEquals(2, shoppingList.getItems().size());
        assertItem(shoppingList.getItems().get(0), pasta, "1050", Unit.GRAM);
        assertItem(shoppingList.getItems().get(1), sauce, "150", Unit.MILLILITER);
        assertEquals(today, repository.lastRangeStart);
        assertEquals(today, repository.lastRangeEnd);
    }

    @Test
    void combinesSameIngredientAndDifferentServingCounts() {
        Recipe first = recipe("First", 2, amount(pasta, "500", Unit.GRAM));
        Recipe second = recipe("Second", 2, amount(pasta, "300", Unit.GRAM));
        List<MealPlanEntry> entries = List.of(
                new MealPlanEntry(LocalDate.of(2026, 9, 1), first, 4),
                new MealPlanEntry(LocalDate.of(2026, 9, 2), second, 1));

        ShoppingList list = service(new InMemoryMealPlanRepository(List.of()),
                LocalDate.of(2026, 9, 1)).buildFromEntries(entries);

        assertItem(list.getItems().getFirst(), pasta, "1150", Unit.GRAM);
    }

    @ParameterizedTest
    @MethodSource("compatibleUnitAdditions")
    void combinesCompatibleUnitsAndKeepsFirstUnit(
            String firstAmount, Unit firstUnit, String secondAmount, Unit secondUnit,
            String expectedAmount) {
        Recipe first = recipe("First", 1, amount(pasta, firstAmount, firstUnit));
        Recipe second = recipe("Second", 1, amount(pasta, secondAmount, secondUnit));

        ShoppingList list = build(List.of(
                entry(LocalDate.of(2026, 9, 1), first),
                entry(LocalDate.of(2026, 9, 2), second)));

        assertItem(list.getItems().getFirst(), pasta, expectedAmount, firstUnit);
    }

    static Stream<Arguments> compatibleUnitAdditions() {
        return Stream.of(
                Arguments.of("500", Unit.GRAM, "1", Unit.KILOGRAM, "1500"),
                Arguments.of("1", Unit.KILOGRAM, "500", Unit.GRAM, "1.5"),
                Arguments.of("500", Unit.MILLILITER, "1", Unit.LITER, "1500"),
                Arguments.of("2", Unit.PIECE, "3", Unit.PIECE, "5"),
                Arguments.of("2", Unit.SLICE, "3", Unit.SLICE, "5"),
                Arguments.of("2", Unit.CLOVE, "3", Unit.CLOVE, "5"),
                Arguments.of("2", Unit.SPRIG, "3", Unit.SPRIG, "5"),
                Arguments.of("1", Unit.TABLESPOON, "2", Unit.TABLESPOON, "3")
        );
    }

    @ParameterizedTest
    @MethodSource("incompatibleUnitPairs")
    void keepsIncompatibleUnitsOfSameIngredientAsSeparateItems(Unit first, Unit second) {
        Recipe firstRecipe = recipe("First", 1, amount(onion, "1", first));
        Recipe secondRecipe = recipe("Second", 1, amount(onion, "100", second));

        ShoppingList list = build(List.of(
                entry(LocalDate.of(2026, 9, 1), firstRecipe),
                entry(LocalDate.of(2026, 9, 2), secondRecipe)));

        assertEquals(2, list.getItems().size());
        assertEquals(List.of(first, second).stream().map(Unit::name).sorted().toList(),
                list.getItems().stream().map(item -> item.getQuantity().getUnit().name()).toList());
    }

    static Stream<Arguments> incompatibleUnitPairs() {
        return Stream.of(
                Arguments.of(Unit.PIECE, Unit.GRAM),
                Arguments.of(Unit.SLICE, Unit.PIECE),
                Arguments.of(Unit.CLOVE, Unit.PIECE),
                Arguments.of(Unit.CLOVE, Unit.SLICE),
                Arguments.of(Unit.SPRIG, Unit.PIECE),
                Arguments.of(Unit.SPRIG, Unit.SLICE),
                Arguments.of(Unit.CLOVE, Unit.SPRIG),
                Arguments.of(Unit.TABLESPOON, Unit.MILLILITER),
                Arguments.of(Unit.TABLESPOON, Unit.TEASPOON)
        );
    }

    @Test
    void keepsDifferentIngredientIdentitiesSeparateEvenWithSameName() {
        Ingredient otherPasta = new Ingredient("Pasta");
        Recipe first = recipe("First", 1, amount(pasta, "500", Unit.GRAM));
        Recipe second = recipe("Second", 1, amount(otherPasta, "300", Unit.GRAM));

        ShoppingList list = build(List.of(
                entry(LocalDate.of(2026, 9, 1), first),
                entry(LocalDate.of(2026, 9, 2), second)));

        assertEquals(2, list.getItems().size());
    }

    @Test
    void aggregatesRealisticListAndSortsByIngredientName() {
        Ingredient cheese = new Ingredient("Cheese");
        Recipe first = recipe("First", 1,
                amount(pasta, "500", Unit.GRAM), amount(sauce, "200", Unit.MILLILITER),
                amount(onion, "1", Unit.PIECE));
        Recipe second = recipe("Second", 1,
                amount(pasta, "300", Unit.GRAM), amount(sauce, "100", Unit.MILLILITER),
                amount(onion, "2", Unit.PIECE), amount(cheese, "50", Unit.GRAM));

        ShoppingList list = build(List.of(
                entry(LocalDate.of(2026, 9, 1), first),
                entry(LocalDate.of(2026, 9, 2), second)));

        assertEquals(List.of("Cheese", "Onion", "Pasta", "Sauce"), list.getItems().stream()
                .map(item -> item.getIngredient().getName()).toList());
        assertEquals(List.of("50", "3", "800", "300"), list.getItems().stream()
                .map(item -> item.getQuantity().getAmount().toPlainString()).toList());
    }

    @Test
    void currentWeekLoadsOnlyTodayThroughSunday() {
        LocalDate today = LocalDate.of(2026, 8, 26);
        Recipe recipe = recipe("Pasta", 1, amount(pasta, "100", Unit.GRAM));
        var repository = new InMemoryMealPlanRepository(List.of(
                entry(LocalDate.of(2026, 8, 24), recipe),
                entry(LocalDate.of(2026, 8, 25), recipe),
                entry(today, recipe),
                entry(LocalDate.of(2026, 8, 27), recipe)));

        ShoppingList list = service(repository, today).buildForCurrentWeek();

        assertItem(list.getItems().getFirst(), pasta, "200", Unit.GRAM);
        assertEquals(today, repository.lastRangeStart);
        assertEquals(LocalDate.of(2026, 8, 30), repository.lastRangeEnd);
    }

    @Test
    void currentWeekIncludesSideOnlyDaysAndExcludesPastMainAndSideEntries() {
        LocalDate today = LocalDate.of(2026, 8, 26);
        Recipe main = recipe("Main", 1, DishType.MAIN, amount(pasta, "100", Unit.GRAM));
        Recipe side = recipe("Side", 1, DishType.SIDE, amount(pasta, "25", Unit.GRAM));
        var repository = new InMemoryMealPlanRepository(List.of(
                new MealPlanEntry(today.minusDays(1), main, 1),
                sideEntry(today.minusDays(1), side, 1, 0),
                new MealPlanEntry(today, main, 1),
                sideEntry(today.plusDays(1), side, 2, 0),
                sideEntry(today.plusDays(2), side, 3, 0)));

        ShoppingList list = service(repository, today).buildForCurrentWeek();

        assertItem(list.getItems().getFirst(), pasta, "225", Unit.GRAM);
        assertEquals(today, repository.lastRangeStart);
        assertEquals(LocalDate.of(2026, 8, 30), repository.lastRangeEnd);
    }

    @Test
    void keepsIncompatibleUnitsSeparateAcrossMainAndSideDishes() {
        LocalDate today = LocalDate.of(2026, 9, 1);
        Recipe main = recipe("Main", 1, DishType.MAIN, amount(onion, "2", Unit.PIECE));
        Recipe side = recipe("Side", 1, DishType.SIDE, amount(onion, "3", Unit.SLICE));

        ShoppingList list = service(new InMemoryMealPlanRepository(List.of(
                new MealPlanEntry(today, main, 1), sideEntry(today, side, 1, 0))), today)
                .buildForToday();

        assertEquals(2, list.getItems().size());
        assertEquals(List.of(Unit.PIECE, Unit.SLICE), list.getItems().stream()
                .map(item -> item.getQuantity().getUnit()).sorted().toList());
    }

    @ParameterizedTest
    @MethodSource("calendarBoundaries")
    void handlesMonthYearAndLeapYearWeekBoundaries(LocalDate today, LocalDate sunday) {
        Recipe recipe = recipe("Pasta", 1, amount(pasta, "100", Unit.GRAM));
        var repository = new InMemoryMealPlanRepository(List.of(
                entry(today.minusDays(1), recipe), entry(today, recipe), entry(sunday, recipe)));

        ShoppingList list = service(repository, today).buildForCurrentWeek();

        assertItem(list.getItems().getFirst(), pasta, "200", Unit.GRAM);
        assertEquals(today, repository.lastRangeStart);
        assertEquals(sunday, repository.lastRangeEnd);
    }

    static Stream<Arguments> calendarBoundaries() {
        return Stream.of(
                Arguments.of(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 4)),
                Arguments.of(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 3)),
                Arguments.of(LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 5))
        );
    }

    @Test
    void validatesConstructorAndAggregationArguments() {
        Clock clock = clock(LocalDate.of(2026, 9, 1));
        var repository = new InMemoryMealPlanRepository(List.of());

        assertThrows(NullPointerException.class,
                () -> new ShoppingListService(null, new RecipeScaler(), new WeekService(), clock));
        assertThrows(NullPointerException.class,
                () -> new ShoppingListService(repository, null, new WeekService(), clock));
        assertThrows(NullPointerException.class,
                () -> new ShoppingListService(repository, new RecipeScaler(), null, clock));
        assertThrows(NullPointerException.class,
                () -> new ShoppingListService(repository, new RecipeScaler(), new WeekService(), null));
        assertThrows(NullPointerException.class,
                () -> service(repository, LocalDate.of(2026, 9, 1)).buildFromEntries(null));
    }

    private ShoppingList build(List<MealPlanEntry> entries) {
        return service(new InMemoryMealPlanRepository(List.of()), LocalDate.of(2026, 9, 1))
                .buildFromEntries(entries);
    }

    private ShoppingListService service(InMemoryMealPlanRepository repository, LocalDate today) {
        return new ShoppingListService(repository, new RecipeScaler(), new WeekService(), clock(today));
    }

    private static Clock clock(LocalDate date) {
        return Clock.fixed(date.atStartOfDay(ZoneId.of("Europe/Berlin")).toInstant(),
                ZoneId.of("Europe/Berlin"));
    }

    private static MealPlanEntry entry(LocalDate date, Recipe recipe) {
        return new MealPlanEntry(date, recipe, 1);
    }

    private static MealPlanEntry sideEntry(LocalDate date, Recipe recipe, int servingCount,
                                           int position) {
        return new MealPlanEntry(date, recipe, servingCount, MealRole.SIDE, position);
    }

    private static Recipe recipe(String name, int standardServings, IngredientAmount... amounts) {
        return recipe(name, standardServings, DishType.MAIN, amounts);
    }

    private static Recipe recipe(String name, int standardServings, DishType dishType,
                                 IngredientAmount... amounts) {
        List<RecipeIngredient> ingredients = Stream.of(amounts)
                .map(amount -> new RecipeIngredient(amount.ingredient(), amount.amount(), amount.unit()))
                .toList();
        return new Recipe(name, standardServings, ingredients, List.of(),
                List.of(new Taste("Savory")), dishType);
    }

    private static IngredientAmount amount(Ingredient ingredient, String amount, Unit unit) {
        return new IngredientAmount(ingredient, new BigDecimal(amount), unit);
    }

    private static void assertItem(ShoppingListItem item, Ingredient ingredient,
                                   String amount, Unit unit) {
        assertEquals(ingredient, item.getIngredient());
        assertEquals(new BigDecimal(amount), item.getQuantity().getAmount());
        assertEquals(unit, item.getQuantity().getUnit());
    }

    private record IngredientAmount(Ingredient ingredient, BigDecimal amount, Unit unit) {
    }

    private static final class InMemoryMealPlanRepository implements MealPlanRepository {
        private final List<MealPlanEntry> entries;
        private LocalDate lastRangeStart;
        private LocalDate lastRangeEnd;

        private InMemoryMealPlanRepository(List<MealPlanEntry> entries) {
            this.entries = new ArrayList<>(entries);
        }

        @Override
        public void save(MealPlanEntry entry) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void applyChanges(List<MealPlanEntry> entriesToSave, List<UUID> entryIdsToDelete) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<MealPlanEntry> findById(UUID id) {
            return entries.stream().filter(entry -> entry.getId().equals(id)).findFirst();
        }

        @Override
        public Optional<MealPlanEntry> findByDate(LocalDate date) {
            return entries.stream().filter(entry -> entry.getDate().equals(date)).findFirst();
        }

        @Override
        public List<MealPlanEntry> findBetween(LocalDate startInclusive, LocalDate endInclusive) {
            lastRangeStart = startInclusive;
            lastRangeEnd = endInclusive;
            return entries.stream()
                    .filter(entry -> !entry.getDate().isBefore(startInclusive))
                    .filter(entry -> !entry.getDate().isAfter(endInclusive))
                    .sorted(java.util.Comparator.comparing(MealPlanEntry::getDate))
                    .toList();
        }

        @Override
        public boolean deleteById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteBefore(LocalDate cutoffExclusive) {
            throw new UnsupportedOperationException();
        }
    }
}
