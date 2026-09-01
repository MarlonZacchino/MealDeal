package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.IngredientCategories;
import de.mealdeal.domain.IngredientCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteIngredientCategoryRepositoryIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsStartCategoriesInPositionOrder() {
        var repository = repository("order.db");

        assertEquals(List.of("Obst", "Gemüse", "Fleisch", "Fisch & Meeresfrüchte",
                        "Milchprodukte", "Eier", "Getreide, Reis & Nudeln",
                        "Hülsenfrüchte", "Kräuter & Gewürze", "Backzutaten",
                        "Öle, Essig & Saucen", "Nüsse & Samen", "Tiefkühlprodukte",
                        "Getränke", "Sonstiges"),
                repository.findAll().stream().map(IngredientCategory::getName).toList());
        assertEquals(java.util.stream.IntStream.range(0, 15).boxed().toList(),
                repository.findAll().stream().map(IngredientCategory::getPosition).toList());
    }

    @Test
    void savesAndLoadsCategoryRoundtrip() {
        var repository = repository("roundtrip.db");
        IngredientCategory category = new IngredientCategory("Fermentiertes", 20);

        repository.save(category);
        IngredientCategory loaded = repository.findById(category.getId()).orElseThrow();

        assertEquals(category, loaded);
        assertEquals("Fermentiertes", loaded.getName());
        assertEquals(20, loaded.getPosition());
        assertEquals(IngredientCategories.OTHER,
                repository.findById(IngredientCategories.OTHER.getId()).orElseThrow());
    }

    private SqliteIngredientCategoryRepository repository(String fileName) {
        return new SqliteIngredientCategoryRepository(
                new SqliteDatabase(temporaryDirectory.resolve(fileName)));
    }
}
