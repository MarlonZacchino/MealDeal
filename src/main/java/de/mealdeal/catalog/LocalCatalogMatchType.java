package de.mealdeal.catalog;

/** Match tier between a standard catalog entry and local user-owned data. */
public enum LocalCatalogMatchType {
    CATALOG_ID,
    EXACT_CANONICAL_NAME,
    EXACT_ALIAS,
    NORMALIZED_CANONICAL_NAME,
    NORMALIZED_ALIAS,
    AMBIGUOUS,
    NONE
}
