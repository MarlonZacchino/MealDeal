package de.mealdeal.service;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.Quantity;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.ShoppingList;
import de.mealdeal.domain.ShoppingListItem;
import de.mealdeal.domain.Unit;
import de.mealdeal.domain.UnitConverter;
import de.mealdeal.domain.InventoryItem;
import de.mealdeal.persistence.repository.InventoryRepository;
import de.mealdeal.persistence.repository.MealPlanRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Derives today's or the remaining week's shopping list from persisted plans.
 *
 * <p>Scaling is delegated to {@link RecipeScaler}, and quantity addition is
 * delegated to {@link Quantity#add(Quantity)}. Ingredient UUIDs define equal
 * ingredients; names are used only for deterministic result ordering.</p>
 */
public final class ShoppingListService {

    private static final Comparator<ShoppingListItem> ITEM_ORDER =
            Comparator.comparing((ShoppingListItem item) -> item.getIngredient().getName(),
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(item -> item.getIngredient().getName())
                    .thenComparing(item -> item.getIngredient().getId())
                    .thenComparing(item -> item.getQuantity().getUnit().name());

    private final MealPlanRepository repository;
    private final InventoryRepository inventoryRepository;
    private final RecipeScaler recipeScaler;
    private final WeekService weekService;
    private final Clock clock;

    /** Creates production calculation using the local system time zone. */
    public ShoppingListService(MealPlanRepository repository) {
        this(repository, emptyInventoryRepository(), new RecipeScaler(), new WeekService(),
                Clock.systemDefaultZone());
    }

    /** Creates production calculation with optional read-only inventory subtraction. */
    public ShoppingListService(MealPlanRepository repository,
                               InventoryRepository inventoryRepository) {
        this(repository, inventoryRepository, new RecipeScaler(), new WeekService(),
                Clock.systemDefaultZone());
    }

    /** Creates deterministic calculation with explicit collaborators. */
    public ShoppingListService(MealPlanRepository repository, RecipeScaler recipeScaler,
                               WeekService weekService, Clock clock) {
        this(repository, emptyInventoryRepository(), recipeScaler, weekService, clock);
    }

    /** Creates deterministic calculation with explicit inventory and collaborators. */
    public ShoppingListService(MealPlanRepository repository,
                               InventoryRepository inventoryRepository,
                               RecipeScaler recipeScaler, WeekService weekService, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "Meal plan repository must not be null.");
        this.inventoryRepository = Objects.requireNonNull(
                inventoryRepository, "Inventory repository must not be null.");
        this.recipeScaler = Objects.requireNonNull(recipeScaler, "Recipe scaler must not be null.");
        this.weekService = Objects.requireNonNull(weekService, "Week service must not be null.");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null.");
    }

    /** Builds a list from every plan entry for the local current date. */
    public ShoppingList buildForToday() {
        LocalDate today = LocalDate.now(clock);
        return buildFromEntries(repository.findBetween(today, today));
    }

    /** Builds today's list and subtracts compatible inventory without modifying it. */
    public ShoppingList buildForTodayWithInventory() {
        return subtractInventory(buildForToday());
    }

    /**
     * Builds a list from today through Sunday of the current local week.
     * Persisted history from earlier days of the same week is not loaded.
     */
    public ShoppingList buildForCurrentWeek() {
        LocalDate today = LocalDate.now(clock);
        LocalDate sunday = weekService.weekContaining(today).getEndDate();
        return buildFromEntries(repository.findBetween(today, sunday));
    }

    /** Builds the remaining week's list and subtracts compatible inventory. */
    public ShoppingList buildForCurrentWeekWithInventory() {
        return subtractInventory(buildForCurrentWeek());
    }

    ShoppingList buildFromEntries(Collection<MealPlanEntry> entries) {
        Objects.requireNonNull(entries, "Meal plan entries must not be null.");
        if (entries.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Meal plan entries must not contain null values.");
        }

        Map<AggregationKey, ShoppingListItem> aggregatedItems = new LinkedHashMap<>();
        for (MealPlanEntry entry : entries) {
            List<RecipeIngredient> scaledIngredients = recipeScaler.scale(entry);
            for (RecipeIngredient scaledIngredient : scaledIngredients) {
                addIngredient(aggregatedItems, scaledIngredient);
            }
        }

        return new ShoppingList(aggregatedItems.values().stream().sorted(ITEM_ORDER).toList());
    }

    ShoppingList subtractInventory(ShoppingList requiredList) {
        Objects.requireNonNull(requiredList, "Required shopping list must not be null.");
        List<InventoryItem> inventory = inventoryRepository.findAll();
        List<ShoppingListItem> remaining = requiredList.getItems().stream()
                .map(required -> subtractCompatibleStock(required, inventory))
                .filter(Objects::nonNull)
                .toList();
        return new ShoppingList(remaining);
    }

    private static ShoppingListItem subtractCompatibleStock(
            ShoppingListItem required, List<InventoryItem> inventory) {
        Unit requiredUnit = required.getQuantity().getUnit();
        java.math.BigDecimal available = inventory.stream()
                .filter(item -> item.getIngredient().getId()
                        .equals(required.getIngredient().getId()))
                .filter(item -> UnitConverter.canConvert(item.getUnit(), requiredUnit))
                .map(item -> UnitConverter.convert(
                        item.getQuantity(), item.getUnit(), requiredUnit))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal amount = required.getQuantity().getAmount().subtract(available);
        if (amount.signum() <= 0) {
            return null;
        }
        return new ShoppingListItem(required.getIngredient(), new Quantity(amount, requiredUnit));
    }

    private static InventoryRepository emptyInventoryRepository() {
        return new InventoryRepository() {
            @Override public void save(InventoryItem item) {
                throw new UnsupportedOperationException();
            }
            @Override public java.util.Optional<InventoryItem> findById(UUID id) {
                return java.util.Optional.empty();
            }
            @Override public List<InventoryItem> findAll() { return List.of(); }
            @Override public boolean deleteById(UUID id) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static void addIngredient(Map<AggregationKey, ShoppingListItem> items,
                                      RecipeIngredient recipeIngredient) {
        Ingredient ingredient = recipeIngredient.getIngredient();
        Quantity quantity = new Quantity(recipeIngredient.getQuantity(), recipeIngredient.getUnit());
        AggregationKey key = new AggregationKey(
                ingredient.getId(), compatibilityGroup(quantity.getUnit()));

        ShoppingListItem existing = items.get(key);
        if (existing == null) {
            items.put(key, new ShoppingListItem(ingredient, quantity));
            return;
        }
        items.put(key, new ShoppingListItem(ingredient, existing.getQuantity().add(quantity)));
    }

    private static CompatibilityGroup compatibilityGroup(Unit unit) {
        return switch (unit) {
            case GRAM, KILOGRAM -> CompatibilityGroup.MASS;
            case MILLILITER, LITER -> CompatibilityGroup.VOLUME;
            case PIECE -> CompatibilityGroup.PIECE;
            case SLICE -> CompatibilityGroup.SLICE;
            case CLOVE -> CompatibilityGroup.CLOVE;
            case SPRIG -> CompatibilityGroup.SPRIG;
            case TABLESPOON -> CompatibilityGroup.TABLESPOON;
            case TEASPOON -> CompatibilityGroup.TEASPOON;
            case PINCH -> CompatibilityGroup.PINCH;
        };
    }

    private enum CompatibilityGroup {
        MASS,
        VOLUME,
        PIECE,
        SLICE,
        CLOVE,
        SPRIG,
        TABLESPOON,
        TEASPOON,
        PINCH
    }

    private record AggregationKey(UUID ingredientId, CompatibilityGroup compatibilityGroup) {
    }
}
