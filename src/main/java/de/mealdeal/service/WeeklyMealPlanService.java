package de.mealdeal.service;

import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.MealRole;
import de.mealdeal.domain.DishType;
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

    /** Loads all seven current-week dates with their optional main and ordered side entries. */
    public List<MealPlanDay> loadCurrentWeek() {
        LocalDate today = LocalDate.now(clock);
        WeekRange week = weekService.weekContaining(today);
        Map<LocalDate, List<MealPlanEntry>> entriesByDate = mealPlanRepository.findBetween(
                        week.getStartDate(), week.getEndDate()).stream()
                .collect(Collectors.groupingBy(MealPlanEntry::getDate));

        return week.days().stream()
                .map(date -> toMealPlanDay(date, date.equals(today),
                        entriesByDate.getOrDefault(date, List.of())))
                .toList();
    }

    /** Loads recipes in stable alphabetical order for the day selectors. */
    public List<Recipe> loadAvailableRecipes() {
        return recipeRepository.findAll().stream().sorted(RECIPE_ORDER).toList();
    }

    /** Loads only recipes that may be selected for the requested planning role. */
    public List<Recipe> loadAvailableRecipes(DishType dishType) {
        Objects.requireNonNull(dishType, "Dish type must not be null.");
        return loadAvailableRecipes().stream()
                .filter(recipe -> recipe.getDishType() == dishType)
                .toList();
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

        Map<UUID, MealPlanEntry> persistedEntries = mealPlanRepository.findBetween(
                        currentWeek().getStartDate(), currentWeek().getEndDate()).stream()
                .filter(entry -> draftDates.contains(entry.getDate()))
                .collect(Collectors.toMap(MealPlanEntry::getId, entry -> entry));
        List<MealPlanEntry> entriesToSave = new ArrayList<>();
        List<UUID> entryIdsToDelete = new ArrayList<>();

        for (MealPlanDraft draft : drafts) {
            for (MealPlanEntry entry : entriesOf(draft)) {
                MealPlanEntry persisted = persistedEntries.remove(entry.getId());
                if (!sameEntry(persisted, entry)) {
                    entriesToSave.add(entry);
                }
            }
        }
        entryIdsToDelete.addAll(persistedEntries.keySet());

        if (!entriesToSave.isEmpty() || !entryIdsToDelete.isEmpty()) {
            mealPlanRepository.applyChanges(entriesToSave, entryIdsToDelete);
        }
    }

    /** Saves or replaces the single plan entry for one current-week date. */
    public MealPlanEntry plan(LocalDate date, Recipe recipe, int servingCount) {
        requireCurrentWeekDate(date);
        Objects.requireNonNull(recipe, "Recipe must not be null.");
        if (recipe.getDishType() != DishType.MAIN) {
            throw new IllegalArgumentException("The single-plan helper accepts main dishes only.");
        }
        MealPlanEntry entry = mealPlanRepository.findByDate(date)
                .map(existing -> new MealPlanEntry(existing.getId(), date, recipe, servingCount,
                        MealRole.MAIN, 0,
                        existing.getRecipe().getId().equals(recipe.getId())
                                ? existing.getIngredientOptionSelections() : Map.of()))
                .orElseGet(() -> new MealPlanEntry(date, recipe, servingCount));
        mealPlanRepository.save(entry);
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

    private static MealPlanDay toMealPlanDay(LocalDate date, boolean today,
                                             List<MealPlanEntry> entries) {
        List<MealPlanEntry> mains = entries.stream()
                .filter(entry -> entry.getMealRole() == MealRole.MAIN).toList();
        if (mains.size() > 1) {
            throw new IllegalStateException("A day must not contain multiple main dishes.");
        }
        List<MealPlanEntry> sides = entries.stream()
                .filter(entry -> entry.getMealRole() == MealRole.SIDE)
                .sorted(Comparator.comparingInt(MealPlanEntry::getPosition))
                .toList();
        return new MealPlanDay(date, today, mains.stream().findFirst(), sides);
    }

    private static List<MealPlanEntry> entriesOf(MealPlanDraft draft) {
        List<MealPlanEntry> entries = new ArrayList<>();
        draft.mainEntry().ifPresent(entries::add);
        entries.addAll(draft.sideEntries());
        return entries;
    }

    private static boolean sameEntry(MealPlanEntry first, MealPlanEntry second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.getId().equals(second.getId())
                && first.getDate().equals(second.getDate())
                && first.getRecipe().getId().equals(second.getRecipe().getId())
                && first.getServingCount() == second.getServingCount()
                && first.getMealRole() == second.getMealRole()
                && first.getPosition() == second.getPosition()
                && first.getIngredientOptionSelections().equals(
                        second.getIngredientOptionSelections());
    }

    private void requireCurrentWeekDate(LocalDate date) {
        Objects.requireNonNull(date, "Meal plan date must not be null.");
        WeekRange week = currentWeek();
        if (date.isBefore(week.getStartDate()) || date.isAfter(week.getEndDate())) {
            throw new IllegalArgumentException("Meal plan date must be in the current week.");
        }
    }

    private WeekRange currentWeek() {
        return weekService.weekContaining(LocalDate.now(clock));
    }
}
