package de.mealdeal.service.recommendation;

import de.mealdeal.domain.InventoryItem;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable data needed for one recommendation request.
 *
 * <p>It deliberately contains snapshots and request preferences rather than repositories
 * or persisted user profiles.</p>
 */
public record RecommendationContext(
        RecommendationRequest request,
        List<InventoryItem> inventorySnapshot,
        TastePreferenceProfile tastePreferences,
        List<HouseholdMemberPreference> householdPreferences,
        RecommendationConstraints hardConstraints,
        Map<UUID, RecipePersonalizationSignals> personalizationSignals) {

    public RecommendationContext {
        request = Objects.requireNonNull(request, "Recommendation request must not be null.");
        inventorySnapshot = immutableList(
                inventorySnapshot, "Inventory snapshot");
        tastePreferences = Objects.requireNonNull(
                tastePreferences, "Taste preferences must not be null.");
        householdPreferences = immutableList(
                householdPreferences, "Household preferences");
        Set<String> memberIds = new HashSet<>();
        if (householdPreferences.stream()
                .anyMatch(member -> !memberIds.add(member.memberId()))) {
            throw new IllegalArgumentException(
                    "Household preferences must have unique member IDs.");
        }
        hardConstraints = Objects.requireNonNull(
                hardConstraints, "Hard constraints must not be null.");
        Objects.requireNonNull(personalizationSignals,
                "Personalization signals must not be null.");
        LinkedHashMap<UUID, RecipePersonalizationSignals> checkedSignals =
                new LinkedHashMap<>();
        personalizationSignals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    UUID recipeId = entry.getKey();
                    RecipePersonalizationSignals signals = entry.getValue();
                    Objects.requireNonNull(recipeId,
                            "Personalization recipe ID must not be null.");
                    Objects.requireNonNull(signals,
                            "Personalization signals must not be null.");
                    if (!recipeId.equals(signals.recipeId())) {
                        throw new IllegalArgumentException(
                                "Personalization signal key must match its recipe ID.");
                    }
                    checkedSignals.put(recipeId, signals);
                });
        personalizationSignals = Collections.unmodifiableMap(checkedSignals);
    }

    /** Backward-compatible R0 context without persisted personalization inputs. */
    public RecommendationContext(
            RecommendationRequest request,
            List<InventoryItem> inventorySnapshot,
            TastePreferenceProfile tastePreferences,
            List<HouseholdMemberPreference> householdPreferences,
            RecommendationConstraints hardConstraints) {
        this(request, inventorySnapshot, tastePreferences, householdPreferences,
                hardConstraints, Map.of());
    }

    /** Creates a context containing only servings and the current inventory snapshot. */
    public static RecommendationContext pantryOnly(
            int servingCount, List<InventoryItem> inventorySnapshot) {
        return new RecommendationContext(
                RecommendationRequest.forServings(servingCount), inventorySnapshot,
                TastePreferenceProfile.empty(), List.of(), RecommendationConstraints.none(),
                Map.of());
    }

    /** Returns this request context enriched with one immutable personalization snapshot. */
    public RecommendationContext withPersonalization(
            Map<UUID, RecipePersonalizationSignals> signals) {
        return new RecommendationContext(request, inventorySnapshot, tastePreferences,
                householdPreferences, hardConstraints, signals);
    }

    public Optional<RecipePersonalizationSignals> personalizationFor(UUID recipeId) {
        return Optional.ofNullable(personalizationSignals.get(
                Objects.requireNonNull(recipeId, "Recipe ID must not be null.")));
    }

    private static <T> List<T> immutableList(List<T> values, String label) {
        Objects.requireNonNull(values, label + " must not be null.");
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(label + " must not contain null values.");
        }
        return List.copyOf(values);
    }
}
