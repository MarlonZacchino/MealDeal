package de.mealdeal.service;

import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.MealRole;
import de.mealdeal.domain.Recipe;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** JavaFX-independent editable state for one weekly-plan day. */
public final class WeeklyMealPlanDayDraft {

    private final LocalDate date;
    private final Optional<MealPlanEntry> originalMain;
    private final List<MealPlanEntry> originalSides;
    private final List<MealPlanEntry> originalDesserts;
    private MealPlanEntry main;
    private List<MealPlanEntry> sides;
    private List<MealPlanEntry> desserts;

    public WeeklyMealPlanDayDraft(MealPlanDay day) {
        Objects.requireNonNull(day, "Meal plan day must not be null.");
        date = day.date();
        originalMain = day.mainEntry();
        originalSides = List.copyOf(day.sideEntries());
        originalDesserts = List.copyOf(day.dessertEntries());
        main = originalMain.orElse(null);
        sides = new ArrayList<>(originalSides);
        desserts = new ArrayList<>(originalDesserts);
    }

    public LocalDate getDate() { return date; }
    public Optional<MealPlanEntry> getMainEntry() { return Optional.ofNullable(main); }
    public List<MealPlanEntry> getSideEntries() { return List.copyOf(sides); }
    public List<MealPlanEntry> getDessertEntries() { return List.copyOf(desserts); }

    /** Selects or clears the optional main dish. */
    public void setMainRecipe(Recipe recipe) {
        if (recipe == null) {
            main = null;
            return;
        }
        requireRole(recipe, MealRole.MAIN);
        int servings = main == null ? recipe.getStandardServingCount() : main.getServingCount();
        Map<UUID, UUID> selections = main != null
                && main.getRecipe().getId().equals(recipe.getId())
                ? main.getIngredientOptionSelections() : Map.of();
        main = entry(main == null ? null : main.getId(), recipe, servings, MealRole.MAIN, 0,
                selections);
    }

    public void setMainServingCount(int servingCount) {
        if (main == null) {
            throw new IllegalStateException("A main dish must be selected first.");
        }
        main = entry(main.getId(), main.getRecipe(), servingCount, MealRole.MAIN, 0,
                main.getIngredientOptionSelections());
    }

    /** Selects one concrete option for a multi-option group of the main entry. */
    public void setMainIngredientOption(UUID groupId, UUID optionId) {
        if (main == null) {
            throw new IllegalStateException("A main dish must be selected first.");
        }
        main = withIngredientOption(main, groupId, optionId);
    }

    /** Adds a side using the main's current portions or the standard default. */
    public void addSide(Recipe recipe) {
        requireRole(Objects.requireNonNull(recipe, "Side recipe must not be null."), MealRole.SIDE);
        int servings = main == null ? Recipe.DEFAULT_SERVING_COUNT : main.getServingCount();
        sides.add(entry(null, recipe, servings, MealRole.SIDE, sides.size(), Map.of()));
    }

    public void setSideRecipe(int index, Recipe recipe) {
        MealPlanEntry current = sideAt(index);
        requireRole(Objects.requireNonNull(recipe, "Side recipe must not be null."), MealRole.SIDE);
        Map<UUID, UUID> selections = current.getRecipe().getId().equals(recipe.getId())
                ? current.getIngredientOptionSelections() : Map.of();
        sides.set(index, entry(current.getId(), recipe, current.getServingCount(),
                MealRole.SIDE, index, selections));
    }

    public void setSideServingCount(int index, int servingCount) {
        MealPlanEntry current = sideAt(index);
        sides.set(index, entry(current.getId(), current.getRecipe(), servingCount,
                MealRole.SIDE, index, current.getIngredientOptionSelections()));
    }

    /** Selects one concrete option for a multi-option group of a side entry. */
    public void setSideIngredientOption(int index, UUID groupId, UUID optionId) {
        MealPlanEntry current = sideAt(index);
        sides.set(index, withIngredientOption(current, groupId, optionId));
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

    /** Adds a dessert with an independent serving count and role-local position. */
    public void addDessert(Recipe recipe) {
        requireRole(Objects.requireNonNull(recipe, "Dessert recipe must not be null."),
                MealRole.DESSERT);
        int servings = main == null ? Recipe.DEFAULT_SERVING_COUNT : main.getServingCount();
        desserts.add(entry(null, recipe, servings, MealRole.DESSERT, desserts.size(), Map.of()));
    }

    public void setDessertRecipe(int index, Recipe recipe) {
        MealPlanEntry current = dessertAt(index);
        requireRole(Objects.requireNonNull(recipe, "Dessert recipe must not be null."),
                MealRole.DESSERT);
        Map<UUID, UUID> selections = current.getRecipe().getId().equals(recipe.getId())
                ? current.getIngredientOptionSelections() : Map.of();
        desserts.set(index, entry(current.getId(), recipe, current.getServingCount(),
                MealRole.DESSERT, index, selections));
    }

    public void setDessertServingCount(int index, int servingCount) {
        MealPlanEntry current = dessertAt(index);
        desserts.set(index, entry(current.getId(), current.getRecipe(), servingCount,
                MealRole.DESSERT, index, current.getIngredientOptionSelections()));
    }

    /** Selects one concrete ingredient option for a dessert entry. */
    public void setDessertIngredientOption(int index, UUID groupId, UUID optionId) {
        MealPlanEntry current = dessertAt(index);
        desserts.set(index, withIngredientOption(current, groupId, optionId));
    }

    public void removeDessert(int index) {
        desserts.remove(index);
        normalizeDessertPositions();
    }

    public void moveDessertUp(int index) {
        if (index > 0 && index < desserts.size()) {
            swapDesserts(index, index - 1);
        }
    }

    public void moveDessertDown(int index) {
        if (index >= 0 && index < desserts.size() - 1) {
            swapDesserts(index, index + 1);
        }
    }

    public boolean isChanged() {
        return !sameEntry(originalMain.orElse(null), main)
                || !sameEntries(originalSides, sides)
                || !sameEntries(originalDesserts, desserts);
    }

    public MealPlanDraft toSaveSnapshot() {
        return new MealPlanDraft(date, Optional.ofNullable(main), sides, desserts);
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
                    MealRole.SIDE, index, side.getIngredientOptionSelections()));
        }
        sides = normalized;
    }

    private void swapDesserts(int first, int second) {
        MealPlanEntry value = desserts.get(first);
        desserts.set(first, desserts.get(second));
        desserts.set(second, value);
        normalizeDessertPositions();
    }

    private void normalizeDessertPositions() {
        List<MealPlanEntry> normalized = new ArrayList<>();
        for (int index = 0; index < desserts.size(); index++) {
            MealPlanEntry dessert = desserts.get(index);
            normalized.add(entry(dessert.getId(), dessert.getRecipe(),
                    dessert.getServingCount(), MealRole.DESSERT, index,
                    dessert.getIngredientOptionSelections()));
        }
        desserts = normalized;
    }

    private MealPlanEntry sideAt(int index) {
        if (index < 0 || index >= sides.size()) {
            throw new IndexOutOfBoundsException("Side index is outside the current draft.");
        }
        return sides.get(index);
    }

    private MealPlanEntry dessertAt(int index) {
        if (index < 0 || index >= desserts.size()) {
            throw new IndexOutOfBoundsException("Dessert index is outside the current draft.");
        }
        return desserts.get(index);
    }

    private MealPlanEntry entry(UUID id, Recipe recipe, int servingCount, MealRole role,
                                int position, Map<UUID, UUID> selections) {
        return id == null
                ? new MealPlanEntry(date, recipe, servingCount, role, position, selections)
                : new MealPlanEntry(id, date, recipe, servingCount, role, position, selections);
    }

    private static MealPlanEntry withIngredientOption(MealPlanEntry entry,
                                                       UUID groupId, UUID optionId) {
        Objects.requireNonNull(groupId, "Ingredient group ID must not be null.");
        Objects.requireNonNull(optionId, "Ingredient option ID must not be null.");
        var group = entry.getRecipe().getIngredientGroups().stream()
                .filter(candidate -> candidate.getId().equals(groupId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Ingredient group must belong to the planned recipe."));
        Map<UUID, UUID> selections = new LinkedHashMap<>(
                entry.getIngredientOptionSelections());
        if (group.getStandardOptionId().equals(optionId)) {
            selections.remove(groupId);
        } else {
            selections.put(groupId, optionId);
        }
        return new MealPlanEntry(entry.getId(), entry.getDate(), entry.getRecipe(),
                entry.getServingCount(), entry.getMealRole(), entry.getPosition(), selections);
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
                && first.getPosition() == second.getPosition()
                && first.getIngredientOptionSelections().equals(
                        second.getIngredientOptionSelections());
    }
}
