package de.mealdeal.service.recommendation;

/** Independent normalized inputs used by the recommendation scoring profile. */
public enum RecommendationSignal {
    PANTRY_COVERAGE,
    MISSING_INGREDIENT_PENALTY,
    TASTE_AFFINITY,
    DESIRED_INGREDIENT_FIT,
    PREPARATION_TIME_FIT,
    INGREDIENT_ALTERNATIVE_FIT,
    RECENT_MEAL_PENALTY,
    VARIETY_SCORE,
    RECIPE_PREFERENCE,
    HOUSEHOLD_PREFERENCE
}
