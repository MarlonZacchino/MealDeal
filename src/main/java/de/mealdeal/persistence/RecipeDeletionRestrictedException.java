package de.mealdeal.persistence;

/** Reports that a recipe cannot be deleted because another stored record references it. */
public final class RecipeDeletionRestrictedException extends PersistenceException {

    public RecipeDeletionRestrictedException(String message, Throwable cause) {
        super(message, cause);
    }
}
