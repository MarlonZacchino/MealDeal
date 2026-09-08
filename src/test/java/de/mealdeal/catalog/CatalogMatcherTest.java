package de.mealdeal.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogMatcherTest {

    @Test
    void followsTheDocumentedIngredientMatchPriority() {
        assertMatch("ingredient.tomato", CatalogMatchType.CATALOG_ID, "ingredient.tomato");
        assertMatch("Tomate", CatalogMatchType.EXACT_CANONICAL_NAME, "ingredient.tomato");
        assertMatch("Tomaten", CatalogMatchType.EXACT_ALIAS, "ingredient.tomato");
        assertMatch(" TOMATE ", CatalogMatchType.NORMALIZED_CANONICAL_NAME,
                "ingredient.tomato");
        assertMatch(" TOMATEN ", CatalogMatchType.NORMALIZED_ALIAS, "ingredient.tomato");
        assertMatch("Kaese", CatalogMatchType.NORMALIZED_CANONICAL_NAME, "ingredient.cheese");
    }

    @Test
    void resolvesTasteTransliterationButNotUnrelatedTerms() {
        CatalogMatch<CatalogTaste> sweet = StandardTasteCatalog.resolve("Suess");

        assertEquals(CatalogMatchType.NORMALIZED_CANONICAL_NAME, sweet.type());
        assertEquals("taste.sweet", sweet.matchedEntry().orElseThrow().catalogId());
        assertTrue(StandardIngredientCatalog.resolve("Tomatenmark extra").matchedEntry().isEmpty());
        assertTrue(StandardIngredientCatalog.resolve("Kokosmilch").matchedEntry().isEmpty());
        assertTrue(StandardIngredientCatalog.resolve("Erdnussbutter").matchedEntry().isEmpty());
        assertTrue(StandardIngredientCatalog.resolve("Chilipulver").matchedEntry().isEmpty());
    }

    @Test
    void ambiguousTermNeverBecomesAnIdentity() {
        List<CatalogTaste> entries = List.of(
                new CatalogTaste("taste.first", "Erster", List.of("Gemeinsam")),
                new CatalogTaste("taste.second", "Zweiter", List.of("Gemeinsam")));

        CatalogMatch<CatalogTaste> result = CatalogMatcher.resolve(entries, "Gemeinsam");

        assertEquals(CatalogMatchType.AMBIGUOUS, result.type());
        assertEquals(List.of("taste.first", "taste.second"), result.candidates().stream()
                .map(CatalogTaste::catalogId).toList());
        assertTrue(result.matchedEntry().isEmpty());
    }

    private static void assertMatch(String input, CatalogMatchType type, String catalogId) {
        CatalogMatch<CatalogIngredient> result = StandardIngredientCatalog.resolve(input);
        assertEquals(type, result.type());
        assertEquals(catalogId, result.matchedEntry().orElseThrow().catalogId());
    }
}
