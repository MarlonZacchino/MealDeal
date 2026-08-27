package de.mealdeal.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * A centrally managed ingredient that can be used by multiple recipes.
 *
 * <p>The UUID provides technical identity without coupling the domain model to
 * a database ID. It remains stable if the ingredient is renamed.</p>
 */
public final class Ingredient {

    private final UUID id;
    private final String name;

    /**
     * Creates an ingredient with a new technical identity.
     *
     * @param name the ingredient name
     */
    public Ingredient(String name) {
        this(UUID.randomUUID(), name);
    }

    /**
     * Recreates an ingredient with an existing technical identity.
     *
     * @param id the stable technical identity
     * @param name the ingredient name
     */
    public Ingredient(UUID id, String name) {
        this.id = Objects.requireNonNull(id, "Ingredient ID must not be null.");
        this.name = requireNonBlank(name, "Ingredient name must not be blank.");
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
        if (!(other instanceof Ingredient ingredient)) {
            return false;
        }
        return id.equals(ingredient.id);
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
