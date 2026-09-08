package de.mealdeal.service;

import de.mealdeal.domain.MealHistoryEntry;
import de.mealdeal.domain.MealHistorySource;
import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.Recipe;
import de.mealdeal.persistence.repository.MealHistoryRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Application service for immutable confirmations of actually cooked meals. */
public final class MealHistoryService {

    private final MealHistoryRepository repository;
    private final Clock clock;

    public MealHistoryService(MealHistoryRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    public MealHistoryService(MealHistoryRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "Repository must not be null.");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null.");
    }

    /** Records an unplanned meal; repeated manual events remain intentionally possible. */
    public MealHistoryEntry recordManual(Recipe recipe, int servings, Instant occurredAt) {
        return record(recipe, servings, occurredAt, MealHistorySource.MANUAL, null);
    }

    /** Records a meal explicitly confirmed after a recommendation. */
    public MealHistoryEntry recordRecommendation(
            Recipe recipe, int servings, Instant occurredAt) {
        return record(recipe, servings, occurredAt, MealHistorySource.RECOMMENDATION, null);
    }

    /**
     * Confirms a planned entry exactly once. Repeating the same confirmation returns the
     * existing event and never consumes inventory or changes the meal plan.
     */
    public MealHistoryEntry confirmCooked(MealPlanEntry planEntry, Instant occurredAt) {
        Objects.requireNonNull(planEntry, "Meal-plan entry must not be null.");
        Objects.requireNonNull(occurredAt, "Meal occurrence timestamp must not be null.");
        Optional<MealHistoryEntry> existing = repository.findBySourceMealPlanEntryId(
                planEntry.getId());
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }
        MealHistoryEntry event = new MealHistoryEntry(planEntry.getRecipe().getId(),
                planEntry.getRecipe().getName(), occurredAt, planEntry.getServingCount(),
                MealHistorySource.MEAL_PLAN, planEntry.getId(), clock.instant());
        repository.save(event);
        return repository.findBySourceMealPlanEntryId(planEntry.getId()).orElse(event);
    }

    public List<MealHistoryEntry> findRecent(int limit) {
        return repository.findRecent(limit);
    }

    public List<MealHistoryEntry> findByRecipeId(UUID recipeId) {
        return repository.findByRecipeId(Objects.requireNonNull(
                recipeId, "Recipe ID must not be null."));
    }

    public Optional<MealHistoryEntry> findLatestByRecipeId(UUID recipeId) {
        return repository.findLatestByRecipeId(Objects.requireNonNull(
                recipeId, "Recipe ID must not be null."));
    }

    public List<MealHistoryEntry> findBetween(Instant fromInclusive, Instant toExclusive) {
        return repository.findBetween(
                Objects.requireNonNull(fromInclusive, "Start timestamp must not be null."),
                Objects.requireNonNull(toExclusive, "End timestamp must not be null."));
    }

    public long countByRecipeIdBetween(
            UUID recipeId, Instant fromInclusive, Instant toExclusive) {
        return repository.countByRecipeIdBetween(
                Objects.requireNonNull(recipeId, "Recipe ID must not be null."),
                Objects.requireNonNull(fromInclusive, "Start timestamp must not be null."),
                Objects.requireNonNull(toExclusive, "End timestamp must not be null."));
    }

    public boolean wasEverCooked(UUID recipeId) {
        return repository.existsByRecipeId(Objects.requireNonNull(
                recipeId, "Recipe ID must not be null."));
    }

    /** Deletes a mistaken history event without touching the independent consumption ledger. */
    public boolean delete(UUID historyEntryId) {
        return repository.deleteById(Objects.requireNonNull(
                historyEntryId, "Meal history ID must not be null."));
    }

    private MealHistoryEntry record(Recipe recipe, int servings, Instant occurredAt,
                                    MealHistorySource source, UUID mealPlanEntryId) {
        Objects.requireNonNull(recipe, "Recipe must not be null.");
        MealHistoryEntry event = new MealHistoryEntry(recipe.getId(), recipe.getName(),
                Objects.requireNonNull(occurredAt,
                        "Meal occurrence timestamp must not be null."),
                servings, source, mealPlanEntryId, clock.instant());
        repository.save(event);
        return event;
    }
}
