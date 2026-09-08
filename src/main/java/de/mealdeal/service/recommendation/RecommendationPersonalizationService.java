package de.mealdeal.service.recommendation;

import de.mealdeal.domain.MealHistoryEntry;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeFeedback;
import de.mealdeal.domain.RecommendationInteraction;
import de.mealdeal.persistence.repository.MealHistoryRepository;
import de.mealdeal.persistence.repository.RecipeFeedbackRepository;
import de.mealdeal.persistence.repository.RecommendationInteractionRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Loads R2 data in batches and builds immutable inputs for the persistence-free scorer.
 *
 * <p>One invocation captures one clock instant. It never writes interactions, feedback,
 * meal history, or inventory.</p>
 */
public final class RecommendationPersonalizationService {

    private final MealHistoryRepository mealHistoryRepository;
    private final RecipeFeedbackRepository feedbackRepository;
    private final RecommendationInteractionRepository interactionRepository;
    private final Clock clock;
    private final RecommendationPersonalizationPolicy policy;

    public RecommendationPersonalizationService(
            MealHistoryRepository mealHistoryRepository,
            RecipeFeedbackRepository feedbackRepository,
            RecommendationInteractionRepository interactionRepository,
            Clock clock) {
        this(mealHistoryRepository, feedbackRepository, interactionRepository, clock,
                new RecommendationPersonalizationPolicy());
    }

    RecommendationPersonalizationService(
            MealHistoryRepository mealHistoryRepository,
            RecipeFeedbackRepository feedbackRepository,
            RecommendationInteractionRepository interactionRepository,
            Clock clock,
            RecommendationPersonalizationPolicy policy) {
        this.mealHistoryRepository = Objects.requireNonNull(
                mealHistoryRepository, "Meal-history repository must not be null.");
        this.feedbackRepository = Objects.requireNonNull(
                feedbackRepository, "Feedback repository must not be null.");
        this.interactionRepository = Objects.requireNonNull(
                interactionRepository, "Interaction repository must not be null.");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null.");
        this.policy = Objects.requireNonNull(policy, "Personalization policy must not be null.");
    }

    /** Enriches an already loaded base context without changing its request semantics. */
    public RecommendationContext enrich(
            Collection<Recipe> candidates, RecommendationContext baseContext) {
        Objects.requireNonNull(baseContext, "Recommendation context must not be null.");
        return baseContext.withPersonalization(loadSignals(candidates));
    }

    /** Builds one deterministic snapshot using exactly three batch repository reads. */
    public Map<UUID, RecipePersonalizationSignals> loadSignals(
            Collection<Recipe> candidates) {
        List<UUID> recipeIds = checkedRecipeIds(candidates);
        if (recipeIds.isEmpty()) {
            return Map.of();
        }
        Instant now = clock.instant();
        Map<UUID, MealHistoryEntry> latestHistory =
                mealHistoryRepository.findLatestByRecipeIds(recipeIds);
        Map<UUID, RecipeFeedback> feedback = feedbackRepository.findByRecipeIds(recipeIds);
        Map<UUID, List<RecommendationInteraction>> interactions =
                interactionRepository.findByRecipeIds(recipeIds);

        Map<UUID, RecipePersonalizationSignals> result = new LinkedHashMap<>();
        for (UUID recipeId : recipeIds) {
            Optional<Instant> lastCookedAt = Optional.ofNullable(latestHistory.get(recipeId))
                    .map(MealHistoryEntry::getOccurredAt);
            Optional<BigDecimal> explicit = policy.explicitPreference(
                    Optional.ofNullable(feedback.get(recipeId)));
            RecommendationPersonalizationPolicy.InteractionPreference implicit =
                    policy.aggregateInteractions(interactions.getOrDefault(recipeId, List.of()));
            BigDecimal effective = explicit.orElse(implicit.value());
            result.put(recipeId, new RecipePersonalizationSignals(
                    recipeId,
                    lastCookedAt,
                    policy.recency(lastCookedAt, now, clock.getZone()),
                    policy.freshness(lastCookedAt, now),
                    explicit,
                    implicit.value(),
                    effective,
                    implicit.selectedCount(),
                    implicit.dismissedCount()));
        }
        return Collections.unmodifiableMap(result);
    }

    private static List<UUID> checkedRecipeIds(Collection<Recipe> candidates) {
        Objects.requireNonNull(candidates, "Recommendation candidates must not be null.");
        if (candidates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Recommendation candidates must not contain null values.");
        }
        return candidates.stream().map(Recipe::getId).distinct().sorted().toList();
    }
}
