package de.mealdeal.persistence;

/**
 * Reports a technical persistence failure without exposing JDBC details to
 * callers of repository interfaces.
 */
public class PersistenceException extends RuntimeException {

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }

    public PersistenceException(String message) {
        super(message);
    }
}
