package de.mealdeal.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CatalogTextNormalizerTest {

    @Test
    void normalizesCaseWhitespaceAndUnicodeDeterministically() {
        assertEquals("tomate", CatalogTextNormalizer.normalize("  TOMATE  "));
        assertEquals("rote tomate",
                CatalogTextNormalizer.normalize("rote\u00a0 \t Tomate"));
        assertEquals(CatalogTextNormalizer.normalize("Käse"),
                CatalogTextNormalizer.normalize("Kaese"));
        assertEquals(CatalogTextNormalizer.normalize("Süß"),
                CatalogTextNormalizer.normalize("Suess"));
        assertEquals(CatalogTextNormalizer.normalize("Käse"),
                CatalogTextNormalizer.normalize("Ka\u0308se"));
    }

    @Test
    void doesNotCollapseDifferentFoodTerms() {
        assertNotEquals(normalize("Tomate"), normalize("Tomatenmark"));
        assertNotEquals(normalize("Milch"), normalize("Kokosmilch"));
        assertNotEquals(normalize("Butter"), normalize("Erdnussbutter"));
        assertNotEquals(normalize("Chili"), normalize("Chilipulver"));
    }

    private static String normalize(String value) {
        return CatalogTextNormalizer.normalize(value);
    }
}
