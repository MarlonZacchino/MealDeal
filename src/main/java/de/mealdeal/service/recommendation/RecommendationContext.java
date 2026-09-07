package de.mealdeal.service.recommendation;

import de.mealdeal.domain.InventoryItem;

import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

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
        RecommendationConstraints hardConstraints) {

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
    }

    /** Creates a context containing only servings and the current inventory snapshot. */
    public static RecommendationContext pantryOnly(
            int servingCount, List<InventoryItem> inventorySnapshot) {
        return new RecommendationContext(
                RecommendationRequest.forServings(servingCount), inventorySnapshot,
                TastePreferenceProfile.empty(), List.of(), RecommendationConstraints.none());
    }

    private static <T> List<T> immutableList(List<T> values, String label) {
        Objects.requireNonNull(values, label + " must not be null.");
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(label + " must not contain null values.");
        }
        return List.copyOf(values);
    }
}
