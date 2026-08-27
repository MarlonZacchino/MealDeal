package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.Taste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteTasteRepositoryIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    private SqliteTasteRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SqliteTasteRepository(
                new SqliteDatabase(temporaryDirectory.resolve("tastes.db")));
    }

    @Test
    void savesLoadsAndUpdatesTaste() {
        UUID id = UUID.randomUUID();
        repository.save(new Taste(id, "Mild"));
        repository.save(new Taste(id, "Very mild"));

        Taste loaded = repository.findById(id).orElseThrow();

        assertEquals(id, loaded.getId());
        assertEquals("Very mild", loaded.getName());
    }

    @Test
    void loadsAllTastesInStableOrder() {
        repository.save(new Taste("Sweet"));
        repository.save(new Taste("Fresh"));

        assertEquals(java.util.List.of("Fresh", "Sweet"), repository.findAll().stream()
                .map(Taste::getName).toList());
    }

    @Test
    void deletesTasteAndReportsUnknownId() {
        Taste taste = new Taste("Fresh");
        repository.save(taste);

        assertTrue(repository.deleteById(taste.getId()));
        assertFalse(repository.findById(taste.getId()).isPresent());
        assertFalse(repository.deleteById(UUID.randomUUID()));
        assertFalse(repository.findById(UUID.randomUUID()).isPresent());
    }
}
