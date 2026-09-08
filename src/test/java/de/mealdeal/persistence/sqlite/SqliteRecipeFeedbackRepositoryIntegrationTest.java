package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeFeedback;
import de.mealdeal.domain.RecipeFeedbackValue;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.service.RecipeFeedbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteRecipeFeedbackRepositoryIntegrationTest {

    @TempDir Path temporaryDirectory;

    private SqliteDatabase database;
    private SqliteRecipeRepository recipeRepository;
    private SqliteRecipeFeedbackRepository feedbackRepository;
    private Recipe recipe;

    @BeforeEach
    void setUp() {
        database = new SqliteDatabase(temporaryDirectory.resolve("feedback.db"));
        recipeRepository = new SqliteRecipeRepository(database);
        feedbackRepository = new SqliteRecipeFeedbackRepository(database);
        Ingredient ingredient = new Ingredient("Kartoffel");
        Taste taste = new Taste("Herzhaft");
        new SqliteIngredientRepository(database).save(ingredient);
        new SqliteTasteRepository(database).save(taste);
        recipe = new Recipe("Kartoffelgericht", 2,
                List.of(new RecipeIngredient(ingredient, BigDecimal.ONE, Unit.PIECE)),
                List.of(), List.of(taste));
        recipeRepository.save(recipe);
    }

    @Test
    void savesUpdatesAndClearsOneCurrentFeedbackWithStableIdentity() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-03T10:00:00Z"), ZoneOffset.UTC);
        RecipeFeedbackService service = new RecipeFeedbackService(feedbackRepository, clock);

        RecipeFeedback liked = service.update(recipe.getId(),
                Optional.of(RecipeFeedbackValue.LIKE), OptionalInt.of(5)).orElseThrow();
        RecipeFeedback updated = service.update(recipe.getId(),
                Optional.of(RecipeFeedbackValue.DISLIKE), OptionalInt.of(1)).orElseThrow();

        assertEquals(liked.getId(), updated.getId());
        RecipeFeedback loaded = service.findByRecipeId(recipe.getId()).orElseThrow();
        assertEquals(RecipeFeedbackValue.DISLIKE, loaded.getValue().orElseThrow());
        assertEquals(1, loaded.getRating().orElseThrow());
        assertTrue(service.update(recipe.getId(), Optional.empty(), OptionalInt.empty()).isEmpty());
        assertTrue(service.findByRecipeId(recipe.getId()).isEmpty());
    }

    @Test
    void supportsRatingOnlyAndRecipeWithoutFeedbackIsNeutral() {
        RecipeFeedbackService service = new RecipeFeedbackService(feedbackRepository,
                Clock.fixed(Instant.parse("2026-09-03T10:00:00Z"), ZoneOffset.UTC));

        assertTrue(service.findByRecipeId(recipe.getId()).isEmpty());
        RecipeFeedback feedback = service.update(
                recipe.getId(), Optional.empty(), OptionalInt.of(3)).orElseThrow();

        assertTrue(feedback.getValue().isEmpty());
        assertEquals(3, feedbackRepository.findByRecipeId(recipe.getId())
                .orElseThrow().getRating().orElseThrow());
        assertEquals(feedback.getId(), feedbackRepository.findByRecipeIds(List.of(
                UUID.randomUUID(), recipe.getId())).get(recipe.getId()).getId());
        assertTrue(feedbackRepository.findByRecipeIds(List.of()).isEmpty());
    }

    @Test
    void databaseRequiresExistingRecipeAndDeletesCurrentFeedbackWithRecipe() {
        assertThrows(PersistenceException.class, () -> feedbackRepository.save(
                new RecipeFeedback(UUID.randomUUID(), RecipeFeedbackValue.LIKE, null,
                        Instant.parse("2026-09-03T10:00:00Z"))));
        feedbackRepository.save(new RecipeFeedback(recipe.getId(),
                RecipeFeedbackValue.LIKE, 4, Instant.parse("2026-09-03T10:00:00Z")));

        assertTrue(recipeRepository.deleteById(recipe.getId()));

        assertTrue(feedbackRepository.findByRecipeId(recipe.getId()).isEmpty());
    }
}
