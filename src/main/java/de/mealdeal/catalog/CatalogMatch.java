package de.mealdeal.catalog;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Deterministic catalog lookup result that never promotes ambiguity to identity. */
public record CatalogMatch<T extends CatalogEntry>(
        CatalogMatchType type, List<T> candidates) {

    public CatalogMatch {
        type = Objects.requireNonNull(type, "Catalog match type must not be null.");
        candidates = List.copyOf(Objects.requireNonNull(
                candidates, "Catalog match candidates must not be null."));
    }

    /** Returns a catalog identity only when exactly one safe candidate was found. */
    public Optional<T> matchedEntry() {
        return type != CatalogMatchType.AMBIGUOUS && type != CatalogMatchType.UNKNOWN
                && candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }
}
