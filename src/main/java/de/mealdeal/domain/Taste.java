package de.mealdeal.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An extensible taste assigned to recipes.
 *
 * <p>This is a regular domain object rather than an enum so users can add new
 * tastes later. Its UUID supplies identity independently of its editable name
 * and of any persistence technology.</p>
 */
public final class Taste {

    private final UUID id;
    private final String name;
    private final String catalogId;

    /**
     * Creates a taste with a new technical identity.
     *
     * @param name the taste name
     */
    public Taste(String name) {
        this(UUID.randomUUID(), name, null);
    }

    /** Creates a local taste with an optional standard-catalog reference. */
    public Taste(String name, String catalogId) {
        this(UUID.randomUUID(), name, catalogId);
    }

    /**
     * Recreates a taste with an existing technical identity.
     *
     * @param id the stable technical identity
     * @param name the taste name
     */
    public Taste(UUID id, String name) {
        this(id, name, null);
    }

    /** Recreates a local taste with its optional semantic catalog link. */
    public Taste(UUID id, String name, String catalogId) {
        this.id = Objects.requireNonNull(id, "Taste ID must not be null.");
        this.name = requireNonBlank(name, "Taste name must not be blank.");
        this.catalogId = optionalCatalogId(catalogId);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** Returns the optional standard semantic reference, never a local identity. */
    public Optional<String> getCatalogId() {
        return Optional.ofNullable(catalogId);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Taste taste)) {
            return false;
        }
        return id.equals(taste.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.strip();
    }

    private static String optionalCatalogId(String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("Taste catalog ID must not be blank.");
        }
        return value.strip();
    }
}
