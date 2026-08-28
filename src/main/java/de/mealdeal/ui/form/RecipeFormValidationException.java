package de.mealdeal.ui.form;

import java.util.List;

/** Contains understandable validation messages for the create-recipe form. */
public final class RecipeFormValidationException extends RuntimeException {

    private final List<String> errors;

    /** Creates an exception from one or more user-facing validation errors. */
    public RecipeFormValidationException(List<String> errors) {
        super(message(errors));
        this.errors = List.copyOf(errors);
    }

    /** Returns all validation errors in display order. */
    public List<String> getErrors() {
        return errors;
    }

    private static String message(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException("Validation errors must not be empty.");
        }
        return String.join(" ", errors);
    }
}
