package de.mealdeal.service;

import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.WeekRange;
import de.mealdeal.persistence.repository.MealPlanRepository;
import de.mealdeal.persistence.repository.RecipeRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /** Saves or replaces the single plan entry for one current-week date. */
    public MealPlanEntry plan(LocalDate date, Recipe recipe, int servingCount) {
        requireCurrentWeekDate(date);
        MealPlanEntry entry = new MealPlanEntry(date, recipe, servingCount);
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

    private void requireCurrentWeekDate(LocalDate date) {
        Objects.requireNonNull(date, "Meal plan date must not be null.");
        WeekRange week = weekService.weekContaining(LocalDate.now(clock));
        if (date.isBefore(week.getStartDate()) || date.isAfter(week.getEndDate())) {
            throw new IllegalArgumentException("Meal plan date must be in the current week.");
        }
    }
}
