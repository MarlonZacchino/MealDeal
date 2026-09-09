package de.mealdeal.service.recommendation;

import de.mealdeal.domain.MealHistoryEntry;
import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeFeedback;
import de.mealdeal.domain.RecipeFeedbackValue;
import de.mealdeal.domain.RecommendationAction;
import de.mealdeal.domain.Taste;
import de.mealdeal.persistence.repository.InventoryRepository;
import de.mealdeal.persistence.repository.RecipeRepository;
import de.mealdeal.service.MealHistoryService;
import de.mealdeal.service.InventoryConsumptionService;
import de.mealdeal.service.RecipeFeedbackService;
import de.mealdeal.service.RecommendationInteractionService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Application workflow for one visible recommendation session and its explicit local actions.
 *
 * <p>It deliberately keeps calculation, presentation events, Recipe feedback and confirmed
 * cooking separate while giving JavaFX one consistent API.</p>
 */
public final class RecommendationWorkflowService {

    public static final int VISIBLE_RESULT_LIMIT = 5;

    private final RecipeRepository recipeRepository;
    private final InventoryRepository inventoryRepository;
    private final PersonalizedRecommendationService recommendationService;
    private final RecommendationInteractionService interactionService;
    private final RecipeFeedbackService feedbackService;
    private final MealHistoryService historyService;
    private final InventoryConsumptionService consumptionService;
    private final Clock clock;
    private final Supplier<UUID> sessionIdSupplier;

    public RecommendationWorkflowService(
            RecipeRepository recipeRepository,
            InventoryRepository inventoryRepository,
            PersonalizedRecommendationService recommendationService,
            RecommendationInteractionService interactionService,
            RecipeFeedbackService feedbackService,
            MealHistoryService historyService,
            InventoryConsumptionService consumptionService) {
        this(recipeRepository, inventoryRepository, recommendationService, interactionService,
                feedbackService, historyService, consumptionService,
                Clock.systemDefaultZone(), UUID::randomUUID);
    }

    RecommendationWorkflowService(
            RecipeRepository recipeRepository,
            InventoryRepository inventoryRepository,
            PersonalizedRecommendationService recommendationService,
            RecommendationInteractionService interactionService,
            RecipeFeedbackService feedbackService,
            MealHistoryService historyService,
            InventoryConsumptionService consumptionService,
            Clock clock,
            Supplier<UUID> sessionIdSupplier) {
        this.recipeRepository = Objects.requireNonNull(
                recipeRepository, "Recipe repository must not be null.");
        this.inventoryRepository = Objects.requireNonNull(
                inventoryRepository, "Inventory repository must not be null.");
        this.recommendationService = Objects.requireNonNull(
                recommendationService, "Recommendation service must not be null.");
        this.interactionService = Objects.requireNonNull(
                interactionService, "Interaction service must not be null.");
        this.feedbackService = Objects.requireNonNull(
                feedbackService, "Feedback service must not be null.");
        this.historyService = Objects.requireNonNull(
                historyService, "History service must not be null.");
        this.consumptionService = Objects.requireNonNull(
                consumptionService, "Consumption service must not be null.");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null.");
        this.sessionIdSupplier = Objects.requireNonNull(
                sessionIdSupplier, "Session ID supplier must not be null.");
    }

    /** Starts a new calculation without treating calculated results as already shown. */
    public RecommendationSession start(
            int servings, Optional<Duration> maximumTime, Collection<Taste> desiredTastes) {
        return start(servings, maximumTime, desiredTastes, List.of());
    }

    /** Starts a recommendation with optional taste and soft ingredient wishes. */
    public RecommendationSession start(
            int servings, Optional<Duration> maximumTime, Collection<Taste> desiredTastes,
            Collection<Ingredient> desiredIngredients) {
        Objects.requireNonNull(maximumTime, "Maximum time must not be null.");
        Objects.requireNonNull(desiredTastes, "Desired tastes must not be null.");
        Objects.requireNonNull(desiredIngredients, "Desired ingredients must not be null.");
        if (desiredTastes.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Desired tastes must not contain null values.");
        }
        if (desiredIngredients.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Desired ingredients must not contain null values.");
        }
        Map<UUID, BigDecimal> tasteAffinities = new LinkedHashMap<>();
        desiredTastes.stream()
                .sorted(java.util.Comparator.comparing(Taste::getId))
                .forEach(taste -> tasteAffinities.put(taste.getId(), BigDecimal.ONE));
        RecommendationRequest request = new RecommendationRequest(
                servings, maximumTime, Optional.empty());
        consumptionService.consumePastEntries();
        var inventory = inventoryRepository.findAll();
        RecommendationContext context = new RecommendationContext(
                request,
                inventory, new TastePreferenceProfile(tasteAffinities),
                DesiredIngredientProfile.fromIngredients(desiredIngredients), List.of(),
                RecommendationConstraints.none());
        RecommendationOutcome outcome = recommendationService.recommend(
                recipeRepository.findAll(), context);
        List<RecipeRecommendation> visible = outcome.recommendations().stream()
                .limit(VISIBLE_RESULT_LIMIT)
                .toList();
        Map<UUID, RecipeFeedback> feedback = feedbackService.findByRecipeIds(
                visible.stream().map(result -> result.recipe().getId()).toList());
        return new RecommendationSession(
                Objects.requireNonNull(sessionIdSupplier.get(),
                        "Generated session ID must not be null."),
                visible, inventory.isEmpty(), servings, maximumTime,
                desiredIngredients.stream().map(Ingredient::getId).distinct().sorted().toList(),
                desiredTastes.stream().map(Taste::getId).distinct().sorted().toList(), feedback);
    }

    /** Records each actually rendered Recipe at most once in this session. */
    public void recordShown(RecommendationSession session) {
        checkedSession(session).visibleRecommendations().forEach(recommendation -> {
            UUID recipeId = recommendation.recipe().getId();
            if (!session.wasShown(recipeId)) {
                interactionService.record(session.id(), recommendation,
                        RecommendationAction.SHOWN, session.displayedRankOf(recipeId));
                session.markShown(recipeId);
            }
        });
    }

    /** Records one deliberate selection once and returns the existing local Recipe. */
    public Recipe select(RecommendationSession session, UUID recipeId) {
        RecipeRecommendation recommendation = recommendation(session, recipeId);
        if (!session.wasSelected(recipeId)) {
            interactionService.record(session.id(), recommendation,
                    RecommendationAction.SELECTED, session.displayedRankOf(recipeId));
            session.markSelected(recipeId);
        }
        return recommendation.recipe();
    }

    /** Records a session-local dismissal once; it never creates Recipe DISLIKE feedback. */
    public void dismiss(RecommendationSession session, UUID recipeId) {
        RecipeRecommendation recommendation = recommendation(session, recipeId);
        if (!session.wasDismissed(recipeId)) {
            interactionService.record(session.id(), recommendation,
                    RecommendationAction.DISMISSED, session.displayedRankOf(recipeId));
            session.markDismissed(recipeId);
        }
    }

    public RecipeFeedback like(RecommendationSession session, UUID recipeId) {
        recommendation(session, recipeId);
        RecipeFeedback feedback = feedbackService.update(recipeId,
                Optional.of(RecipeFeedbackValue.LIKE), OptionalInt.empty()).orElseThrow();
        session.updateFeedback(recipeId, feedback);
        return feedback;
    }

    public RecipeFeedback dislike(RecommendationSession session, UUID recipeId) {
        recommendation(session, recipeId);
        RecipeFeedback feedback = feedbackService.update(recipeId,
                Optional.of(RecipeFeedbackValue.DISLIKE), OptionalInt.empty()).orElseThrow();
        session.updateFeedback(recipeId, feedback);
        return feedback;
    }

    public void clearFeedback(RecommendationSession session, UUID recipeId) {
        recommendation(session, recipeId);
        feedbackService.clear(recipeId);
        session.clearFeedback(recipeId);
    }

    /** Refreshes explicit feedback after detail return without recalculating the ranking. */
    public void refreshFeedback(RecommendationSession session) {
        List<UUID> ids = checkedSession(session).recommendations().stream()
                .map(result -> result.recipe().getId()).toList();
        Map<UUID, RecipeFeedback> feedback = feedbackService.findByRecipeIds(ids);
        ids.forEach(id -> {
            if (feedback.containsKey(id)) {
                session.updateFeedback(id, feedback.get(id));
            } else {
                session.clearFeedback(id);
            }
        });
    }

    /** Records one cooking confirmation per Recipe and presentation session. */
    public MealHistoryEntry recordCooked(
            RecommendationSession session, UUID recipeId, int servings) {
        Recipe recipe = recommendation(session, recipeId).recipe();
        Optional<MealHistoryEntry> existing = session.cookedEntry(recipeId);
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }
        MealHistoryEntry entry = historyService.recordRecommendation(
                recipe, servings, clock.instant());
        session.markCooked(recipeId, entry);
        return entry;
    }

    private static RecommendationSession checkedSession(RecommendationSession session) {
        return Objects.requireNonNull(session, "Recommendation session must not be null.");
    }

    private static RecipeRecommendation recommendation(
            RecommendationSession session, UUID recipeId) {
        Objects.requireNonNull(recipeId, "Recipe ID must not be null.");
        return checkedSession(session).recommendationFor(recipeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Recipe is not part of this recommendation session."));
    }
}
