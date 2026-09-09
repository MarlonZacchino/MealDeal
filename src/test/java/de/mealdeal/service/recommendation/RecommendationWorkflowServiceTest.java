package de.mealdeal.service.recommendation;

import de.mealdeal.domain.InventoryItem;
import de.mealdeal.domain.MealHistoryEntry;
import de.mealdeal.domain.MealHistorySource;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeFeedback;
import de.mealdeal.domain.RecipeFeedbackValue;
import de.mealdeal.domain.RecommendationAction;
import de.mealdeal.domain.RecommendationInteraction;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.repository.InventoryRepository;
import de.mealdeal.persistence.repository.MealHistoryRepository;
import de.mealdeal.persistence.repository.RecipeFeedbackRepository;
import de.mealdeal.persistence.repository.RecipeRepository;
import de.mealdeal.persistence.repository.RecommendationInteractionRepository;
import de.mealdeal.service.MealHistoryService;
import de.mealdeal.service.InventoryConsumptionService;
import de.mealdeal.persistence.sqlite.SqliteDatabase;
import de.mealdeal.persistence.sqlite.SqliteMealPlanRepository;
import de.mealdeal.persistence.sqlite.SqliteInventoryRepository;
import de.mealdeal.persistence.sqlite.SqliteInventoryConsumptionRepository;
import org.junit.jupiter.api.io.TempDir;
import de.mealdeal.service.RecipeFeedbackService;
import de.mealdeal.service.RecommendationInteractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecommendationWorkflowServiceTest {

    @TempDir java.nio.file.Path temporaryDirectory;

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");
    private final MemoryHistoryRepository history = new MemoryHistoryRepository();
    private final MemoryFeedbackRepository feedback = new MemoryFeedbackRepository();
    private final MemoryInteractionRepository interactions = new MemoryInteractionRepository();
    private List<Recipe> recipes;
    private MemoryRecipeRepository recipeRepository;
    private RecommendationWorkflowService workflow;

    @Test
    void detailFeedbackRefreshPreservesSessionRankingDismissalsAndCookedState() {
        var session = workflow.start(3, Optional.of(Duration.ofMinutes(30)), List.of(taste("Herzhaft")));
        var ranked = session.recommendations();
        UUID chosen = ranked.getFirst().recipe().getId();
        UUID dismissed = ranked.get(2).recipe().getId();
        workflow.recordShown(session);
        workflow.dismiss(session, dismissed);
        workflow.select(session, chosen);
        workflow.recordCooked(session, chosen, 3);
        var feedbackService = new RecipeFeedbackService(feedback, Clock.fixed(NOW, ZoneOffset.UTC));
        feedbackService.update(chosen, Optional.of(RecipeFeedbackValue.DISLIKE), java.util.OptionalInt.empty());
        workflow.refreshFeedback(session);
        workflow.recordShown(session);
        assertEquals(ranked, session.recommendations());
        assertEquals(3, session.visibleRecommendations().size());
        assertFalse(session.visibleRecommendations().stream()
                .anyMatch(result -> result.recipe().getId().equals(chosen)));
        assertTrue(session.isCooked(chosen));
        assertEquals(RecipeFeedbackValue.DISLIKE, session.feedbackFor(chosen).orElseThrow().getValue().orElseThrow());
        assertEquals(5, interactions.findBySessionId(session.id()).stream()
                .filter(event -> event.getAction() == RecommendationAction.SHOWN).count());
        feedbackService.clear(chosen);
        workflow.refreshFeedback(session);
        assertTrue(session.feedbackFor(chosen).isEmpty());
        assertEquals(ranked, session.recommendations());
    }

    @BeforeEach
    void setUp() {
        var ingredient = ingredient("Tomate");
        Taste taste = taste("Herzhaft");
        recipes = java.util.stream.IntStream.range(0, 7)
                .mapToObj(index -> recipe("candidate-" + index, "Gericht " + index,
                        List.of(group("group-" + index,
                                List.of(option("option-" + index, ingredient,
                                        "100", Unit.GRAM, 0)), 0)), taste))
                .toList();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var personalization = new RecommendationPersonalizationService(
                history, feedback, interactions, clock);
        recipeRepository = new MemoryRecipeRepository(recipes);
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("consumption.db"));
        var consumption = new InventoryConsumptionService(new SqliteMealPlanRepository(database),
                new SqliteInventoryRepository(database), new SqliteInventoryConsumptionRepository(database),
                new de.mealdeal.service.RecipeScaler(), clock);
        workflow = new RecommendationWorkflowService(
                recipeRepository,
                new MemoryInventoryRepository(List.of(stock(
                        "tomato", ingredient, "1000", Unit.GRAM))),
                new PersonalizedRecommendationService(
                        personalization, new RecipeRecommendationService()),
                new RecommendationInteractionService(interactions, clock),
                new RecipeFeedbackService(feedback, clock),
                new MealHistoryService(history, clock), consumption, clock, UUID::randomUUID);
    }

    @Test
    void newRequestCreatesNewSessionAndLimitsVisibleRankingToFive() {
        var desiredIngredient = recipes.getFirst().getIngredientGroups().getFirst()
                .getStandardOption().getIngredient();
        var desiredTaste = taste("Herzhaft");
        RecommendationSession first = workflow.start(3, Optional.of(Duration.ofMinutes(45)),
                List.of(desiredTaste), List.of(desiredIngredient));
        RecommendationSession second = workflow.start(3, Optional.empty(), List.of());

        assertEquals(5, first.recommendations().size());
        assertEquals(3, first.requestedServings());
        assertEquals(Duration.ofMinutes(45), first.requestedMaximumTime().orElseThrow());
        assertEquals(List.of(desiredIngredient.getId()), first.desiredIngredientIds());
        assertEquals(List.of(desiredTaste.getId()), first.desiredTasteIds());
        assertFalse(first.inventoryEmpty());
        assertNotEquals(first.id(), second.id());
    }

    @Test
    void shownSelectionAndDismissalAreExplicitAndDeduplicatedWithinSession() {
        RecommendationSession session = workflow.start(2, Optional.empty(), List.of());
        UUID selectedId = session.recommendations().getFirst().recipe().getId();
        UUID dismissedId = session.recommendations().get(1).recipe().getId();

        workflow.recordShown(session);
        workflow.recordShown(session);
        workflow.select(session, selectedId);
        workflow.select(session, selectedId);
        workflow.dismiss(session, dismissedId);
        workflow.dismiss(session, dismissedId);
        workflow.recordShown(session);

        assertEquals(5, interactions.count(RecommendationAction.SHOWN));
        assertEquals(1, interactions.count(RecommendationAction.SELECTED));
        assertEquals(1, interactions.count(RecommendationAction.DISMISSED));
        assertEquals(4, session.visibleRecommendations().size());
        assertTrue(feedback.values.isEmpty());
        assertTrue(history.values.isEmpty());

        RecommendationSession nextSession = workflow.start(2, Optional.empty(), List.of());
        workflow.recordShown(nextSession);
        assertEquals(10, interactions.count(RecommendationAction.SHOWN));
    }

    @Test
    void dismissedRecipeReturnsInNewSessionWhenItStillRanksInVisibleResults() {
        recipeRepository.values.subList(3, recipeRepository.values.size()).clear();
        RecommendationSession first = workflow.start(2, Optional.empty(), List.of());
        UUID recipeId = recipeIds(first).getFirst();
        var original = first.recommendationFor(recipeId).orElseThrow();
        workflow.recordShown(first);
        workflow.dismiss(first, recipeId);

        assertFalse(first.visibleRecommendations().stream()
                .anyMatch(result -> result.recipe().getId().equals(recipeId)));
        RecommendationSession second = workflow.start(2, Optional.empty(), List.of());
        workflow.recordShown(second);

        assertNotEquals(first.id(), second.id());
        assertTrue(second.visibleRecommendations().stream()
                .anyMatch(result -> result.recipe().getId().equals(recipeId)));
        assertTrue(second.recommendationFor(recipeId).orElseThrow().score()
                .compareTo(original.score()) < 0);
        assertEquals(1, interactions.findBySessionId(second.id()).stream()
                .filter(event -> event.getRecipeId().equals(recipeId)
                        && event.getAction() == RecommendationAction.SHOWN).count());
        assertEquals(0, interactions.findBySessionId(second.id()).stream()
                .filter(event -> event.getAction() == RecommendationAction.DISMISSED).count());
        assertEquals(original.score(), interactions.findBySessionId(first.id()).stream()
                .filter(event -> event.getAction() == RecommendationAction.DISMISSED)
                .findFirst().orElseThrow().getDisplayedScore());
        assertTrue(feedback.values.isEmpty());
        assertTrue(history.values.isEmpty());
    }

    @Test
    void historicalDismissalCanMoveRecipeBelowTopFiveWithoutExcludingIt() {
        RecommendationSession first = workflow.start(2, Optional.empty(), List.of());
        UUID recipeId = recipeIds(first).getFirst();
        workflow.recordShown(first);
        workflow.dismiss(first, recipeId);

        RecommendationSession second = workflow.start(2, Optional.empty(), List.of());
        assertTrue(second.recommendationFor(recipeId).isEmpty(),
                "Six otherwise tied candidates now rank above the dismissed candidate.");

        RecommendationContext context = new RecommendationPersonalizationService(
                history, feedback, interactions, Clock.fixed(NOW, ZoneOffset.UTC))
                .enrich(recipes, RecommendationContext.pantryOnly(2, List.of()));
        RecommendationOutcome fullRanking = new RecipeRecommendationService()
                .recommend(recipes, context);
        assertTrue(fullRanking.exclusions().isEmpty());
        assertEquals(recipeId, fullRanking.recommendations().getLast().recipe().getId());
        assertEquals(7, fullRanking.recommendations().size());
    }

    @Test
    void failedDismissalKeepsRecipeVisibleAndRetryRecordsOneEvent() {
        RecommendationSession session = workflow.start(2, Optional.empty(), List.of());
        UUID recipeId = recipeIds(session).getFirst();
        workflow.recordShown(session);
        interactions.failNextSave = true;

        assertThrows(PersistenceException.class, () -> workflow.dismiss(session, recipeId));
        assertEquals(5, session.visibleRecommendations().size());
        assertEquals(0, interactions.count(RecommendationAction.DISMISSED));
        workflow.dismiss(session, recipeId);
        assertEquals(4, session.visibleRecommendations().size());
        assertEquals(1, interactions.count(RecommendationAction.DISMISSED));
    }

    @Test
    void feedbackCanBeChangedAndClearedWithoutReorderingCurrentSession() {
        RecommendationSession session = workflow.start(2, Optional.empty(), List.of());
        List<UUID> originalOrder = recipeIds(session);
        UUID recipeId = originalOrder.getFirst();

        workflow.like(session, recipeId);
        assertEquals(RecipeFeedbackValue.LIKE,
                session.feedbackFor(recipeId).orElseThrow().getValue().orElseThrow());
        workflow.dislike(session, recipeId);
        assertEquals(RecipeFeedbackValue.DISLIKE,
                session.feedbackFor(recipeId).orElseThrow().getValue().orElseThrow());
        workflow.clearFeedback(session, recipeId);

        assertTrue(session.feedbackFor(recipeId).isEmpty());
        assertEquals(originalOrder, recipeIds(session));
        assertTrue(interactions.values.isEmpty());
        assertTrue(history.values.isEmpty());
    }

    @Test
    void dislikeIsVisibleInNextSessionWithoutCreatingDismissedEvent() {
        recipeRepository.values.subList(3, recipeRepository.values.size()).clear();
        RecommendationSession first = workflow.start(2, Optional.empty(), List.of());
        UUID recipeId = first.recommendations().getFirst().recipe().getId();

        workflow.dislike(first, recipeId);
        RecommendationSession second = workflow.start(2, Optional.empty(), List.of());

        assertEquals(RecipeFeedbackValue.DISLIKE,
                second.feedbackFor(recipeId).orElseThrow().getValue().orElseThrow());
        assertTrue(second.recommendationFor(recipeId).orElseThrow().reasonCodes().stream()
                .anyMatch(code -> code == RecommendationReasonCode.RECIPE_EXPLICITLY_DISLIKED
                        || code == RecommendationReasonCode.RECIPE_STRONGLY_DISLIKED));
        assertEquals(0, interactions.count(RecommendationAction.DISMISSED));
    }

    @Test
    void clearedFeedbackProducesNoExplicitPreferenceInNextSession() {
        recipeRepository.values.subList(3, recipeRepository.values.size()).clear();
        RecommendationSession first = workflow.start(2, Optional.empty(), List.of());
        UUID recipeId = first.recommendations().getFirst().recipe().getId();

        workflow.like(first, recipeId);
        workflow.clearFeedback(first, recipeId);
        RecommendationSession second = workflow.start(2, Optional.empty(), List.of());

        assertTrue(second.feedbackFor(recipeId).isEmpty());
        assertTrue(second.recommendationFor(recipeId).orElseThrow().reasonCodes().stream()
                .noneMatch(code -> code == RecommendationReasonCode.RECIPE_EXPLICITLY_LIKED
                        || code == RecommendationReasonCode.RECIPE_STRONGLY_LIKED
                        || code == RecommendationReasonCode.RECIPE_EXPLICITLY_DISLIKED
                        || code == RecommendationReasonCode.RECIPE_STRONGLY_DISLIKED));
    }

    @Test
    void cookedRecipeLeavesCurrentAndNextSameDaySessionWithoutDuplicateEvents() {
        recipeRepository.values.subList(3, recipeRepository.values.size()).clear();
        RecommendationSession first = workflow.start(4, Optional.empty(), List.of());
        Recipe chosen = first.recommendations().getFirst().recipe();
        List<UUID> originalOrder = recipeIds(first);
        workflow.recordShown(first);
        workflow.select(first, chosen.getId());
        workflow.like(first, chosen.getId());
        MealHistoryEntry cooked = workflow.recordCooked(first, chosen.getId(), 4);
        MealHistoryEntry repeated = workflow.recordCooked(first, chosen.getId(), 4);
        workflow.recordShown(first);

        RecommendationSession second = workflow.start(4, Optional.empty(), List.of());

        assertEquals(cooked, repeated);
        assertEquals(1, history.values.size());
        assertEquals(MealHistorySource.RECOMMENDATION, cooked.getSource());
        assertEquals(chosen.getId(), cooked.getRecipeId());
        assertEquals(chosen.getName(), cooked.getRecipeName());
        assertEquals(4, cooked.getServings());
        assertEquals(NOW, cooked.getOccurredAt());
        assertFalse(first.visibleRecommendations().stream()
                .anyMatch(result -> result.recipe().getId().equals(chosen.getId())));
        assertEquals(originalOrder.stream().filter(id -> !id.equals(chosen.getId())).toList(),
                first.visibleRecommendations().stream()
                        .map(result -> result.recipe().getId()).toList());
        assertTrue(first.hasCookedRecommendation());
        assertEquals(0, interactions.count(RecommendationAction.DISMISSED));
        assertEquals(3, interactions.count(RecommendationAction.SHOWN));
        assertEquals(RecipeFeedbackValue.LIKE,
                feedback.findByRecipeId(chosen.getId()).orElseThrow()
                        .getValue().orElseThrow());
        assertTrue(second.recommendationFor(chosen.getId()).isEmpty());
    }

    private static List<UUID> recipeIds(RecommendationSession session) {
        return session.recommendations().stream()
                .map(result -> result.recipe().getId()).toList();
    }

    private static final class MemoryRecipeRepository implements RecipeRepository {
        private final List<Recipe> values;
        private MemoryRecipeRepository(List<Recipe> values) {
            this.values = new ArrayList<>(values);
        }
        @Override public void save(Recipe recipe) { throw new UnsupportedOperationException(); }
        @Override public Optional<Recipe> findById(UUID id) {
            return values.stream().filter(recipe -> recipe.getId().equals(id)).findFirst();
        }
        @Override public List<Recipe> findAll() { return values; }
        @Override public boolean deleteById(UUID id) { return false; }
    }

    private static final class MemoryInventoryRepository implements InventoryRepository {
        private final List<InventoryItem> values;
        private MemoryInventoryRepository(List<InventoryItem> values) { this.values = values; }
        @Override public void save(InventoryItem item) { throw new UnsupportedOperationException(); }
        @Override public Optional<InventoryItem> findById(UUID id) { return Optional.empty(); }
        @Override public List<InventoryItem> findAll() { return values; }
        @Override public boolean deleteById(UUID id) { return false; }
    }

    private static final class MemoryHistoryRepository implements MealHistoryRepository {
        private final List<MealHistoryEntry> values = new ArrayList<>();
        @Override public void save(MealHistoryEntry entry) { values.add(entry); }
        @Override public Optional<MealHistoryEntry> findById(UUID id) {
            return values.stream().filter(value -> value.getId().equals(id)).findFirst();
        }
        @Override public Optional<MealHistoryEntry> findBySourceMealPlanEntryId(UUID id) {
            return Optional.empty();
        }
        @Override public List<MealHistoryEntry> findRecent(int limit) { return values; }
        @Override public List<MealHistoryEntry> findByRecipeId(UUID id) {
            return values.stream().filter(value -> value.getRecipeId().equals(id)).toList();
        }
        @Override public List<MealHistoryEntry> findBetween(Instant from, Instant to) {
            return values.stream().filter(value -> !value.getOccurredAt().isBefore(from)
                    && value.getOccurredAt().isBefore(to)).toList();
        }
        @Override public Optional<MealHistoryEntry> findLatestByRecipeId(UUID id) {
            return findByRecipeId(id).stream().max(Comparator.comparing(
                    MealHistoryEntry::getOccurredAt));
        }
        @Override public Map<UUID, MealHistoryEntry> findLatestByRecipeIds(Collection<UUID> ids) {
            Map<UUID, MealHistoryEntry> result = new LinkedHashMap<>();
            ids.forEach(id -> findLatestByRecipeId(id).ifPresent(value -> result.put(id, value)));
            return result;
        }
        @Override public long countByRecipeIdBetween(UUID id, Instant from, Instant to) {
            return findBetween(from, to).stream()
                    .filter(value -> value.getRecipeId().equals(id)).count();
        }
        @Override public boolean existsByRecipeId(UUID id) {
            return values.stream().anyMatch(value -> value.getRecipeId().equals(id));
        }
        @Override public boolean deleteById(UUID id) {
            return values.removeIf(value -> value.getId().equals(id));
        }
    }

    private static final class MemoryFeedbackRepository implements RecipeFeedbackRepository {
        private final Map<UUID, RecipeFeedback> values = new LinkedHashMap<>();
        @Override public void save(RecipeFeedback value) { values.put(value.getRecipeId(), value); }
        @Override public Optional<RecipeFeedback> findByRecipeId(UUID id) {
            return Optional.ofNullable(values.get(id));
        }
        @Override public Map<UUID, RecipeFeedback> findByRecipeIds(Collection<UUID> ids) {
            Map<UUID, RecipeFeedback> result = new LinkedHashMap<>();
            ids.forEach(id -> Optional.ofNullable(values.get(id))
                    .ifPresent(value -> result.put(id, value)));
            return result;
        }
        @Override public boolean deleteByRecipeId(UUID id) { return values.remove(id) != null; }
    }

    private static final class MemoryInteractionRepository
            implements RecommendationInteractionRepository {
        private final List<RecommendationInteraction> values = new ArrayList<>();
        private boolean failNextSave;
        @Override public void save(RecommendationInteraction value) {
            if (failNextSave) {
                failNextSave = false;
                throw new PersistenceException("Simulated interaction write failure.");
            }
            values.add(value);
        }
        @Override public List<RecommendationInteraction> findBySessionId(UUID id) {
            return values.stream().filter(value -> value.getRecommendationSessionId().equals(id))
                    .toList();
        }
        @Override public List<RecommendationInteraction> findByRecipeId(UUID id) {
            return values.stream().filter(value -> value.getRecipeId().equals(id)).toList();
        }
        @Override public Map<UUID, List<RecommendationInteraction>> findByRecipeIds(
                Collection<UUID> ids) {
            Map<UUID, List<RecommendationInteraction>> result = new LinkedHashMap<>();
            ids.forEach(id -> result.put(id, findByRecipeId(id)));
            return result;
        }
        @Override public List<RecommendationInteraction> findRecent(int limit) {
            return values.stream().limit(limit).toList();
        }
        @Override public boolean deleteById(UUID id) {
            return values.removeIf(value -> value.getId().equals(id));
        }
        private long count(RecommendationAction action) {
            return values.stream().filter(value -> value.getAction() == action).count();
        }
    }
}
