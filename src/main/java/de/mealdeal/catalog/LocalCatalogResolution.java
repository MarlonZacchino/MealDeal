package de.mealdeal.catalog;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of looking for an existing local entity for one standard term.
 * Ambiguous local data is reported but never merged automatically.
 */
public record LocalCatalogResolution<C extends CatalogEntry, L>(
        C catalogEntry, LocalCatalogMatchType matchType, List<L> localCandidates) {

    public LocalCatalogResolution {
        catalogEntry = Objects.requireNonNull(catalogEntry, "Catalog entry must not be null.");
        matchType = Objects.requireNonNull(matchType, "Local match type must not be null.");
        localCandidates = List.copyOf(Objects.requireNonNull(
                localCandidates, "Local candidates must not be null."));
    }

    /** Returns a local entity only for one unambiguous candidate. */
    public Optional<L> existingLocalEntity() {
        return matchType != LocalCatalogMatchType.AMBIGUOUS && localCandidates.size() == 1
                ? Optional.of(localCandidates.getFirst()) : Optional.empty();
    }
}
