package de.mealdeal.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
