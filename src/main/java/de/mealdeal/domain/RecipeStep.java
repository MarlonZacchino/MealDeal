package de.mealdeal.domain;

/**
 * One explicitly positioned instruction in a recipe.
 */
public final class RecipeStep {

    private final int position;
    private final String description;

    /**
     * Creates a positioned preparation instruction.
     *
     * @param position the one-based position in the recipe
     * @param description the instruction text
     */
    public RecipeStep(int position, String description) {
        if (position < 1) {
            throw new IllegalArgumentException("Recipe step position must be at least 1.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Recipe step description must not be blank.");
        }

        this.position = position;
        this.description = description.strip();
    }

    public int getPosition() {
        return position;
    }

    public String getDescription() {
        return description;
    }
}
