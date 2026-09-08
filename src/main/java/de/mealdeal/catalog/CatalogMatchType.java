package de.mealdeal.catalog;

/** The first successful deterministic tier of a catalog lookup. */
public enum CatalogMatchType {
    CATALOG_ID,
    EXACT_CANONICAL_NAME,
    EXACT_ALIAS,
    NORMALIZED_CANONICAL_NAME,
    NORMALIZED_ALIAS,
    AMBIGUOUS,
    UNKNOWN
}
