package de.mealdeal.catalog;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable standard taste term used as a reference for local tastes. */
public record CatalogTaste(
        String catalogId, String displayName, List<String> aliases) implements CatalogEntry {

    private static final Pattern ID_PATTERN = Pattern.compile("taste\\.[a-z0-9_]+");

    public CatalogTaste {
        if (catalogId == null || !ID_PATTERN.matcher(catalogId).matches()) {
            throw new IllegalArgumentException("Catalog taste ID must match taste.<stable_key>.");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Catalog taste name must not be blank.");
        }
        displayName = displayName.strip();
        Objects.requireNonNull(aliases, "Catalog aliases must not be null.");
        Set<String> normalized = new HashSet<>();
        normalized.add(CatalogTextNormalizer.normalize(displayName));
        aliases = aliases.stream().map(alias -> {
            if (alias == null || alias.isBlank()) {
                throw new IllegalArgumentException("Catalog alias must not be blank.");
            }
            return alias.strip();
        }).peek(alias -> {
            if (!normalized.add(CatalogTextNormalizer.normalize(alias))) {
                throw new IllegalArgumentException(
                        "Catalog aliases must be semantically distinct.");
            }
        }).toList();
    }
}
