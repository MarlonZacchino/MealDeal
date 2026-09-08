package de.mealdeal.catalog;

/** Stable standard categories used by official catalog entries, not user categories. */
public enum CatalogIngredientCategory {
    FRUIT("category.fruit", "Obst"),
    VEGETABLES("category.vegetables", "Gemüse"),
    MEAT("category.meat", "Fleisch"),
    FISH_AND_SEAFOOD("category.fish_and_seafood", "Fisch & Meeresfrüchte"),
    DAIRY("category.dairy", "Milchprodukte"),
    EGGS("category.eggs", "Eier"),
    GRAINS_RICE_AND_PASTA("category.grains_rice_and_pasta", "Getreide, Reis & Nudeln"),
    LEGUMES("category.legumes", "Hülsenfrüchte"),
    HERBS_AND_SPICES("category.herbs_and_spices", "Kräuter & Gewürze"),
    BAKING("category.baking", "Backzutaten"),
    OILS_VINEGAR_AND_SAUCES("category.oils_vinegar_and_sauces", "Öle, Essig & Saucen"),
    NUTS_AND_SEEDS("category.nuts_and_seeds", "Nüsse & Samen"),
    FROZEN("category.frozen", "Tiefkühlprodukte"),
    BEVERAGES("category.beverages", "Getränke"),
    OTHER("category.other", "Sonstiges");

    private final String catalogId;
    private final String displayName;

    CatalogIngredientCategory(String catalogId, String displayName) {
        this.catalogId = catalogId;
        this.displayName = displayName;
    }

    public String getCatalogId() {
        return catalogId;
    }

    public String getDisplayName() {
        return displayName;
    }
}
