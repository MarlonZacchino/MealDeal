package de.mealdeal.persistence;

/** Reports a category name that already exists ignoring letter case. */
public final class DuplicateIngredientCategoryException extends PersistenceException {

    public DuplicateIngredientCategoryException(String message) {
        super(message);
    }
}
