package de.mealdeal.catalog;

import java.util.List;
import java.util.Optional;

/** Versioned-in-code, immutable V1 taste catalog for offline use. */
public final class StandardTasteCatalog {

    public static final String VERSION = "1";

    private static final List<CatalogTaste> ENTRIES = List.of(
            taste("savory", "Herzhaft"),
            taste("sweet", "Süß"),
            taste("salty", "Salzig"),
            taste("sour", "Sauer"),
            taste("spicy", "Scharf", "Pikant"),
            taste("creamy", "Cremig"),
            taste("fruity", "Fruchtig"),
            taste("smoky", "Rauchig"),
            taste("fresh", "Frisch"),
            taste("umami", "Umami")
    );

    private StandardTasteCatalog() {
    }

    /** Returns all V1 entries in stable declaration order. */
    public static List<CatalogTaste> all() {
        return ENTRIES;
    }

    public static Optional<CatalogTaste> findById(String catalogId) {
        return ENTRIES.stream().filter(entry -> entry.catalogId().equals(catalogId)).findFirst();
    }

    /** Resolves one ID or explicit German term by conservative matching. */
    public static CatalogMatch<CatalogTaste> resolve(String input) {
        return CatalogMatcher.resolve(ENTRIES, input);
    }

    private static CatalogTaste taste(String key, String name, String... aliases) {
        return new CatalogTaste("taste." + key, name, List.of(aliases));
    }
}
