package de.mealdeal.ui;

/** Reports an FXML or controller loading problem with its resource context. */
public final class ViewLoadingException extends RuntimeException {

    /** Creates an exception with a resource-specific message. */
    public ViewLoadingException(String message) {
        super(message);
    }

    /** Creates an exception that retains the underlying loading failure. */
    public ViewLoadingException(String message, Throwable cause) {
        super(message, cause);
    }
}
