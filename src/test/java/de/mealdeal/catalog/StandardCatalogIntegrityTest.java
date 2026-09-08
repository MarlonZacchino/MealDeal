package de.mealdeal.catalog;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardCatalogIntegrityTest {

    @Test
    void ingredientCatalogHasStableUniqueAndValidContent() {
        var entries = StandardIngredientCatalog.all();
        Set<String> ids = new HashSet<>();

        assertTrue(entries.size() >= 40 && entries.size() <= 80);
        for (CatalogIngredient entry : entries) {
            assertTrue(entry.catalogId().matches("ingredient\\.[a-z0-9_]+"));
            assertTrue(ids.add(entry.catalogId()));
            assertFalse(entry.displayName().isBlank());
            assertFalse(entry.typicalUnits().isEmpty());
            assertTrue(entry.aliases().stream().noneMatch(String::isBlank));
            assertTrue(Set.of(CatalogIngredientCategory.values()).contains(entry.category()));
        }
        assertEquals(entries.size(), ids.size());
        assertThrows(UnsupportedOperationException.class, entries::clear);
    }

    @Test
    void tasteCatalogHasStableUniqueAndValidContent() {
        var entries = StandardTasteCatalog.all();
        Set<String> ids = new HashSet<>();

        for (CatalogTaste entry : entries) {
            assertTrue(entry.catalogId().matches("taste\\.[a-z0-9_]+"));
            assertTrue(ids.add(entry.catalogId()));
            assertFalse(entry.displayName().isBlank());
            assertTrue(entry.aliases().stream().noneMatch(String::isBlank));
        }
        assertEquals(10, entries.size());
        assertThrows(UnsupportedOperationException.class, entries::clear);
    }

    @Test
    void everyDeclaredTermResolvesUnambiguouslyToItsOwnEntry() {
        for (CatalogIngredient entry : StandardIngredientCatalog.all()) {
            assertEquals(entry, StandardIngredientCatalog.resolve(entry.displayName())
                    .matchedEntry().orElseThrow());
            for (String alias : entry.aliases()) {
                assertEquals(entry, StandardIngredientCatalog.resolve(alias)
                        .matchedEntry().orElseThrow());
            }
        }
        for (CatalogTaste entry : StandardTasteCatalog.all()) {
            assertEquals(entry, StandardTasteCatalog.resolve(entry.displayName())
                    .matchedEntry().orElseThrow());
            for (String alias : entry.aliases()) {
                assertEquals(entry, StandardTasteCatalog.resolve(alias)
                        .matchedEntry().orElseThrow());
            }
        }
    }
}
