package de.mealdeal.domain;

import java.util.List;
import java.util.UUID;

/** Stable catalog initially installed in every MealDeal database. */
public final class IngredientCategories {

    public static final IngredientCategory FRUIT = category(1, "Obst", 0);
    public static final IngredientCategory VEGETABLES = category(2, "Gemüse", 1);
    public static final IngredientCategory MEAT = category(3, "Fleisch", 2);
    public static final IngredientCategory FISH_AND_SEAFOOD =
            category(4, "Fisch & Meeresfrüchte", 3);
    public static final IngredientCategory DAIRY = category(5, "Milchprodukte", 4);
    public static final IngredientCategory EGGS = category(6, "Eier", 5);
    public static final IngredientCategory GRAINS_RICE_AND_PASTA =
            category(7, "Getreide, Reis & Nudeln", 6);
    public static final IngredientCategory LEGUMES = category(8, "Hülsenfrüchte", 7);
    public static final IngredientCategory HERBS_AND_SPICES =
            category(9, "Kräuter & Gewürze", 8);
    public static final IngredientCategory BAKING = category(10, "Backzutaten", 9);
    public static final IngredientCategory OILS_VINEGAR_AND_SAUCES =
            category(11, "Öle, Essig & Saucen", 10);
    public static final IngredientCategory NUTS_AND_SEEDS =
            category(12, "Nüsse & Samen", 11);
    public static final IngredientCategory FROZEN = category(13, "Tiefkühlprodukte", 12);
    public static final IngredientCategory BEVERAGES = category(14, "Getränke", 13);
    public static final IngredientCategory OTHER = category(15, "Sonstiges", 14);

    private static final List<IngredientCategory> START_CATEGORIES = List.of(
            FRUIT, VEGETABLES, MEAT, FISH_AND_SEAFOOD, DAIRY, EGGS,
            GRAINS_RICE_AND_PASTA, LEGUMES, HERBS_AND_SPICES, BAKING,
            OILS_VINEGAR_AND_SAUCES, NUTS_AND_SEEDS, FROZEN, BEVERAGES, OTHER);

    private IngredientCategories() {
    }

    /** Returns the immutable start catalog in its persisted display order. */
    public static List<IngredientCategory> all() {
        return START_CATEGORIES;
    }

    private static IngredientCategory category(int suffix, String name, int position) {
        return new IngredientCategory(UUID.fromString(
                "00000000-0000-4000-8000-%012d".formatted(suffix)), name, position);
    }
}
