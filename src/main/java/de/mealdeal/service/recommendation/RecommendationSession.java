package de.mealdeal.service.recommendation;

import de.mealdeal.domain.MealHistoryEntry;
import de.mealdeal.domain.RecipeFeedback;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.Duration;

/**
 * One locally presented recommendation run with stable rank and score snapshots.
 *
 * <p>The session owns only presentation lifecycle state. It neither recalculates scores nor
 * persists events itself.</p>
 */
public final class RecommendationSession {

    private final UUID id;
    private final List<RecipeRecommendation> recommendations;
    private final boolean inventoryEmpty;
    private final int requestedServings;
    private final Optional<Duration> requestedMaximumTime;
    private final List<UUID> desiredIngredientIds;
    private final List<UUID> desiredTasteIds;
    private final Map<UUID, RecipeFeedback> feedbackByRecipe;
    private final Set<UUID> shownRecipeIds = new LinkedHashSet<>();
    private final Set<UUID> selectedRecipeIds = new LinkedHashSet<>();
    private final Set<UUID> dismissedRecipeIds = new LinkedHashSet<>();
    private final Map<UUID, MealHistoryEntry> cookedEntries = new LinkedHashMap<>();

    public RecommendationSession(
            UUID id, List<RecipeRecommendation> recommendations, boolean inventoryEmpty) {
        this(id, recommendations, inventoryEmpty, 1, Map.of());
    }

    public RecommendationSession(
            UUID id, List<RecipeRecommendation> recommendations, boolean inventoryEmpty,
            Map<UUID, RecipeFeedback> feedbackByRecipe) {
        this(id, recommendations, inventoryEmpty, 1, feedbackByRecipe);
    }

    public RecommendationSession(
            UUID id, List<RecipeRecommendation> recommendations, boolean inventoryEmpty,
            int requestedServings, Map<UUID, RecipeFeedback> feedbackByRecipe) {
        this(id, recommendations, inventoryEmpty, requestedServings, Optional.empty(),
                List.of(), List.of(), feedbackByRecipe);
    }

    /** Creates a session retaining the complete visible request state for detail return. */
    public RecommendationSession(
            UUID id, List<RecipeRecommendation> recommendations, boolean inventoryEmpty,
            int requestedServings, Optional<Duration> requestedMaximumTime,
            List<UUID> desiredIngredientIds, List<UUID> desiredTasteIds,
            Map<UUID, RecipeFeedback> feedbackByRecipe) {
        this.id = Objects.requireNonNull(id, "Recommendation session ID must not be null.");
        Objects.requireNonNull(recommendations, "Recommendations must not be null.");
        if (recommendations.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Recommendations must not contain null values.");
        }
        this.recommendations = List.copyOf(recommendations);
        this.inventoryEmpty = inventoryEmpty;
        if (requestedServings <= 0) {
            throw new IllegalArgumentException("Requested servings must be greater than zero.");
        }
        this.requestedServings = requestedServings;
        this.requestedMaximumTime = Objects.requireNonNull(
                requestedMaximumTime, "Requested maximum time must not be null.");
        this.desiredIngredientIds = checkedIds(
                desiredIngredientIds, "Desired ingredient IDs");
        this.desiredTasteIds = checkedIds(desiredTasteIds, "Desired taste IDs");
        this.feedbackByRecipe = new LinkedHashMap<>(Objects.requireNonNull(
                feedbackByRecipe, "Recipe feedback must not be null."));
    }

    public UUID id() {
        return id;
    }

    public List<RecipeRecommendation> recommendations() {
        return recommendations;
    }

    public List<RecipeRecommendation> visibleRecommendations() {
        return recommendations.stream()
                .filter(recommendation -> !dismissedRecipeIds.contains(
                        recommendation.recipe().getId()))
                .filter(recommendation -> !cookedEntries.containsKey(
                        recommendation.recipe().getId()))
                .toList();
    }

    /** Returns whether at least one result was hidden after being confirmed as cooked. */
    public boolean hasCookedRecommendation() {
        return !cookedEntries.isEmpty();
    }

    public boolean inventoryEmpty() {
        return inventoryEmpty;
    }

    public int requestedServings() {
        return requestedServings;
    }

    public Optional<Duration> requestedMaximumTime() {
        return requestedMaximumTime;
    }

    public List<UUID> desiredIngredientIds() {
        return desiredIngredientIds;
    }

    public List<UUID> desiredTasteIds() {
        return desiredTasteIds;
    }

    public Optional<RecipeFeedback> feedbackFor(UUID recipeId) {
        return Optional.ofNullable(feedbackByRecipe.get(
                Objects.requireNonNull(recipeId, "Recipe ID must not be null.")));
    }

    public boolean isCooked(UUID recipeId) {
        return cookedEntries.containsKey(Objects.requireNonNull(
                recipeId, "Recipe ID must not be null."));
    }

    public Optional<RecipeRecommendation> recommendationFor(UUID recipeId) {
        Objects.requireNonNull(recipeId, "Recipe ID must not be null.");
        return recommendations.stream()
                .filter(recommendation -> recommendation.recipe().getId().equals(recipeId))
                .findFirst();
    }

    public int displayedRankOf(UUID recipeId) {
        Objects.requireNonNull(recipeId, "Recipe ID must not be null.");
        for (int index = 0; index < recommendations.size(); index++) {
            if (recommendations.get(index).recipe().getId().equals(recipeId)) {
                return index + 1;
            }
        }
        throw new IllegalArgumentException("Recipe is not part of this recommendation session.");
    }

    boolean wasShown(UUID recipeId) {
        return shownRecipeIds.contains(recipeId);
    }

    boolean wasSelected(UUID recipeId) {
        return selectedRecipeIds.contains(recipeId);
    }

    boolean wasDismissed(UUID recipeId) {
        return dismissedRecipeIds.contains(recipeId);
    }

    void markShown(UUID recipeId) {
        shownRecipeIds.add(recipeId);
    }

    void markSelected(UUID recipeId) {
        selectedRecipeIds.add(recipeId);
    }

    void markDismissed(UUID recipeId) {
        dismissedRecipeIds.add(recipeId);
    }

    Optional<MealHistoryEntry> cookedEntry(UUID recipeId) {
        return Optional.ofNullable(cookedEntries.get(recipeId));
    }

    void markCooked(UUID recipeId, MealHistoryEntry entry) {
        cookedEntries.put(recipeId, entry);
    }

    void updateFeedback(UUID recipeId, RecipeFeedback feedback) {
        feedbackByRecipe.put(recipeId, feedback);
    }

    void clearFeedback(UUID recipeId) {
        feedbackByRecipe.remove(recipeId);
    }

    private static List<UUID> checkedIds(List<UUID> ids, String label) {
        Objects.requireNonNull(ids, label + " must not be null.");
        if (ids.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(label + " must not contain null values.");
        }
        return ids.stream().distinct().sorted().toList();
    }
}
