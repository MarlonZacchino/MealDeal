package de.mealdeal.ui.form;

import java.util.List;
import java.util.UUID;

/** JavaFX-independent input for one ordered group of ingredient alternatives. */
public record IngredientGroupFormInput(
        UUID groupId,
        List<IngredientOptionFormInput> options,
        UUID standardOptionId) {

    public IngredientGroupFormInput {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
