package de.mealdeal.service;

import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.MealRole;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.WeekRange;
import de.mealdeal.persistence.repository.MealPlanRepository;
import de.mealdeal.persistence.repository.RecipeRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Coordinates the editable Monday-to-Sunday plan for the current local week. */
public final class WeeklyMealPlanService {

    private static final Comparator<Recipe> RECIPE_ORDER = Comparator
            .comparing(Recipe::getName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Recipe::getName)
            .thenComparing(Recipe::getId);

    private final MealPlanRepository mealPlanRepository;
    private final RecipeRepository recipeRepository;
    private final WeekService weekService;
    private final Clock clock;

    /** Creates the production service using the local system time zone. */
    public WeeklyMealPlanService(MealPlanRepository mealPlanRepository,
                                 RecipeRepository recipeRepository) {
        this(mealPlanRepository, recipeRepository,
                new WeekService(), Clock.systemDefaultZone());
    }

    /** Creates a deterministic service with explicit calendar collaborators. */
    public WeeklyMealPlanService(MealPlanRepository mealPlanRepository,
                                 RecipeRepository recipeRepository,
                                 WeekService weekService, Clock clock) {
        this.mealPlanRepository = Objects.requireNonNull(
                mealPlanRepository, "Meal plan repository must not be null.");
        this.recipeRepository = Objects.requireNonNull(
                recipeRepository, "Recipe repository must not be null.");
        this.weekService = Objects.requireNonNull(
                weekService, "Week service must not be null.");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null.");
    }

    /** Loads all seven current-week dates and their optional persisted entries. */
    public List<MealPlanDay> loadCurrentWeek() {
        LocalDate today = LocalDate.now(clock);
        WeekRange week = weekService.weekContaining(today);
        Map<LocalDate, MealPlanEntry> entriesByDate = mealPlanRepository.findBetween(
                        week.getStartDate(), week.getEndDate()).stream()
                // The current UI still represents one main dish per day; sides are persisted
                // for the follow-up UI phase and deliberately do not replace that main entry.
                .filter(entry -> entry.getMealRole() == MealRole.MAIN)
                .collect(Collectors.toUnmodifiableMap(
                        MealPlanEntry::getDate, Function.identity()));

        return week.days().stream()
                .map(date -> new MealPlanDay(
                        date, date.equals(today),
                        java.util.Optional.ofNullable(entriesByDate.get(date))))
                .toList();
    }

    /** Loads recipes in stable alphabetical order for the day selectors. */
    public List<Recipe> loadAvailableRecipes() {
        return recipeRepository.findAll().stream().sorted(RECIPE_ORDER).toList();
    }

    /**
     * Persists all changed current-week drafts in one repository operation.
     *
     * <p>Unchanged dates are deliberately omitted, so a weekly save does not rewrite
     * entries the user did not edit. The repository owns the transaction that makes
     * additions, replacements and removals atomic.</p>
     */
    public void saveChanges(List<MealPlanDraft> drafts) {
        Objects.requireNonNull(drafts, "Meal plan drafts must not be null.");
        Set<LocalDate> draftDates = new HashSet<>();
        for (MealPlanDraft draft : drafts) {
            Objects.requireNonNull(draft, "Meal plan draft must not be null.");
            requireCurrentWeekDate(draft.date());
            if (!draftDates.add(draft.date())) {
                throw new IllegalArgumentException("Each meal plan date may occur only once.");
            }
        }

        Map<LocalDate, MealPlanEntry> persistedEntries = loadCurrentWeek().stream()
                .flatMap(day -> day.entry().stream())
                .collect(Collectors.toMap(MealPlanEntry::getDate, Function.identity()));
        List<MealPlanEntry> entriesToSave = new ArrayList<>();
        List<UUID> entryIdsToDelete = new ArrayList<>();

        for (MealPlanDraft draft : drafts) {
            MealPlanEntry persistedEntry = persistedEntries.get(draft.date());
            if (draft.recipe().isEmpty()) {
                if (persistedEntry != null) {
                    entryIdsToDelete.add(persistedEntry.getId());
                }
                continue;
            }

            Recipe selectedRecipe = draft.recipe().orElseThrow();
            if (persistedEntry == null || differsFrom(persistedEntry, selectedRecipe,
                    draft.servingCount())) {
                entriesToSave.add(new MealPlanEntry(
                        draft.date(), selectedRecipe, draft.servingCount()));
                if (persistedEntry != null) {
                    // Replacing the MAIN must release its partial unique-index slot
                    // inside the same repository transaction.
                    entryIdsToDelete.add(persistedEntry.getId());
                }
            }
        }

        if (!entriesToSave.isEmpty() || !entryIdsToDelete.isEmpty()) {
            mealPlanRepository.applyChanges(entriesToSave, entryIdsToDelete);
        }
    }

    /** Saves or replaces the single plan entry for one current-week date. */
    public MealPlanEntry plan(LocalDate date, Recipe recipe, int servingCount) {
        requireCurrentWeekDate(date);
        MealPlanEntry entry = new MealPlanEntry(date, recipe, servingCount);
        mealPlanRepository.findByDate(date).ifPresentOrElse(
                existing -> mealPlanRepository.applyChanges(List.of(entry), List.of(existing.getId())),
                () -> mealPlanRepository.save(entry));
        return entry;
    }

    /** Removes the optional entry for one current-week date. */
    public boolean remove(LocalDate date) {
        requireCurrentWeekDate(date);
        return mealPlanRepository.findByDate(date)
                .map(MealPlanEntry::getId)
                .map(mealPlanRepository::deleteById)
                .orElse(false);
    }

    private static boolean differsFrom(MealPlanEntry entry, Recipe recipe, int servingCount) {
        return !entry.getRecipe().getId().equals(recipe.getId())
                || entry.getServingCount() != servingCount;
    }

    private void requireCurrentWeekDate(LocalDate date) {
        Objects.requireNonNull(date, "Meal plan date must not be null.");
        WeekRange week = weekService.weekContaining(LocalDate.now(clock));
        if (date.isBefore(week.getStartDate()) || date.isAfter(week.getEndDate())) {
            throw new IllegalArgumentException("Meal plan date must be in the current week.");
        }
    }
}
