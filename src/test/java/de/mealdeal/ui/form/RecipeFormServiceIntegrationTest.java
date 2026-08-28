package de.mealdeal.ui.form;

import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.sqlite.SqliteDatabase;
import de.mealdeal.persistence.sqlite.SqliteIngredientRepository;
import de.mealdeal.persistence.sqlite.SqliteRecipeRepository;
import de.mealdeal.persistence.sqlite.SqliteTasteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecipeFormServiceIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsNewCentralDataAndCompleteRecipeInSqlite() {
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("form.db"));
        var ingredients = new SqliteIngredientRepository(database);
        var tastes = new SqliteTasteRepository(database);
        var recipes = new SqliteRecipeRepository(database);
        RecipeFormService service = new RecipeFormService(recipes, ingredients, tastes);

        service.createAndSave(new RecipeFormInput("Kartoffelpfanne", "3",
                List.of(new IngredientFormInput("Kartoffel", "750", Unit.GRAM)),
                List.of("Herzhaft"), List.of("Schneiden.", "Braten.")));

        assertEquals(List.of("Kartoffel"), ingredients.findAll().stream()
                .map(value -> value.getName()).toList());
        assertEquals(List.of("Herzhaft"), tastes.findAll().stream()
                .map(value -> value.getName()).toList());
        assertEquals("Kartoffelpfanne", recipes.findAll().getFirst().getName());
        assertEquals(2, recipes.findAll().getFirst().getSteps().size());
    }
}
