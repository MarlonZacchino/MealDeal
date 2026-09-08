package de.mealdeal.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TasteTest {

    @Test
    void createsTasteWithValidName() {
        Taste taste = new Taste("  Savory  ");

        assertEquals("Savory", taste.getName());
    }

    @Test
    void rejectsEmptyName() {
        assertThrows(IllegalArgumentException.class, () -> new Taste(""));
    }

    @Test
    void rejectsWhitespaceOnlyName() {
        assertThrows(IllegalArgumentException.class, () -> new Taste("   "));
    }

    @Test
    void equalityUsesStableIdentity() {
        UUID id = UUID.randomUUID();

        assertEquals(new Taste(id, "Mild"), new Taste(id, "Fresh"));
    }

    @Test
    void optionalCatalogLinkKeepsUserDefinedTasteCompatible() {
        UUID id = UUID.randomUUID();
        Taste linked = new Taste(id, "Meine Süße", "taste.sweet");

        assertEquals(id, linked.getId());
        assertEquals("taste.sweet", linked.getCatalogId().orElseThrow());
        assertTrue(new Taste("Eigener Geschmack").getCatalogId().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new Taste("Süß", " "));
    }
}
