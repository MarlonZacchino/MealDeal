package de.mealdeal.catalog;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Resolves catalog IDs and explicit names without fuzzy or substring equality. */
public final class CatalogMatcher {

    private CatalogMatcher() {
    }

    /** Applies the documented catalog matching tiers in strict priority order. */
    public static <T extends CatalogEntry> CatalogMatch<T> resolve(
            List<T> entries, String input) {
        Objects.requireNonNull(entries, "Catalog entries must not be null.");
        if (input == null || input.isBlank()) {
            return new CatalogMatch<>(CatalogMatchType.UNKNOWN, List.of());
        }
        CatalogMatch<T> result = match(entries, CatalogMatchType.CATALOG_ID,
                entry -> entry.catalogId().equals(input));
        if (result != null) {
            return result;
        }
        result = match(entries, CatalogMatchType.EXACT_CANONICAL_NAME,
                entry -> entry.displayName().equals(input));
        if (result != null) {
            return result;
        }
        result = match(entries, CatalogMatchType.EXACT_ALIAS,
                entry -> entry.aliases().contains(input));
        if (result != null) {
            return result;
        }
        String normalizedInput = CatalogTextNormalizer.normalize(input);
        result = match(entries, CatalogMatchType.NORMALIZED_CANONICAL_NAME,
                entry -> CatalogTextNormalizer.normalize(entry.displayName())
                        .equals(normalizedInput));
        if (result != null) {
            return result;
        }
        result = match(entries, CatalogMatchType.NORMALIZED_ALIAS,
                entry -> entry.aliases().stream()
                        .map(CatalogTextNormalizer::normalize)
                        .anyMatch(normalizedInput::equals));
        return result == null
                ? new CatalogMatch<>(CatalogMatchType.UNKNOWN, List.of()) : result;
    }

    private static <T extends CatalogEntry> CatalogMatch<T> match(
            List<T> entries, CatalogMatchType type, Predicate<T> predicate) {
        List<T> candidates = entries.stream().filter(Objects::nonNull).filter(predicate)
                .sorted(Comparator.comparing(CatalogEntry::catalogId)).toList();
        if (candidates.isEmpty()) {
            return null;
        }
        return new CatalogMatch<>(candidates.size() == 1 ? type : CatalogMatchType.AMBIGUOUS,
                candidates);
    }
}
