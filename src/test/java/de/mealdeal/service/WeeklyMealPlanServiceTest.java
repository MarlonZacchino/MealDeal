package de.mealdeal.service;

import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.Taste;
import de.mealdeal.persistence.repository.MealPlanRepository;
import de.mealdeal.persistence.repository.RecipeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeeklyMealPlanServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 29);

    private InMemoryMealPlanRepository mealPlans;
    private InMemoryRecipeRepository recipes;
    private WeeklyMealPlanService service;
    private Recipe pasta;
    private Recipe soup;

    @BeforeEach
    void setUp() {
        pasta = recipe("Pasta", 2);
        soup = recipe("Suppe", 4);
        mealPlans = new InMemoryMealPlanRepository();
        recipes = new InMemoryRecipeRepository(List.of(soup, pasta));
        service = new WeeklyMealPlanService(
                mealPlans, recipes, new WeekService(), clock(TODAY));
    }

    @Test
    void loadsMondayThroughSundayWithConcreteDatesAndTodayMarker() {
        List<MealPlanDay> days = service.loadCurrentWeek();

        assertEquals(List.of(
                LocalDate.of(2026, 8, 24),
                LocalDate.of(2026, 8, 25),
                LocalDate.of(2026, 8, 26),
                LocalDate.of(2026, 8, 27),
                LocalDate.of(2026, 8, 28),
                LocalDate.of(2026, 8, 29),
                LocalDate.of(2026, 8, 30)),
                days.stream().map(MealPlanDay::date).toList());
        assertEquals(7, days.size());
        assertEquals(List.of(TODAY), days.stream()
                .filter(MealPlanDay::today)
                .map(MealPlanDay::date)
                .toList());
        assertEquals(LocalDate.of(2026, 8, 24), mealPlans.lastRangeStart);
        assertEquals(LocalDate.of(2026, 8, 30), mealPlans.lastRangeEnd);
    }

    @Test
    void plansRecipeWithIndividualServingCount() {
        LocalDate tuesday = LocalDate.of(2026, 8, 25);

        MealPlanEntry planned = service.plan(tuesday, pasta, 7);

        MealPlanEntry persisted = mealPlans.findByDate(tuesday).orElseThrow();
        assertEquals(planned.getId(), persisted.getId());
        assertSame(pasta, persisted.getRecipe());
        assertEquals(7, persisted.getServingCount());
        assertEquals(persisted, service.loadCurrentWeek().get(1).entry().orElseThrow());
    }

    @Test
    void replacesExistingPlanForSameDate() {
        LocalDate thursday = LocalDate.of(2026, 8, 27);
        MealPlanEntry original = service.plan(thursday, pasta, 2);

        MealPlanEntry replacement = service.plan(thursday, soup, 5);

        MealPlanEntry persisted = mealPlans.findByDate(thursday).orElseThrow();
        assertEquals(1, mealPlans.entries.size());
        assertNotEquals(original.getId(), replacement.getId());
        assertEquals(replacement.getId(), persisted.getId());
        assertSame(soup, persisted.getRecipe());
        assertEquals(5, persisted.getServingCount());
    }

    @Test
    void removesExistingPlanAndLeavesEmptyDate() {
        LocalDate sunday = LocalDate.of(2026, 8, 30);
        service.plan(sunday, pasta, 3);

        assertTrue(service.remove(sunday));
        assertTrue(mealPlans.findByDate(sunday).isEmpty());
        assertTrue(service.loadCurrentWeek().getLast().entry().isEmpty());
        assertFalse(service.remove(sunday));
    }

    @Test
    void sortsAvailableRecipesDeterministically() {
        Recipe otherPasta = recipe("Pasta", 3);
        recipes.entries.add(otherPasta);

        List<Recipe> sorted = service.loadAvailableRecipes();

        assertEquals(List.of("Pasta", "Pasta", "Suppe"),
                sorted.stream().map(Recipe::getName).toList());
        assertTrue(sorted.get(0).getId().compareTo(sorted.get(1).getId()) < 0);
    }

    @Test
    void rejectsPlanningOutsideCurrentWeekAndInvalidServings() {
        assertThrows(IllegalArgumentException.class,
                () -> service.plan(LocalDate.of(2026, 8, 31), pasta, 2));
        assertThrows(IllegalArgumentException.class,
                () -> service.plan(LocalDate.of(2026, 8, 25), pasta, 0));
        assertThrows(IllegalArgumentException.class,
                () -> service.remove(LocalDate.of(2026, 8, 23)));
    }

    @Test
    void savesNewReplacedRemovedAndPortionChangesTogetherWithoutRewritingUnchangedDays() {
        LocalDate monday = LocalDate.of(2026, 8, 24);
        LocalDate tuesday = LocalDate.of(2026, 8, 25);
        LocalDate wednesday = LocalDate.of(2026, 8, 26);
        LocalDate thursday = LocalDate.of(2026, 8, 27);
        LocalDate sunday = LocalDate.of(2026, 8, 30);
        MealPlanEntry replaced = service.plan(tuesday, pasta, 2);
        MealPlanEntry removed = service.plan(wednesday, pasta, 3);
        MealPlanEntry portionChanged = service.plan(thursday, pasta, 2);
        MealPlanEntry unchanged = service.plan(sunday, soup, 4);
        mealPlans.clearRecordedChanges();

        service.saveChanges(List.of(
                MealPlanDraft.planned(monday, soup, 6),
                MealPlanDraft.planned(tuesday, soup, 5),
                MealPlanDraft.unplanned(wednesday),
                MealPlanDraft.planned(thursday, pasta, 7),
                MealPlanDraft.planned(sunday, soup, 4)));

        assertEquals(soup.getId(), mealPlans.findByDate(monday).orElseThrow().getRecipe().getId());
        assertEquals(soup.getId(), mealPlans.findByDate(tuesday).orElseThrow().getRecipe().getId());
        assertEquals(5, mealPlans.findByDate(tuesday).orElseThrow().getServingCount());
        assertTrue(mealPlans.findByDate(wednesday).isEmpty());
        assertEquals(7, mealPlans.findByDate(thursday).orElseThrow().getServingCount());
        assertEquals(unchanged.getId(), mealPlans.findByDate(sunday).orElseThrow().getId());
        assertEquals(List.of(monday, tuesday, thursday), mealPlans.savedInLastBatch.stream()
                .map(MealPlanEntry::getDate).toList());
        assertEquals(List.of(removed.getId()), mealPlans.deletedInLastBatch);
        assertFalse(mealPlans.savedInLastBatch.stream()
                .map(MealPlanEntry::getDate).anyMatch(sunday::equals));
        assertNotEquals(replaced.getId(), mealPlans.findByDate(tuesday).orElseThrow().getId());
        assertNotEquals(portionChanged.getId(), mealPlans.findByDate(thursday).orElseThrow().getId());
    }

    private static Clock clock(LocalDate date) {
        ZoneId zone = ZoneId.of("Europe/Berlin");
        return Clock.fixed(date.atStartOfDay(zone).toInstant(), zone);
    }

    private static Recipe recipe(String name, int servings) {
        return new Recipe(name, servings, List.of(), List.of(),
                List.of(new Taste("Herzhaft")));
    }

    private static final class InMemoryMealPlanRepository implements MealPlanRepository {
        private final List<MealPlanEntry> entries = new ArrayList<>();
        private LocalDate lastRangeStart;
        private LocalDate lastRangeEnd;
        private List<MealPlanEntry> savedInLastBatch = List.of();
        private List<UUID> deletedInLastBatch = List.of();

        @Override
        public void save(MealPlanEntry entry) {
            entries.removeIf(existing -> existing.getDate().equals(entry.getDate()));
            entries.add(entry);
        }

        @Override
        public void applyChanges(List<MealPlanEntry> entriesToSave, List<UUID> entryIdsToDelete) {
            savedInLastBatch = List.copyOf(entriesToSave);
            deletedInLastBatch = List.copyOf(entryIdsToDelete);
            entryIdsToDelete.forEach(this::deleteById);
            entriesToSave.forEach(this::save);
        }

        private void clearRecordedChanges() {
            savedInLastBatch = List.of();
            deletedInLastBatch = List.of();
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
                    .toList();
        }

        @Override
        public boolean deleteById(UUID id) {
            return entries.removeIf(entry -> entry.getId().equals(id));
        }

        @Override
        public int deleteBefore(LocalDate cutoffExclusive) {
            int previousSize = entries.size();
            entries.removeIf(entry -> entry.getDate().isBefore(cutoffExclusive));
            return previousSize - entries.size();
        }
    }

    private static final class InMemoryRecipeRepository implements RecipeRepository {
        private final List<Recipe> entries;

        private InMemoryRecipeRepository(List<Recipe> entries) {
            this.entries = new ArrayList<>(entries);
        }

        @Override
        public void save(Recipe recipe) {
            entries.removeIf(existing -> existing.getId().equals(recipe.getId()));
            entries.add(recipe);
        }

        @Override
        public Optional<Recipe> findById(UUID id) {
            return entries.stream().filter(recipe -> recipe.getId().equals(id)).findFirst();
        }

        @Override
        public List<Recipe> findAll() {
            return List.copyOf(entries);
        }

        @Override
        public boolean deleteById(UUID id) {
            return entries.removeIf(recipe -> recipe.getId().equals(id));
        }
    }
}
