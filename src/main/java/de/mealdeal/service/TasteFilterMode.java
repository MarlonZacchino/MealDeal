package de.mealdeal.service;

/** Fixed strategies available for filtering recipes by taste. */
public enum TasteFilterMode {
    /** Every selected taste must be present. */
    AND,
    /** At least one selected taste must be present. */
    OR,
    /** Non-zero matches are ordered by their number of matching tastes. */
    RANKING
}
