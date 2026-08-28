package de.mealdeal;

/** Reports a technical failure while preparing the local application environment. */
public final class ApplicationStartupException extends RuntimeException {

    /** Creates an exception with a clear startup message. */
    public ApplicationStartupException(String message) {
        super(message);
    }

    /** Creates an exception that retains the underlying startup failure. */
    public ApplicationStartupException(String message, Throwable cause) {
        super(message, cause);
    }
}
