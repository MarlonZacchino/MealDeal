package de.mealdeal.ui;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.IngredientCategory;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Shared presentation grouping for ingredient category panes. */
public final class IngredientCategoryGrouping {

    private IngredientCategoryGrouping() {
    }

    /**
     * Groups visible ingredients by category and sorts both levels by German display name.
     * Empty categories never occur because the input contains ingredients rather than a catalog.
     */
    public static List<Group> group(List<Ingredient> ingredients, String filterText,
                                    Collection<UUID> excludedIngredientIds) {
        Objects.requireNonNull(ingredients, "Ingredients must not be null.");
        Set<UUID> excludedIds = Objects.requireNonNull(
                excludedIngredientIds, "Excluded ingredient IDs must not be null.").stream()
                .collect(Collectors.toUnmodifiableSet());
        String filter = normalized(filterText);
        Comparator<Ingredient> ingredientOrder = Comparator
                .comparing(Ingredient::getName, IngredientCategoryGrouping::compareGermanNames)
                .thenComparing(Ingredient::getId);
        Map<IngredientCategory, List<Ingredient>> grouped = new LinkedHashMap<>();
        ingredients.stream()
                .filter(Objects::nonNull)
                .filter(ingredient -> !excludedIds.contains(ingredient.getId()))
                .filter(ingredient -> normalized(ingredient.getName()).contains(filter))
                .sorted(ingredientOrder)
                .forEach(ingredient -> grouped.computeIfAbsent(
                        ingredient.getCategory(), ignored -> new ArrayList<>()).add(ingredient));
        return grouped.entrySet().stream()
                .map(entry -> new Group(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparing((Group group) -> group.category().getName(),
                                IngredientCategoryGrouping::compareGermanNames)
                        .thenComparing(group -> group.category().getId()))
                .toList();
    }

    /** Active filters expand matching categories so their results are immediately visible. */
    public static boolean shouldExpandForFilter(String filterText) {
        return !normalized(filterText).isEmpty();
    }

    /** Immutable category and ingredient rows shared by both ingredient presentations. */
    public record Group(IngredientCategory category, List<Ingredient> ingredients) {
        public Group {
            Objects.requireNonNull(category, "Ingredient category must not be null.");
            ingredients = List.copyOf(ingredients);
        }
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.GERMAN);
    }

    private static int compareGermanNames(String first, String second) {
        Collator collator = Collator.getInstance(Locale.GERMAN);
        collator.setStrength(Collator.PRIMARY);
        int comparison = collator.compare(first, second);
        if (comparison != 0) {
            return comparison;
        }
        comparison = String.CASE_INSENSITIVE_ORDER.compare(first, second);
        return comparison != 0 ? comparison : first.compareTo(second);
    }
}
