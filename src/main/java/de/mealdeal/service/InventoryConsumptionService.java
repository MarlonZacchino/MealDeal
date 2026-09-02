package de.mealdeal.service;

import de.mealdeal.domain.ConsumptionItem;
import de.mealdeal.domain.InventoryConsumption;
import de.mealdeal.domain.InventoryItem;
import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.Unit;
import de.mealdeal.domain.UnitConverter;
import de.mealdeal.persistence.repository.InventoryConsumptionRepository;
import de.mealdeal.persistence.repository.InventoryRepository;
import de.mealdeal.persistence.repository.MealPlanRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Processes each past meal-plan entry against inventory exactly once. */
public final class InventoryConsumptionService {

    private final MealPlanRepository mealPlanRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryConsumptionRepository consumptionRepository;
    private final RecipeScaler recipeScaler;
    private final Clock clock;

    public InventoryConsumptionService(MealPlanRepository mealPlanRepository,
                                       InventoryRepository inventoryRepository,
                                       InventoryConsumptionRepository consumptionRepository) {
        this(mealPlanRepository, inventoryRepository, consumptionRepository,
                new RecipeScaler(), Clock.systemDefaultZone());
    }

    /** Creates a deterministic processor with explicit scaling and time collaborators. */
    public InventoryConsumptionService(MealPlanRepository mealPlanRepository,
                                       InventoryRepository inventoryRepository,
                                       InventoryConsumptionRepository consumptionRepository,
                                       RecipeScaler recipeScaler, Clock clock) {
        this.mealPlanRepository = Objects.requireNonNull(
                mealPlanRepository, "Meal-plan repository must not be null.");
        this.inventoryRepository = Objects.requireNonNull(
                inventoryRepository, "Inventory repository must not be null.");
        this.consumptionRepository = Objects.requireNonNull(
                consumptionRepository, "Consumption repository must not be null.");
        this.recipeScaler = Objects.requireNonNull(recipeScaler, "Recipe scaler must not be null.");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null.");
    }

    /** Processes every unprocessed entry before today and returns the processed count. */
    public int consumePastEntries() {
        LocalDate today = LocalDate.now(clock);
        int processed = 0;
        for (MealPlanEntry entry : mealPlanRepository.findBefore(today)) {
            if (consumptionRepository.existsByMealPlanEntryId(entry.getId())) {
                continue;
            }
            consume(entry);
            processed++;
        }
        return processed;
    }

    private void consume(MealPlanEntry entry) {
        List<RecipeIngredient> resolvedIngredients = recipeScaler.scale(entry);
        List<ConsumptionItem> snapshot = resolvedIngredients.stream()
                .map(ingredient -> new ConsumptionItem(ingredient.getIngredient().getId(),
                        ingredient.getQuantity(), ingredient.getUnit()))
                .toList();
        List<InventoryItem> updates = calculateInventoryUpdates(
                snapshot, inventoryRepository.findAll());
        InventoryConsumption consumption = new InventoryConsumption(
                entry.getId(), entry.getDate(), clock.instant(), snapshot);
        consumptionRepository.saveWithInventoryUpdates(consumption, updates);
    }

    private static List<InventoryItem> calculateInventoryUpdates(
            List<ConsumptionItem> requirements, List<InventoryItem> inventory) {
        Map<UUID, InventoryItem> current = new LinkedHashMap<>();
        inventory.forEach(item -> current.put(item.getId(), item));
        Map<UUID, InventoryItem> changed = new LinkedHashMap<>();

        for (ConsumptionItem requirement : requirements) {
            BigDecimal remaining = requirement.quantity();
            for (InventoryItem original : inventory) {
                InventoryItem stock = current.get(original.getId());
                if (remaining.signum() == 0
                        || !stock.getIngredient().getId().equals(requirement.ingredientId())
                        || !UnitConverter.canConvert(stock.getUnit(), requirement.unit())) {
                    continue;
                }
                BigDecimal available = UnitConverter.convert(
                        stock.getQuantity(), stock.getUnit(), requirement.unit());
                BigDecimal deducted = available.min(remaining);
                if (deducted.signum() == 0) {
                    continue;
                }
                BigDecimal newAmountInRequiredUnit = available.subtract(deducted);
                BigDecimal newStoredAmount = UnitConverter.convert(
                        newAmountInRequiredUnit, requirement.unit(), stock.getUnit());
                InventoryItem updated = new InventoryItem(stock.getId(), stock.getIngredient(),
                        newStoredAmount, stock.getUnit());
                current.put(updated.getId(), updated);
                changed.put(updated.getId(), updated);
                remaining = remaining.subtract(deducted);
            }
        }
        return List.copyOf(changed.values());
    }
}
