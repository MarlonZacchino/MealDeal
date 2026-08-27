package de.mealdeal.service;

/**
 * Human-readable classification of a non-zero search match.
 */
public enum MatchQuality {
    PERFECT,
    GOOD,
    PARTIAL;

    /**
     * Classifies a match using the shared ingredient and taste ranking rules.
     *
     * @param matchedCount number of matched criteria
     * @param selectedCount total number of selected criteria
     * @return matching quality
     */
    public static MatchQuality fromCounts(int matchedCount, int selectedCount) {
        if (selectedCount <= 0) {
            throw new IllegalArgumentException("Selected count must be greater than zero.");
        }
        if (matchedCount <= 0 || matchedCount > selectedCount) {
            throw new IllegalArgumentException(
                    "Matched count must be between 1 and selected count.");
        }
        if (matchedCount == selectedCount) {
            return PERFECT;
        }
        if (matchedCount > selectedCount / 2) {
            return GOOD;
        }
        return PARTIAL;
    }
}
