package de.mealdeal.service;

import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.MealRole;
import de.mealdeal.domain.Recipe;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** JavaFX-independent editable state for one weekly-plan day. */
public final class WeeklyMealPlanDayDraft {

    private final LocalDate date;
    private final Optional<MealPlanEntry> originalMain;
    private final List<MealPlanEntry> originalSides;
    private MealPlanEntry main;
    private List<MealPlanEntry> sides;

    public WeeklyMealPlanDayDraft(MealPlanDay day) {
        Objects.requireNonNull(day, "Meal plan day must not be null.");
        date = day.date();
        originalMain = day.mainEntry();
        originalSides = List.copyOf(day.sideEntries());
        main = originalMain.orElse(null);
        sides = new ArrayList<>(originalSides);
    }

    public LocalDate getDate() { return date; }
    public Optional<MealPlanEntry> getMainEntry() { return Optional.ofNullable(main); }
    public List<MealPlanEntry> getSideEntries() { return List.copyOf(sides); }

    /** Selects or clears the optional main dish. */
    public void setMainRecipe(Recipe recipe) {
        if (recipe == null) {
            main = null;
            return;
        }
        requireRole(recipe, MealRole.MAIN);
        int servings = main == null ? recipe.getStandardServingCount() : main.getServingCount();
        main = entry(main == null ? null : main.getId(), recipe, servings, MealRole.MAIN, 0);
    }

    public void setMainServingCount(int servingCount) {
        if (main == null) {
            throw new IllegalStateException("A main dish must be selected first.");
        }
        main = entry(main.getId(), main.getRecipe(), servingCount, MealRole.MAIN, 0);
    }

    /** Adds a side using the main's current portions or the standard default. */
    public void addSide(Recipe recipe) {
        requireRole(Objects.requireNonNull(recipe, "Side recipe must not be null."), MealRole.SIDE);
        int servings = main == null ? Recipe.DEFAULT_SERVING_COUNT : main.getServingCount();
        sides.add(entry(null, recipe, servings, MealRole.SIDE, sides.size()));
    }

    public void setSideRecipe(int index, Recipe recipe) {
        MealPlanEntry current = sideAt(index);
        requireRole(Objects.requireNonNull(recipe, "Side recipe must not be null."), MealRole.SIDE);
        sides.set(index, entry(current.getId(), recipe, current.getServingCount(),
                MealRole.SIDE, index));
    }

    public void setSideServingCount(int index, int servingCount) {
        MealPlanEntry current = sideAt(index);
        sides.set(index, entry(current.getId(), current.getRecipe(), servingCount,
                MealRole.SIDE, index));
    }

    public void removeSide(int index) {
        sides.remove(index);
        normalizeSidePositions();
    }

    public void moveSideUp(int index) {
        if (index > 0 && index < sides.size()) {
            swapSides(index, index - 1);
        }
    }

    public void moveSideDown(int index) {
        if (index >= 0 && index < sides.size() - 1) {
            swapSides(index, index + 1);
        }
    }

    public boolean isChanged() {
        return !sameEntry(originalMain.orElse(null), main) || !sameEntries(originalSides, sides);
    }

    public MealPlanDraft toSaveSnapshot() {
        return new MealPlanDraft(date, Optional.ofNullable(main), sides);
    }

    private void swapSides(int first, int second) {
        MealPlanEntry value = sides.get(first);
        sides.set(first, sides.get(second));
        sides.set(second, value);
        normalizeSidePositions();
    }

    private void normalizeSidePositions() {
        List<MealPlanEntry> normalized = new ArrayList<>();
        for (int index = 0; index < sides.size(); index++) {
            MealPlanEntry side = sides.get(index);
            normalized.add(entry(side.getId(), side.getRecipe(), side.getServingCount(),
                    MealRole.SIDE, index));
        }
        sides = normalized;
    }

    private MealPlanEntry sideAt(int index) {
        if (index < 0 || index >= sides.size()) {
            throw new IndexOutOfBoundsException("Side index is outside the current draft.");
        }
        return sides.get(index);
    }

    private MealPlanEntry entry(UUID id, Recipe recipe, int servingCount, MealRole role,
                                int position) {
        return id == null
                ? new MealPlanEntry(date, recipe, servingCount, role, position)
                : new MealPlanEntry(id, date, recipe, servingCount, role, position);
    }

    private static void requireRole(Recipe recipe, MealRole role) {
        if (MealRole.forDishType(recipe.getDishType()) != role) {
            throw new IllegalArgumentException("Recipe type does not match the planned meal role.");
        }
    }

    private static boolean sameEntries(List<MealPlanEntry> first, List<MealPlanEntry> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            if (!sameEntry(first.get(index), second.get(index))) {
                return false;
            }
        }
        return true;
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
                && first.getPosition() == second.getPosition();
    }
}
