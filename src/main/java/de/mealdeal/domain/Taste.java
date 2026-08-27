package de.mealdeal.domain;

import java.util.Objects;
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

    /**
     * Creates a taste with a new technical identity.
     *
     * @param name the taste name
     */
    public Taste(String name) {
        this(UUID.randomUUID(), name);
    }

    /**
     * Recreates a taste with an existing technical identity.
     *
     * @param id the stable technical identity
     * @param name the taste name
     */
    public Taste(UUID id, String name) {
        this.id = Objects.requireNonNull(id, "Taste ID must not be null.");
        this.name = requireNonBlank(name, "Taste name must not be blank.");
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
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
}
