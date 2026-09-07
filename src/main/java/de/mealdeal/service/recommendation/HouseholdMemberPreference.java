package de.mealdeal.service.recommendation;

import java.util.Objects;

/** Temporary per-request preferences and hard constraints of one household member. */
public record HouseholdMemberPreference(
        String memberId,
        TastePreferenceProfile tastePreferences,
        RecommendationConstraints hardConstraints) {

    public HouseholdMemberPreference {
        if (memberId == null || memberId.isBlank()) {
            throw new IllegalArgumentException("Household member ID must not be blank.");
        }
        memberId = memberId.strip();
        tastePreferences = Objects.requireNonNull(
                tastePreferences, "Taste preferences must not be null.");
        hardConstraints = Objects.requireNonNull(
                hardConstraints, "Hard constraints must not be null.");
    }
}
