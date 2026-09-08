package de.mealdeal.service.recommendation;

/** Coarse, deterministic V1 explanation state for the continuous freshness signal. */
public enum RecipeRecency {
    NEVER_COOKED,
    COOKED_TODAY,
    COOKED_RECENTLY,
    MID_WINDOW,
    NOT_COOKED_RECENTLY
}
