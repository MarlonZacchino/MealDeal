package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.MealHistoryEntry;
import de.mealdeal.domain.MealHistorySource;
import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredient;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import de.mealdeal.service.MealHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteMealHistoryRepositoryIntegrationTest {

    @TempDir Path temporaryDirectory;

    private SqliteDatabase database;
    private SqliteMealHistoryRepository historyRepository;
    private SqliteRecipeRepository recipeRepository;
    private Recipe recipe;

    @BeforeEach
    void setUp() {
        database = new SqliteDatabase(temporaryDirectory.resolve("history.db"));
        historyRepository = new SqliteMealHistoryRepository(database);
        recipeRepository = new SqliteRecipeRepository(database);
        Ingredient ingredient = new Ingredient("Pasta");
        Taste taste = new Taste("Herzhaft");
        new SqliteIngredientRepository(database).save(ingredient);
        new SqliteTasteRepository(database).save(taste);
        recipe = new Recipe("Carbonara", 2,
                List.of(new RecipeIngredient(ingredient, new BigDecimal("250"), Unit.GRAM)),
                List.of(), List.of(taste));
        recipeRepository.save(recipe);
    }

    @Test
    void savesReloadsDeletesAndSupportsRecommendationQueries() {
        Instant firstTime = Instant.parse("2026-08-20T18:00:00Z");
        Instant secondTime = Instant.parse("2026-09-01T19:00:00Z");
        MealHistoryEntry first = event(firstTime, 2, MealHistorySource.MANUAL);
        MealHistoryEntry second = event(secondTime, 4, MealHistorySource.RECOMMENDATION);
        MealHistoryEntry otherRecipe = new MealHistoryEntry(UUID.randomUUID(), "Other",
                Instant.parse("2026-09-02T19:00:00Z"), 1, MealHistorySource.MANUAL,
                null, Instant.parse("2026-09-02T20:00:00Z"));

        historyRepository.save(first);
        historyRepository.save(second);
        historyRepository.save(otherRecipe);

        MealHistoryEntry loaded = historyRepository.findById(second.getId()).orElseThrow();
        assertEquals(second.getId(), loaded.getId());
        assertEquals(recipe.getId(), loaded.getRecipeId());
        assertEquals("Carbonara", loaded.getRecipeName());
        assertEquals(4, loaded.getServings());
        assertEquals(MealHistorySource.RECOMMENDATION, loaded.getSource());
        assertEquals(List.of(otherRecipe.getId(), second.getId()), historyRepository.findRecent(2)
                .stream().map(MealHistoryEntry::getId).toList());
        assertEquals(List.of(second.getId(), first.getId()),
                historyRepository.findByRecipeId(recipe.getId()).stream()
                        .map(MealHistoryEntry::getId).toList());
        assertEquals(second.getId(), historyRepository.findLatestByRecipeId(recipe.getId())
                .orElseThrow().getId());
        var latestBatch = historyRepository.findLatestByRecipeIds(List.of(
                otherRecipe.getRecipeId(), recipe.getId(), UUID.randomUUID()));
        assertEquals(second.getId(), latestBatch.get(recipe.getId()).getId());
        assertEquals(otherRecipe.getId(),
                latestBatch.get(otherRecipe.getRecipeId()).getId());
        assertEquals(2, latestBatch.size());
        assertEquals(List.of(otherRecipe.getId(), second.getId()), historyRepository.findBetween(
                        Instant.parse("2026-09-01T00:00:00Z"),
                        Instant.parse("2026-09-03T00:00:00Z"))
                .stream().map(MealHistoryEntry::getId).toList());
        assertEquals(1, historyRepository.countByRecipeIdBetween(recipe.getId(),
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-02T00:00:00Z")));
        assertTrue(historyRepository.existsByRecipeId(recipe.getId()));

        assertTrue(historyRepository.deleteById(first.getId()));
        assertFalse(historyRepository.deleteById(first.getId()));
        assertEquals(1, historyRepository.findByRecipeId(recipe.getId()).size());
    }

    @Test
    void mealPlanConfirmationIsIdempotentWhileManualEventsMayRepeat() {
        Instant now = Instant.parse("2026-09-02T20:00:00Z");
        MealPlanEntry planEntry = new MealPlanEntry(
                LocalDate.of(2026, 9, 2), recipe, 3);
        new SqliteMealPlanRepository(database).save(planEntry);
        MealHistoryService service = new MealHistoryService(historyRepository,
                Clock.fixed(Instant.parse("2026-09-02T20:05:00Z"), ZoneOffset.UTC));

        MealHistoryEntry firstConfirmation = service.confirmCooked(planEntry, now);
        MealHistoryEntry secondConfirmation = service.confirmCooked(planEntry, now.plusSeconds(5));
        service.recordManual(recipe, 3, now);
        service.recordManual(recipe, 3, now);
        MealHistoryEntry recommendation = service.recordRecommendation(recipe, 2, now);
        historyRepository.save(new MealHistoryEntry(recipe.getId(), recipe.getName(),
                now.plusSeconds(10), 9, MealHistorySource.MEAL_PLAN, planEntry.getId(),
                now.plusSeconds(11)));

        assertEquals(firstConfirmation.getId(), secondConfirmation.getId());
        assertEquals(4, historyRepository.findByRecipeId(recipe.getId()).size());
        assertEquals(planEntry.getId(), firstConfirmation.getSourceMealPlanEntryId().orElseThrow());
        assertEquals(3, firstConfirmation.getServings());
        assertEquals(MealHistorySource.RECOMMENDATION, recommendation.getSource());
    }

    @Test
    void historySurvivesRecipeDeletionWithItsNameSnapshot() {
        MealHistoryEntry event = event(
                Instant.parse("2026-09-01T19:00:00Z"), 2, MealHistorySource.MANUAL);
        historyRepository.save(event);

        assertTrue(recipeRepository.deleteById(recipe.getId()));

        MealHistoryEntry retained = historyRepository.findById(event.getId()).orElseThrow();
        assertEquals(recipe.getId(), retained.getRecipeId());
        assertEquals("Carbonara", retained.getRecipeName());
    }

    @Test
    void chronologicalQueriesRemainCorrectForSubSecondInstants() {
        MealHistoryEntry earlier = event(
                Instant.parse("2026-09-01T19:00:00.100000000Z"),
                1, MealHistorySource.MANUAL);
        MealHistoryEntry later = event(
                Instant.parse("2026-09-01T19:00:00.900000000Z"),
                1, MealHistorySource.MANUAL);
        historyRepository.save(earlier);
        historyRepository.save(later);

        assertEquals(List.of(later.getId(), earlier.getId()), historyRepository.findRecent(2)
                .stream().map(MealHistoryEntry::getId).toList());
        assertEquals(1, historyRepository.countByRecipeIdBetween(recipe.getId(),
                Instant.parse("2026-09-01T19:00:00.500000000Z"),
                Instant.parse("2026-09-01T19:00:01Z")));
    }

    @Test
    void latestBatchUsesOccurredAtRatherThanLaterCreationTimestamp() {
        MealHistoryEntry cookedLater = new MealHistoryEntry(
                recipe.getId(), recipe.getName(), Instant.parse("2026-09-02T19:00:00Z"),
                2, MealHistorySource.MANUAL, null,
                Instant.parse("2026-09-02T19:01:00Z"));
        MealHistoryEntry recordedLaterButCookedEarlier = new MealHistoryEntry(
                recipe.getId(), recipe.getName(), Instant.parse("2026-09-01T19:00:00Z"),
                2, MealHistorySource.MANUAL, null,
                Instant.parse("2026-09-03T19:01:00Z"));
        historyRepository.save(cookedLater);
        historyRepository.save(recordedLaterButCookedEarlier);

        MealHistoryEntry latest = historyRepository.findLatestByRecipeIds(List.of(recipe.getId()))
                .get(recipe.getId());

        assertEquals(cookedLater.getId(), latest.getId());
    }

    @Test
    void latestBatchUsesOccurredAtInsteadOfCreatedAt() {
        MealHistoryEntry actuallyLatest = new MealHistoryEntry(
                recipe.getId(), recipe.getName(), Instant.parse("2026-09-03T12:00:00Z"),
                2, MealHistorySource.MANUAL, null,
                Instant.parse("2026-09-03T12:01:00Z"));
        MealHistoryEntry recordedLater = new MealHistoryEntry(
                recipe.getId(), recipe.getName(), Instant.parse("2026-09-02T12:00:00Z"),
                2, MealHistorySource.MANUAL, null,
                Instant.parse("2026-09-04T12:01:00Z"));
        historyRepository.save(actuallyLatest);
        historyRepository.save(recordedLater);

        assertEquals(actuallyLatest.getId(), historyRepository.findLatestByRecipeIds(
                List.of(recipe.getId())).get(recipe.getId()).getId());
    }

    @Test
    void migratesVersionFifteenWithoutChangingExistingData() throws Exception {
        Path path = temporaryDirectory.resolve("version-15.db");
        UUID ingredientId = UUID.randomUUID();
        UUID tasteId = UUID.randomUUID();
        UUID recipeId = UUID.randomUUID();
        try (var connection = java.sql.DriverManager.getConnection(
                "jdbc:sqlite:" + path.toAbsolutePath())) {
            connection.setAutoCommit(false);
            SqliteSchema.createVersion1(connection);
            SqliteSchema.createVersion2(connection);
            SqliteSchema.createVersion3(connection);
            SqliteSchema.createVersion4(connection);
            SqliteSchema.createVersion5(connection);
            SqliteSchema.createVersion6(connection);
            SqliteSchema.createVersion7(connection);
            SqliteSchema.createVersion8(connection);
            SqliteSchema.createVersion9(connection);
            SqliteSchema.createVersion10(connection);
            SqliteSchema.createVersion11(connection);
            SqliteSchema.createVersion12(connection);
            SqliteSchema.createVersion13(connection);
            SqliteSchema.createVersion14(connection);
            SqliteSchema.createVersion15(connection);
            insert(connection,
                    "INSERT INTO ingredients (id, name, category_id, catalog_id) "
                            + "VALUES (?, ?, ?, ?)",
                    ingredientId, "Tomate", de.mealdeal.domain.IngredientCategories.VEGETABLES
                            .getId(), "ingredient.tomato");
            insert(connection, "INSERT INTO tastes (id, name, catalog_id) VALUES (?, ?, ?)",
                    tasteId, "Herzhaft", "taste.savory");
            insert(connection,
                    "INSERT INTO recipes (id, name, standard_serving_count, dish_type) "
                            + "VALUES (?, ?, ?, ?)", recipeId, "Tomatengericht", 2, "MAIN");
            insert(connection, "INSERT INTO recipe_tastes (recipe_id, taste_id) VALUES (?, ?)",
                    recipeId, tasteId);
            connection.commit();
        }

        SqliteDatabase migrated = new SqliteDatabase(path);

        assertEquals(16, migrated.getSchemaVersion());
        Recipe loaded = new SqliteRecipeRepository(migrated).findById(recipeId).orElseThrow();
        assertEquals("Tomatengericht", loaded.getName());
        assertEquals("ingredient.tomato", new SqliteIngredientRepository(migrated)
                .findById(ingredientId).orElseThrow().getCatalogId().orElseThrow());
        assertEquals("taste.savory", new SqliteTasteRepository(migrated)
                .findById(tasteId).orElseThrow().getCatalogId().orElseThrow());
        assertTrue(new SqliteMealHistoryRepository(migrated).findRecent(1).isEmpty());
    }

    private MealHistoryEntry event(Instant occurredAt, int servings, MealHistorySource source) {
        return new MealHistoryEntry(recipe.getId(), recipe.getName(), occurredAt, servings,
                source, null, occurredAt.plusSeconds(60));
    }

    private static void insert(java.sql.Connection connection, String sql, Object... values)
            throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index].toString());
            }
            statement.executeUpdate();
        }
    }
}
