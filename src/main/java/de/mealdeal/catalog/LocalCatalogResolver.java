package de.mealdeal.catalog;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.Taste;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/** Conservatively detects reusable local entities without mutating or merging user data. */
public final class LocalCatalogResolver {

    /** Resolves a standard ingredient against local ingredients in deterministic tiers. */
    public LocalCatalogResolution<CatalogIngredient, Ingredient> resolveIngredient(
            CatalogIngredient entry, List<Ingredient> localIngredients) {
        Objects.requireNonNull(entry, "Catalog ingredient must not be null.");
        return resolve(entry, localIngredients, Ingredient::getId, Ingredient::getName,
                Ingredient::getCatalogId);
    }

    /** Resolves a standard taste against local tastes in deterministic tiers. */
    public LocalCatalogResolution<CatalogTaste, Taste> resolveTaste(
            CatalogTaste entry, List<Taste> localTastes) {
        Objects.requireNonNull(entry, "Catalog taste must not be null.");
        return resolve(entry, localTastes, Taste::getId, Taste::getName, Taste::getCatalogId);
    }

    private static <C extends CatalogEntry, L, I extends Comparable<I>>
            LocalCatalogResolution<C, L> resolve(
                    C entry, List<L> localEntities, Function<L, I> id,
                    Function<L, String> name, Function<L, Optional<String>> catalogId) {
        Objects.requireNonNull(localEntities, "Local entities must not be null.");
        List<L> ordered = localEntities.stream().filter(Objects::nonNull)
                .sorted(Comparator.comparing(id)).toList();
        LocalCatalogResolution<C, L> result = match(entry, ordered,
                LocalCatalogMatchType.CATALOG_ID,
                local -> catalogId.apply(local).filter(entry.catalogId()::equals).isPresent());
        if (result != null) {
            return result;
        }
        result = match(entry, ordered, LocalCatalogMatchType.EXACT_CANONICAL_NAME,
                local -> name.apply(local).equals(entry.displayName()));
        if (result != null) {
            return result;
        }
        result = match(entry, ordered, LocalCatalogMatchType.EXACT_ALIAS,
                local -> entry.aliases().contains(name.apply(local)));
        if (result != null) {
            return result;
        }
        String normalizedCanonical = CatalogTextNormalizer.normalize(entry.displayName());
        result = match(entry, ordered, LocalCatalogMatchType.NORMALIZED_CANONICAL_NAME,
                local -> CatalogTextNormalizer.normalize(name.apply(local))
                        .equals(normalizedCanonical));
        if (result != null) {
            return result;
        }
        result = match(entry, ordered, LocalCatalogMatchType.NORMALIZED_ALIAS,
                local -> entry.aliases().stream().map(CatalogTextNormalizer::normalize)
                        .anyMatch(CatalogTextNormalizer.normalize(name.apply(local))::equals));
        return result == null
                ? new LocalCatalogResolution<>(entry, LocalCatalogMatchType.NONE, List.of())
                : result;
    }

    private static <C extends CatalogEntry, L> LocalCatalogResolution<C, L> match(
            C entry, List<L> localEntities, LocalCatalogMatchType type, Predicate<L> predicate) {
        List<L> candidates = localEntities.stream().filter(predicate).toList();
        if (candidates.isEmpty()) {
            return null;
        }
        return new LocalCatalogResolution<>(entry,
                candidates.size() == 1 ? type : LocalCatalogMatchType.AMBIGUOUS, candidates);
    }
}
