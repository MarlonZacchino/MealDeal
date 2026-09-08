package de.mealdeal.catalog;

import de.mealdeal.domain.Unit;

import java.util.List;
import java.util.Optional;

import static de.mealdeal.catalog.CatalogIngredientCategory.BAKING;
import static de.mealdeal.catalog.CatalogIngredientCategory.DAIRY;
import static de.mealdeal.catalog.CatalogIngredientCategory.EGGS;
import static de.mealdeal.catalog.CatalogIngredientCategory.FISH_AND_SEAFOOD;
import static de.mealdeal.catalog.CatalogIngredientCategory.FRUIT;
import static de.mealdeal.catalog.CatalogIngredientCategory.GRAINS_RICE_AND_PASTA;
import static de.mealdeal.catalog.CatalogIngredientCategory.HERBS_AND_SPICES;
import static de.mealdeal.catalog.CatalogIngredientCategory.LEGUMES;
import static de.mealdeal.catalog.CatalogIngredientCategory.MEAT;
import static de.mealdeal.catalog.CatalogIngredientCategory.NUTS_AND_SEEDS;
import static de.mealdeal.catalog.CatalogIngredientCategory.OILS_VINEGAR_AND_SAUCES;
import static de.mealdeal.catalog.CatalogIngredientCategory.OTHER;
import static de.mealdeal.catalog.CatalogIngredientCategory.VEGETABLES;
import static de.mealdeal.domain.Unit.CLOVE;
import static de.mealdeal.domain.Unit.GRAM;
import static de.mealdeal.domain.Unit.KILOGRAM;
import static de.mealdeal.domain.Unit.LITER;
import static de.mealdeal.domain.Unit.MILLILITER;
import static de.mealdeal.domain.Unit.PIECE;
import static de.mealdeal.domain.Unit.PINCH;
import static de.mealdeal.domain.Unit.SLICE;
import static de.mealdeal.domain.Unit.SPRIG;
import static de.mealdeal.domain.Unit.TABLESPOON;
import static de.mealdeal.domain.Unit.TEASPOON;

/** Versioned-in-code, immutable V1 ingredient catalog for offline use. */
public final class StandardIngredientCatalog {

    public static final String VERSION = "1";

    private static final List<CatalogIngredient> ENTRIES = List.of(
            entry("tomato", "Tomate", VEGETABLES, units(GRAM, KILOGRAM, PIECE), "Tomaten"),
            entry("onion", "Zwiebel", VEGETABLES, units(GRAM, KILOGRAM, PIECE), "Zwiebeln"),
            entry("garlic", "Knoblauch", VEGETABLES, units(GRAM, CLOVE), "Knoblauchzehen"),
            entry("potato", "Kartoffel", VEGETABLES, units(GRAM, KILOGRAM, PIECE), "Kartoffeln"),
            entry("carrot", "Karotte", VEGETABLES, units(GRAM, KILOGRAM, PIECE), "Karotten", "Möhre", "Möhren"),
            entry("bell_pepper", "Paprika", VEGETABLES, units(GRAM, PIECE), "Paprikaschote", "Paprikaschoten"),
            entry("zucchini", "Zucchini", VEGETABLES, units(GRAM, PIECE)),
            entry("cucumber", "Gurke", VEGETABLES, units(GRAM, PIECE), "Gurken"),
            entry("broccoli", "Brokkoli", VEGETABLES, units(GRAM, KILOGRAM)),
            entry("cauliflower", "Blumenkohl", VEGETABLES, units(GRAM, KILOGRAM)),
            entry("spinach", "Spinat", VEGETABLES, units(GRAM, KILOGRAM)),
            entry("mushroom", "Champignon", VEGETABLES, units(GRAM, KILOGRAM), "Champignons"),
            entry("leek", "Lauch", VEGETABLES, units(GRAM, PIECE), "Porree"),

            entry("apple", "Apfel", FRUIT, units(GRAM, KILOGRAM, PIECE), "Äpfel"),
            entry("banana", "Banane", FRUIT, units(GRAM, KILOGRAM, PIECE), "Bananen"),
            entry("strawberry", "Erdbeere", FRUIT, units(GRAM, KILOGRAM), "Erdbeeren"),
            entry("lemon", "Zitrone", FRUIT, units(GRAM, PIECE), "Zitronen"),
            entry("lime", "Limette", FRUIT, units(GRAM, PIECE), "Limetten"),
            entry("orange", "Orange", FRUIT, units(GRAM, PIECE), "Orangen"),
            entry("pear", "Birne", FRUIT, units(GRAM, PIECE), "Birnen"),
            entry("peach", "Pfirsich", FRUIT, units(GRAM, PIECE), "Pfirsiche"),

            entry("chicken_breast", "Hähnchenbrust", MEAT, units(GRAM, KILOGRAM, PIECE), "Hähnchenbrustfilet", "Hähnchenbrust Filet"),
            entry("beef", "Rindfleisch", MEAT, units(GRAM, KILOGRAM)),
            entry("ground_beef", "Rinderhackfleisch", MEAT, units(GRAM, KILOGRAM), "Rinderhack"),
            entry("pork", "Schweinefleisch", MEAT, units(GRAM, KILOGRAM)),
            entry("pork_cutlet", "Schweineschnitzel", MEAT, units(GRAM, PIECE)),
            entry("turkey_breast", "Putenbrust", MEAT, units(GRAM, KILOGRAM, PIECE), "Putenbrustfilet"),
            entry("bacon", "Speck", MEAT, units(GRAM, SLICE)),

            entry("salmon", "Lachs", FISH_AND_SEAFOOD, units(GRAM, KILOGRAM, PIECE), "Lachsfilet"),
            entry("tuna", "Thunfisch", FISH_AND_SEAFOOD, units(GRAM, KILOGRAM)),
            entry("shrimp", "Garnele", FISH_AND_SEAFOOD, units(GRAM, KILOGRAM), "Garnelen"),
            entry("cod", "Kabeljau", FISH_AND_SEAFOOD, units(GRAM, KILOGRAM, PIECE), "Kabeljaufilet"),

            entry("milk", "Milch", DAIRY, units(MILLILITER, LITER)),
            entry("cream", "Sahne", DAIRY, units(MILLILITER, LITER)),
            entry("butter", "Butter", DAIRY, units(GRAM, KILOGRAM)),
            entry("cheese", "Käse", DAIRY, units(GRAM, KILOGRAM, SLICE)),
            entry("yogurt", "Joghurt", DAIRY, units(GRAM, KILOGRAM)),
            entry("quark", "Quark", DAIRY, units(GRAM, KILOGRAM)),
            entry("mozzarella", "Mozzarella", DAIRY, units(GRAM, PIECE)),
            entry("egg", "Ei", EGGS, units(PIECE), "Eier"),

            entry("rice", "Reis", GRAINS_RICE_AND_PASTA, units(GRAM, KILOGRAM)),
            entry("pasta", "Nudeln", GRAINS_RICE_AND_PASTA, units(GRAM, KILOGRAM), "Pasta"),
            entry("bread", "Brot", GRAINS_RICE_AND_PASTA, units(GRAM, SLICE, PIECE)),
            entry("oats", "Haferflocken", GRAINS_RICE_AND_PASTA, units(GRAM, KILOGRAM)),
            entry("couscous", "Couscous", GRAINS_RICE_AND_PASTA, units(GRAM, KILOGRAM)),
            entry("bulgur", "Bulgur", GRAINS_RICE_AND_PASTA, units(GRAM, KILOGRAM)),
            entry("cornmeal", "Maisgrieß", GRAINS_RICE_AND_PASTA, units(GRAM, KILOGRAM), "Polenta"),

            entry("lentil", "Linse", LEGUMES, units(GRAM, KILOGRAM), "Linsen"),
            entry("chickpea", "Kichererbse", LEGUMES, units(GRAM, KILOGRAM), "Kichererbsen"),
            entry("kidney_bean", "Kidneybohne", LEGUMES, units(GRAM, KILOGRAM), "Kidneybohnen"),
            entry("pea", "Erbse", LEGUMES, units(GRAM, KILOGRAM), "Erbsen"),

            entry("salt", "Salz", HERBS_AND_SPICES, units(GRAM, TEASPOON, PINCH)),
            entry("black_pepper", "Schwarzer Pfeffer", HERBS_AND_SPICES, units(GRAM, TEASPOON, PINCH), "Pfeffer"),
            entry("basil", "Basilikum", HERBS_AND_SPICES, units(GRAM, SPRIG)),
            entry("parsley", "Petersilie", HERBS_AND_SPICES, units(GRAM, SPRIG)),
            entry("thyme", "Thymian", HERBS_AND_SPICES, units(GRAM, SPRIG)),
            entry("rosemary", "Rosmarin", HERBS_AND_SPICES, units(GRAM, SPRIG)),
            entry("paprika_powder", "Paprikapulver", HERBS_AND_SPICES, units(GRAM, TEASPOON)),
            entry("chili", "Chili", HERBS_AND_SPICES, units(GRAM, PIECE), "Chilischote", "Chilischoten"),
            entry("oregano", "Oregano", HERBS_AND_SPICES, units(GRAM, TEASPOON, SPRIG)),

            entry("flour", "Mehl", BAKING, units(GRAM, KILOGRAM)),
            entry("sugar", "Zucker", BAKING, units(GRAM, KILOGRAM)),
            entry("baking_powder", "Backpulver", BAKING, units(GRAM, TEASPOON)),
            entry("yeast", "Hefe", BAKING, units(GRAM, PIECE)),
            entry("cocoa_powder", "Kakaopulver", BAKING, units(GRAM, TABLESPOON, TEASPOON)),

            entry("olive_oil", "Olivenöl", OILS_VINEGAR_AND_SAUCES, units(MILLILITER, LITER, TABLESPOON)),
            entry("sunflower_oil", "Sonnenblumenöl", OILS_VINEGAR_AND_SAUCES, units(MILLILITER, LITER, TABLESPOON)),
            entry("vinegar", "Essig", OILS_VINEGAR_AND_SAUCES, units(MILLILITER, LITER, TABLESPOON)),
            entry("soy_sauce", "Sojasauce", OILS_VINEGAR_AND_SAUCES, units(MILLILITER, TABLESPOON, TEASPOON)),
            entry("tomato_paste", "Tomatenmark", OILS_VINEGAR_AND_SAUCES, units(GRAM, TABLESPOON, TEASPOON)),
            entry("mustard", "Senf", OILS_VINEGAR_AND_SAUCES, units(GRAM, TABLESPOON, TEASPOON)),

            entry("almond", "Mandel", NUTS_AND_SEEDS, units(GRAM, KILOGRAM), "Mandeln"),
            entry("walnut", "Walnuss", NUTS_AND_SEEDS, units(GRAM, KILOGRAM), "Walnüsse"),
            entry("sesame", "Sesam", NUTS_AND_SEEDS, units(GRAM, TABLESPOON, TEASPOON)),
            entry("sunflower_seed", "Sonnenblumenkern", NUTS_AND_SEEDS, units(GRAM, KILOGRAM), "Sonnenblumenkerne"),

            entry("tofu", "Tofu", OTHER, units(GRAM, KILOGRAM)),
            entry("honey", "Honig", OTHER, units(GRAM, TABLESPOON, TEASPOON)),
            entry("vegetable_broth", "Gemüsebrühe", OTHER, units(MILLILITER, LITER), "Gemüsefond")
    );

    private StandardIngredientCatalog() {
    }

    /** Returns all V1 entries in stable declaration order. */
    public static List<CatalogIngredient> all() {
        return ENTRIES;
    }

    public static Optional<CatalogIngredient> findById(String catalogId) {
        return ENTRIES.stream().filter(entry -> entry.catalogId().equals(catalogId)).findFirst();
    }

    /** Resolves one ID or explicit German term by conservative matching. */
    public static CatalogMatch<CatalogIngredient> resolve(String input) {
        return CatalogMatcher.resolve(ENTRIES, input);
    }

    private static CatalogIngredient entry(
            String key, String name, CatalogIngredientCategory category,
            List<Unit> units, String... aliases) {
        return new CatalogIngredient(
                "ingredient." + key, name, List.of(aliases), category, units);
    }

    private static List<Unit> units(Unit... units) {
        return List.of(units);
    }
}
