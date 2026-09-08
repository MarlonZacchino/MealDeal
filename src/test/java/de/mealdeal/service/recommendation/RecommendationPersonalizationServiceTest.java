package de.mealdeal.service.recommendation;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.MealHistoryEntry;
import de.mealdeal.domain.MealHistorySource;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeFeedback;
import de.mealdeal.domain.RecipeFeedbackValue;
import de.mealdeal.domain.RecommendationAction;
import de.mealdeal.domain.RecommendationInteraction;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.repository.MealHistoryRepository;
import de.mealdeal.persistence.repository.RecipeFeedbackRepository;
import de.mealdeal.persistence.repository.RecommendationInteractionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static de.mealdeal.service.recommendation.RecommendationTestFixtures.group;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.ingredient;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.option;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.recipe;
import static de.mealdeal.service.recommendation.RecommendationTestFixtures.taste;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationPersonalizationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

    @Test
    void loadsAllCandidatesWithThreeBatchReadsAndOneClockSnapshot() {
        Recipe alpha = candidate("alpha");
        Recipe beta = candidate("beta");
        MealHistoryEntry betaHistory = history(beta, NOW.minusSeconds(7 * 86_400));
        RecipeFeedback betaFeedback = new RecipeFeedback(
                beta.getId(), RecipeFeedbackValue.DISLIKE, null, NOW.minusSeconds(60));
        List<RecommendationInteraction> betaSelections = List.of(
                interaction(beta, RecommendationAction.SELECTED),
                interaction(beta, RecommendationAction.SELECTED),
                interaction(beta, RecommendationAction.SELECTED));
        BatchHistoryRepository history = new BatchHistoryRepository(
                Map.of(beta.getId(), betaHistory));
        BatchFeedbackRepository feedback = new BatchFeedbackRepository(
                Map.of(beta.getId(), betaFeedback));
        BatchInteractionRepository interactions = new BatchInteractionRepository(
                Map.of(beta.getId(), betaSelections));
        RecommendationPersonalizationService service = new RecommendationPersonalizationService(
                history, feedback, interactions, Clock.fixed(NOW, ZoneOffset.UTC));

        Map<UUID, RecipePersonalizationSignals> signals =
                service.loadSignals(List.of(beta, alpha));

        assertEquals(List.of(alpha.getId(), beta.getId()).stream().sorted().toList(),
                signals.keySet().stream().toList());
        assertEquals(1, history.batchCalls);
        assertEquals(1, feedback.batchCalls);
        assertEquals(1, interactions.batchCalls);
        assertEquals(BigDecimal.ONE, signals.get(alpha.getId()).freshness());
        assertEquals(RecipeRecency.NEVER_COOKED, signals.get(alpha.getId()).recency());
        assertEquals(0, new BigDecimal("0.5").compareTo(signals.get(beta.getId()).freshness()));
        assertEquals(new BigDecimal("-0.75"),
                signals.get(beta.getId()).effectivePreference());
        assertEquals(0, new BigDecimal("0.30").compareTo(
                signals.get(beta.getId()).implicitInteractionPreference()));
        assertTrue(signals.get(beta.getId()).hasExplicitFeedback());
    }

    @Test
    void interactionIsFallbackOnlyWhenExplicitFeedbackIsAbsent() {
        Recipe candidate = candidate("fallback");
        BatchInteractionRepository interactions = new BatchInteractionRepository(Map.of(
                candidate.getId(), List.of(
                        interaction(candidate, RecommendationAction.DISMISSED),
                        interaction(candidate, RecommendationAction.DISMISSED))));
        RecommendationPersonalizationService service = new RecommendationPersonalizationService(
                new BatchHistoryRepository(Map.of()), new BatchFeedbackRepository(Map.of()),
                interactions, Clock.fixed(NOW, ZoneOffset.UTC));

        RecipePersonalizationSignals result = service.loadSignals(List.of(candidate))
                .get(candidate.getId());

        assertEquals(0, new BigDecimal("-0.25").compareTo(result.effectivePreference()));
        assertEquals(2, result.dismissedCount());
        assertTrue(result.explicitPreference().isEmpty());
    }

    @Test
    void explicitLikeOverridesManyDismissedInteractions() {
        Recipe candidate = candidate("explicit-like");
        RecipeFeedback liked = new RecipeFeedback(
                candidate.getId(), RecipeFeedbackValue.LIKE, null, NOW);
        List<RecommendationInteraction> dismissals = java.util.stream.IntStream.range(0, 20)
                .mapToObj(ignored -> interaction(candidate, RecommendationAction.DISMISSED))
                .toList();
        RecommendationPersonalizationService service = new RecommendationPersonalizationService(
                new BatchHistoryRepository(Map.of()),
                new BatchFeedbackRepository(Map.of(candidate.getId(), liked)),
                new BatchInteractionRepository(Map.of(candidate.getId(), dismissals)),
                Clock.fixed(NOW, ZoneOffset.UTC));

        RecipePersonalizationSignals result = service.loadSignals(List.of(candidate))
                .get(candidate.getId());

        assertEquals(new BigDecimal("0.75"), result.effectivePreference());
        assertTrue(result.implicitInteractionPreference().signum() < 0);
    }

    @Test
    void enrichingContextPreservesAllExistingRequestData() {
        Recipe candidate = candidate("context");
        RecommendationPersonalizationService service = new RecommendationPersonalizationService(
                new BatchHistoryRepository(Map.of()), new BatchFeedbackRepository(Map.of()),
                new BatchInteractionRepository(Map.of()),
                Clock.fixed(NOW, ZoneOffset.UTC));
        RecommendationContext original = RecommendationContext.pantryOnly(3, List.of());

        RecommendationContext enriched = service.enrich(List.of(candidate), original);

        assertEquals(original.request(), enriched.request());
        assertEquals(original.inventorySnapshot(), enriched.inventorySnapshot());
        assertEquals(original.tastePreferences(), enriched.tastePreferences());
        assertEquals(original.householdPreferences(), enriched.householdPreferences());
        assertEquals(original.hardConstraints(), enriched.hardConstraints());
        assertTrue(enriched.personalizationFor(candidate.getId()).isPresent());
    }

    @Test
    void applicationFacadeDelegatesLoadedSignalsToExistingScorer() {
        Recipe candidate = candidate("facade");
        RecommendationPersonalizationService personalization =
                new RecommendationPersonalizationService(
                        new BatchHistoryRepository(Map.of()),
                        new BatchFeedbackRepository(Map.of()),
                        new BatchInteractionRepository(Map.of()),
                        Clock.fixed(NOW, ZoneOffset.UTC));
        PersonalizedRecommendationService service = new PersonalizedRecommendationService(
                personalization, new RecipeRecommendationService());
        RecommendationContext context = RecommendationContext.pantryOnly(2, List.of(
                RecommendationTestFixtures.stock("facade", ingredientOf(candidate),
                        "100", Unit.GRAM)));

        RecipeRecommendation result = service.recommend(List.of(candidate), context)
                .recommendations().getFirst();

        assertEquals(BigDecimal.ONE,
                result.signals().valueOf(RecommendationSignal.VARIETY_SCORE).orElseThrow());
        assertEquals(0, new BigDecimal("0.5").compareTo(result.signals()
                .valueOf(RecommendationSignal.RECIPE_PREFERENCE).orElseThrow()));
        assertTrue(result.reasonCodes().contains(
                RecommendationReasonCode.RECIPE_NEVER_COOKED));
    }

    @Test
    void allConfirmedMealSourcesProduceTheSameRecency() {
        Recipe candidate = candidate("sources");
        Instant occurredAt = NOW.minusSeconds(3 * 86_400);

        List<BigDecimal> freshnessValues = java.util.Arrays.stream(MealHistorySource.values())
                .map(source -> new MealHistoryEntry(
                        candidate.getId(), candidate.getName(), occurredAt, 2, source,
                        source == MealHistorySource.MEAL_PLAN ? UUID.randomUUID() : null,
                        NOW.minusSeconds(10)))
                .map(event -> new RecommendationPersonalizationService(
                        new BatchHistoryRepository(Map.of(candidate.getId(), event)),
                        new BatchFeedbackRepository(Map.of()),
                        new BatchInteractionRepository(Map.of()),
                        Clock.fixed(NOW, ZoneOffset.UTC)))
                .map(service -> service.loadSignals(List.of(candidate))
                        .get(candidate.getId()).freshness())
                .toList();

        assertTrue(freshnessValues.stream().allMatch(
                value -> value.compareTo(freshnessValues.getFirst()) == 0));
    }

    private static Recipe candidate(String key) {
        Ingredient ingredient = ingredient("R3 " + key);
        Taste taste = taste("R3 Taste " + key);
        return recipe(key, key, List.of(group(key, List.of(
                option(key, ingredient, "100", Unit.GRAM, 0)), 0)), taste);
    }

    private static Ingredient ingredientOf(Recipe recipe) {
        return recipe.getIngredientGroups().getFirst().getStandardOption().getIngredient();
    }

    private static MealHistoryEntry history(Recipe recipe, Instant occurredAt) {
        return new MealHistoryEntry(recipe.getId(), recipe.getName(), occurredAt, 2,
                MealHistorySource.MANUAL, null, NOW.minusSeconds(10));
    }

    private static RecommendationInteraction interaction(
            Recipe recipe, RecommendationAction action) {
        return new RecommendationInteraction(UUID.randomUUID(), recipe.getId(), action,
                1, new BigDecimal("0.5"), NOW.minusSeconds(30));
    }

    private static final class BatchHistoryRepository implements MealHistoryRepository {
        private final Map<UUID, MealHistoryEntry> latest;
        private int batchCalls;

        private BatchHistoryRepository(Map<UUID, MealHistoryEntry> latest) {
            this.latest = latest;
        }

        @Override public Map<UUID, MealHistoryEntry> findLatestByRecipeIds(
                Collection<UUID> recipeIds) {
            batchCalls++;
            return latest;
        }
        @Override public void save(MealHistoryEntry entry) { throw unused(); }
        @Override public Optional<MealHistoryEntry> findById(UUID id) { throw unused(); }
        @Override public Optional<MealHistoryEntry> findBySourceMealPlanEntryId(UUID id) {
            throw unused();
        }
        @Override public List<MealHistoryEntry> findRecent(int limit) { throw unused(); }
        @Override public List<MealHistoryEntry> findByRecipeId(UUID recipeId) { throw unused(); }
        @Override public List<MealHistoryEntry> findBetween(Instant from, Instant to) {
            throw unused();
        }
        @Override public Optional<MealHistoryEntry> findLatestByRecipeId(UUID id) {
            throw unused();
        }
        @Override public long countByRecipeIdBetween(UUID id, Instant from, Instant to) {
            throw unused();
        }
        @Override public boolean existsByRecipeId(UUID recipeId) { throw unused(); }
        @Override public boolean deleteById(UUID id) { throw unused(); }
    }

    private static final class BatchFeedbackRepository implements RecipeFeedbackRepository {
        private final Map<UUID, RecipeFeedback> feedback;
        private int batchCalls;

        private BatchFeedbackRepository(Map<UUID, RecipeFeedback> feedback) {
            this.feedback = feedback;
        }

        @Override public Map<UUID, RecipeFeedback> findByRecipeIds(Collection<UUID> recipeIds) {
            batchCalls++;
            return feedback;
        }
        @Override public void save(RecipeFeedback value) { throw unused(); }
        @Override public Optional<RecipeFeedback> findByRecipeId(UUID id) { throw unused(); }
        @Override public boolean deleteByRecipeId(UUID id) { throw unused(); }
    }

    private static final class BatchInteractionRepository
            implements RecommendationInteractionRepository {
        private final Map<UUID, List<RecommendationInteraction>> interactions;
        private int batchCalls;

        private BatchInteractionRepository(
                Map<UUID, List<RecommendationInteraction>> interactions) {
            this.interactions = interactions;
        }

        @Override public Map<UUID, List<RecommendationInteraction>> findByRecipeIds(
                Collection<UUID> recipeIds) {
            batchCalls++;
            return interactions;
        }
        @Override public void save(RecommendationInteraction interaction) { throw unused(); }
        @Override public List<RecommendationInteraction> findBySessionId(UUID id) {
            throw unused();
        }
        @Override public List<RecommendationInteraction> findByRecipeId(UUID id) {
            throw unused();
        }
        @Override public List<RecommendationInteraction> findRecent(int limit) { throw unused(); }
        @Override public boolean deleteById(UUID id) { throw unused(); }
    }

    private static UnsupportedOperationException unused() {
        return new UnsupportedOperationException("Individual repository query must not be used.");
    }
}
