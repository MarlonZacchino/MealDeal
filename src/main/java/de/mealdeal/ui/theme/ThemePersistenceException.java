package de.mealdeal.ui.theme;

/** Reports a failure while reading or writing the local theme preference. */
public final class ThemePersistenceException extends RuntimeException {

    public ThemePersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
