package de.mealdeal.catalog;

import de.mealdeal.domain.Unit;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable standard ingredient term used as a reference for local ingredients. */
public record CatalogIngredient(
        String catalogId,
        String displayName,
        List<String> aliases,
        CatalogIngredientCategory category,
        List<Unit> typicalUnits) implements CatalogEntry {

    private static final Pattern ID_PATTERN = Pattern.compile("ingredient\\.[a-z0-9_]+");

    public CatalogIngredient {
        catalogId = requireCatalogId(catalogId);
        displayName = requireText(displayName, "Catalog ingredient name must not be blank.");
        aliases = validatedAliases(displayName, aliases);
        category = Objects.requireNonNull(category, "Catalog category must not be null.");
        typicalUnits = validatedUnits(typicalUnits);
    }

    private static String requireCatalogId(String value) {
        if (value == null || !ID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Catalog ingredient ID must match ingredient.<stable_key>.");
        }
        return value;
    }

    private static List<String> validatedAliases(String displayName, List<String> aliases) {
        Objects.requireNonNull(aliases, "Catalog aliases must not be null.");
        Set<String> normalized = new HashSet<>();
        normalized.add(CatalogTextNormalizer.normalize(displayName));
        return aliases.stream().map(alias -> requireText(
                        alias, "Catalog alias must not be blank."))
                .peek(alias -> {
                    if (!normalized.add(CatalogTextNormalizer.normalize(alias))) {
                        throw new IllegalArgumentException(
                                "Catalog aliases must be semantically distinct.");
                    }
                }).toList();
    }

    private static List<Unit> validatedUnits(List<Unit> units) {
        Objects.requireNonNull(units, "Typical units must not be null.");
        List<Unit> result = List.copyOf(units);
        if (result.stream().anyMatch(Objects::isNull)
                || Set.copyOf(result).size() != result.size()) {
            throw new IllegalArgumentException("Typical units must be non-null and unique.");
        }
        return result;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.strip();
    }
}
