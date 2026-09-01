package de.mealdeal.persistence;

/** Reports an attempted duplicate inventory item for the same ingredient and unit. */
public final class DuplicateInventoryItemException extends PersistenceException {

    public DuplicateInventoryItemException(String message) {
        super(message);
    }
}
