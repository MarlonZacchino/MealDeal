package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.Ingredient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteIngredientRepositoryIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    private SqliteIngredientRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SqliteIngredientRepository(
                new SqliteDatabase(temporaryDirectory.resolve("ingredients.db")));
    }

    @Test
    void savesLoadsAndUpdatesIngredient() {
        UUID id = UUID.randomUUID();
        repository.save(new Ingredient(id, "Tomato"));
        repository.save(new Ingredient(id, "Cherry tomato"));

        Ingredient loaded = repository.findById(id).orElseThrow();

        assertEquals(id, loaded.getId());
        assertEquals("Cherry tomato", loaded.getName());
    }

    @Test
    void loadsAllIngredientsInStableOrder() {
        repository.save(new Ingredient("Zucchini"));
        repository.save(new Ingredient("Apple"));

        assertEquals(java.util.List.of("Apple", "Zucchini"), repository.findAll().stream()
                .map(Ingredient::getName).toList());
    }

    @Test
    void deletesIngredientAndReportsUnknownId() {
        Ingredient ingredient = new Ingredient("Tomato");
        repository.save(ingredient);

        assertTrue(repository.deleteById(ingredient.getId()));
        assertFalse(repository.findById(ingredient.getId()).isPresent());
        assertFalse(repository.deleteById(UUID.randomUUID()));
        assertFalse(repository.findById(UUID.randomUUID()).isPresent());
    }
}
