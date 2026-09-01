package de.mealdeal.domain;

import java.util.Objects;
import java.util.UUID;

/** One centrally persisted category used to organize ingredients. */
public final class IngredientCategory {

    private final UUID id;
    private final String name;
    private final int position;

    /** Creates a category with a new stable identity. */
    public IngredientCategory(String name, int position) {
        this(UUID.randomUUID(), name, position);
    }

    /** Recreates a category with its persisted identity and order. */
    public IngredientCategory(UUID id, String name, int position) {
        this.id = Objects.requireNonNull(id, "Ingredient category ID must not be null.");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Ingredient category name must not be blank.");
        }
        if (position < 0) {
            throw new IllegalArgumentException(
                    "Ingredient category position must not be negative.");
        }
        this.name = name.strip();
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getPosition() {
        return position;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof IngredientCategory category
                && id.equals(category.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
