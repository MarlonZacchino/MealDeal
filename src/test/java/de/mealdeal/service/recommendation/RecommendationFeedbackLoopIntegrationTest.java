package de.mealdeal.service.recommendation;

import de.mealdeal.domain.MealHistorySource;
import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.persistence.sqlite.SqliteMealPlanRepository;
import de.mealdeal.persistence.sqlite.SqliteInventoryConsumptionRepository;
import de.mealdeal.service.InventoryConsumptionService;
import de.mealdeal.service.RecipeScaler;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeFeedbackValue;
import de.mealdeal.domain.RecommendationAction;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.sqlite.SqliteDatabase;
import de.mealdeal.persistence.sqlite.SqliteIngredientRepository;
import de.mealdeal.persistence.sqlite.SqliteInventoryRepository;
import de.mealdeal.persistence.sqlite.SqliteMealHistoryRepository;
import de.mealdeal.persistence.sqlite.SqliteRecipeFeedbackRepository;
import de.mealdeal.persistence.sqlite.SqliteRecipeRepository;
import de.mealdeal.persistence.sqlite.SqliteRecommendationInteractionRepository;
import de.mealdeal.persistence.sqlite.SqliteTasteRepository;
import de.mealdeal.service.MealHistoryService;
import de.mealdeal.service.RecipeFeedbackService;
import de.mealdeal.service.RecommendationInteractionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.mealdeal.service.recommendation.RecommendationTestFixtures.group;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.ingredient;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.option;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.recipe;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.stock;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.taste;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationFeedbackLoopIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void directRecommendationAfterDayChangeConsumesPastPlanExactlyOnceBeforeScoring() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-08T12:00:00Z"));
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("day-change.db"));
        var tomato = ingredient("Day Tomate");
        var savory = taste("Day Herzhaft");
        Recipe candidate = recipe("day", "Day Gericht", List.of(group("day",
                List.of(option("day", tomato, "100", Unit.GRAM, 0)), 0)), savory);
        new SqliteIngredientRepository(database).save(tomato);
        new SqliteTasteRepository(database).save(savory);
        new SqliteRecipeRepository(database).save(candidate);
        var inventory = new SqliteInventoryRepository(database);
        var stock = stock("day", tomato, "150", Unit.GRAM);
        inventory.save(stock);
        var plan = new MealPlanEntry(LocalDate.of(2026, 9, 8), candidate, 2);
        new SqliteMealPlanRepository(database).save(plan);
        var ledger = new SqliteInventoryConsumptionRepository(database);
        var workflow = workflow(database, clock);
        var today = workflow.start(2, Optional.empty(), List.of());
        assertEquals(0, ledger.findAll().size());
        assertEquals(0, pantry(today).compareTo(BigDecimal.ONE));

        clock.now = Instant.parse("2026-09-09T00:01:00Z");
        var tomorrow = workflow.start(2, Optional.empty(), List.of());

        assertEquals(0, inventory.findById(stock.getId()).orElseThrow().getQuantity()
                .compareTo(new BigDecimal("50")));
        assertEquals(0, pantry(tomorrow).compareTo(new BigDecimal("0.5")));
        assertEquals(1, ledger.findAll().size());
        assertEquals(plan.getId(), ledger.findAll().getFirst().getMealPlanEntryId());
        assertEquals(clock.instant(), ledger.findAll().getFirst().getProcessedAt());

        var repeated = workflow.start(2, Optional.empty(), List.of());
        assertEquals(pantry(tomorrow), pantry(repeated));
        assertEquals(0, inventory.findById(stock.getId()).orElseThrow().getQuantity()
                .compareTo(new BigDecimal("50")));
        assertEquals(1, ledger.findAll().size());
        assertFalse(new SqliteMealHistoryRepository(database).existsByRecipeId(candidate.getId()));
    }

    private static BigDecimal pantry(RecommendationSession session) {
        return session.recommendations().getFirst().signals()
                .valueOf(RecommendationSignal.PANTRY_COVERAGE).orElseThrow();
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        private MutableClock(Instant now) { this.now = now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }

    @Test
    void persistedDismissalIsOnlyPreferenceWhenWorkflowAndSessionAreRecreated() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-08T12:00:00Z"), ZoneOffset.UTC);
        Path databasePath = temporaryDirectory.resolve("dismiss.db");
        SqliteDatabase database = new SqliteDatabase(databasePath);
        var tomato = ingredient("Dismiss Tomate");
        var savory = taste("Dismiss Herzhaft");
        Recipe candidate = recipe("dismiss", "Dismiss Gericht", List.of(group("dismiss",
                List.of(option("dismiss", tomato, "100", Unit.GRAM, 0)), 0)), savory);
        new SqliteIngredientRepository(database).save(tomato);
        new SqliteTasteRepository(database).save(savory);
        new SqliteRecipeRepository(database).save(candidate);
        RecommendationWorkflowService firstWorkflow = workflow(database, clock);
        RecommendationSession first = firstWorkflow.start(2, Optional.empty(), List.of());
        firstWorkflow.recordShown(first);
        firstWorkflow.dismiss(first, candidate.getId());
        assertTrue(first.visibleRecommendations().isEmpty());

        SqliteDatabase reopened = new SqliteDatabase(databasePath);
        RecommendationWorkflowService nextWorkflow = workflow(reopened, clock);
        RecommendationSession second = nextWorkflow.start(2, Optional.empty(), List.of());
        nextWorkflow.recordShown(second);

        assertNotEquals(first.id(), second.id());
        assertEquals(candidate.getId(), second.visibleRecommendations().getFirst().recipe().getId());
        assertTrue(second.recommendations().getFirst().score()
                .compareTo(first.recommendations().getFirst().score()) < 0);
        var events = new SqliteRecommendationInteractionRepository(reopened);
        assertEquals(2, events.findBySessionId(first.id()).size());
        assertEquals(RecommendationAction.SHOWN,
                events.findBySessionId(second.id()).getFirst().getAction());
        assertEquals(1, events.findBySessionId(second.id()).size());
        assertTrue(new SqliteRecipeFeedbackRepository(reopened)
                .findByRecipeId(candidate.getId()).isEmpty());
        assertFalse(new SqliteMealHistoryRepository(reopened).existsByRecipeId(candidate.getId()));
    }

    @Test
    void cookedRecipeDisappearsImmediatelyAndReturnsAfterLocalDayChange() {
        Instant now = Instant.parse("2026-09-08T12:00:00Z");
        MutableClock clock = new MutableClock(now);
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("mealdeal.db"));
        var ingredientRepository = new SqliteIngredientRepository(database);
        var tasteRepository = new SqliteTasteRepository(database);
        var recipeRepository = new SqliteRecipeRepository(database);
        var inventoryRepository = new SqliteInventoryRepository(database);
        var historyRepository = new SqliteMealHistoryRepository(database);
        var feedbackRepository = new SqliteRecipeFeedbackRepository(database);
        var interactionRepository = new SqliteRecommendationInteractionRepository(database);

        var tomato = ingredient("R5 Tomate");
        var savory = taste("R5 Herzhaft");
        Recipe firstRecipe = recipe("r5-a", "R5 Gericht A",
                List.of(group("r5-a", List.of(
                        option("r5-a", tomato, "100", Unit.GRAM, 0)), 0)), savory);
        Recipe secondRecipe = recipe("r5-b", "R5 Gericht B",
                List.of(group("r5-b", List.of(
                        option("r5-b", tomato, "100", Unit.GRAM, 0)), 0)), savory);
        ingredientRepository.save(tomato);
        tasteRepository.save(savory);
        recipeRepository.save(firstRecipe);
        recipeRepository.save(secondRecipe);
        var inventory = stock("r5", tomato, "1000", Unit.GRAM);
        inventoryRepository.save(inventory);

        RecommendationWorkflowService firstWorkflow = workflow(database, clock);
        RecommendationSession firstSession = firstWorkflow.start(
                3, Optional.empty(), List.of());
        Recipe chosen = firstSession.recommendations().getFirst().recipe();
        historyRepository.save(new de.mealdeal.domain.MealHistoryEntry(
                chosen.getId(), chosen.getName(), now.minusSeconds(86_400), 2,
                MealHistorySource.MANUAL, null, now.minusSeconds(60)));
        firstWorkflow.recordShown(firstSession);
        firstWorkflow.select(firstSession, chosen.getId());
        firstWorkflow.like(firstSession, chosen.getId());
        firstWorkflow.recordCooked(firstSession, chosen.getId(), 3);
        UUID originalSessionId = firstSession.id();
        firstWorkflow.recordShown(firstSession);

        assertEquals(2, interactionRepository.findBySessionId(firstSession.id()).stream()
                .filter(value -> value.getAction() == RecommendationAction.SHOWN).count());
        assertEquals(1, interactionRepository.findBySessionId(firstSession.id()).stream()
                .filter(value -> value.getAction() == RecommendationAction.SELECTED).count());
        assertEquals(RecipeFeedbackValue.LIKE,
                feedbackRepository.findByRecipeId(chosen.getId()).orElseThrow()
                        .getValue().orElseThrow());
        var history = historyRepository.findByRecipeId(chosen.getId());
        assertEquals(2, history.size());
        var latest = historyRepository.findLatestByRecipeId(chosen.getId()).orElseThrow();
        assertEquals(MealHistorySource.RECOMMENDATION, latest.getSource());
        assertEquals(3, latest.getServings());
        assertEquals(now, latest.getOccurredAt());
        assertEquals(new BigDecimal("1000"), inventoryRepository.findById(inventory.getId())
                .orElseThrow().getQuantity());
        assertEquals(originalSessionId, firstSession.id());
        assertFalse(firstSession.visibleRecommendations().stream()
                .anyMatch(result -> result.recipe().getId().equals(chosen.getId())));
        assertEquals(0, interactionRepository.findBySessionId(firstSession.id()).stream()
                .filter(value -> value.getAction() == RecommendationAction.DISMISSED).count());
        assertEquals(2, interactionRepository.findBySessionId(firstSession.id()).stream()
                .filter(value -> value.getAction() == RecommendationAction.SHOWN).count());

        RecommendationSession secondSession = workflow(database, clock).start(
                3, Optional.empty(), List.of());

        assertEquals(RecipeFeedbackValue.LIKE,
                feedbackRepository.findByRecipeId(chosen.getId()).orElseThrow()
                        .getValue().orElseThrow());
        assertTrue(secondSession.recommendationFor(chosen.getId()).isEmpty());
        assertTrue(secondSession.recommendations().stream()
                .anyMatch(result -> !result.recipe().getId().equals(chosen.getId())));

        clock.now = Instant.parse("2026-09-09T00:01:00Z");
        RecommendationSession nextDay = workflow(database, clock).start(
                3, Optional.empty(), List.of());
        var returned = nextDay.recommendationFor(chosen.getId()).orElseThrow();
        assertTrue(returned.reasonCodes().contains(RecommendationReasonCode.RECENTLY_COOKED));
        assertTrue(returned.reasonCodes().stream()
                .anyMatch(code -> code == RecommendationReasonCode.RECIPE_EXPLICITLY_LIKED
                        || code == RecommendationReasonCode.RECIPE_STRONGLY_LIKED));
    }

    private static RecommendationWorkflowService workflow(SqliteDatabase database, Clock clock) {
        var historyRepository = new SqliteMealHistoryRepository(database);
        var feedbackRepository = new SqliteRecipeFeedbackRepository(database);
        var interactionRepository = new SqliteRecommendationInteractionRepository(database);
        return new RecommendationWorkflowService(
                new SqliteRecipeRepository(database), new SqliteInventoryRepository(database),
                new PersonalizedRecommendationService(
                        new RecommendationPersonalizationService(
                                historyRepository, feedbackRepository,
                                interactionRepository, clock),
                        new RecipeRecommendationService()),
                new RecommendationInteractionService(interactionRepository, clock),
                new RecipeFeedbackService(feedbackRepository, clock),
                new MealHistoryService(historyRepository, clock),
                new InventoryConsumptionService(new SqliteMealPlanRepository(database),
                        new SqliteInventoryRepository(database),
                        new SqliteInventoryConsumptionRepository(database), new RecipeScaler(), clock),
                clock, UUID::randomUUID);
    }
}
