package de.mealdeal.ui.controller;

import de.mealdeal.domain.RecipeFeedback;
import de.mealdeal.domain.RecipeFeedbackValue;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.util.Optional;
import java.util.Objects;
import java.util.function.Consumer;

/** Shared explicit feedback controls; persistence stays with the calling workflow. */
final class RecipeFeedbackView extends VBox {

    RecipeFeedbackView(Optional<RecipeFeedback> feedback,
                       Consumer<RecipeFeedbackValue> update, Runnable clear) {
        super(8);
        Optional<RecipeFeedbackValue> activeValue = feedback.flatMap(RecipeFeedback::getValue);
        ToggleButton like = button("Gefällt mir", () -> toggle(
                RecipeFeedbackValue.LIKE, activeValue, update, clear));
        ToggleButton dislike = button("Gefällt mir nicht", () -> toggle(
                RecipeFeedbackValue.DISLIKE, activeValue, update, clear));
        activeValue.ifPresent(value -> {
            ToggleButton active = value == RecipeFeedbackValue.LIKE ? like : dislike;
            active.getStyleClass().add("recipe-feedback-active");
            active.setSelected(true);
            active.setText("✓ " + active.getText());
            active.setAccessibleText(active.getText() + ", aktuelle Bewertung");
        });
        FlowPane actions = new FlowPane(10, 10, like, dislike);
        actions.getStyleClass().add("recommendation-feedback-actions");
        getChildren().add(actions);
        feedback.filter(value -> value.getRating().isPresent()).ifPresent(value -> {
            int rating = value.getRating().orElseThrow();
            Label ratingLabel = new Label(ratingText(rating));
            ratingLabel.getStyleClass().add("secondary-text");
            getChildren().add(ratingLabel);
            if (activeValue.isEmpty()) {
                Button clearRating = new Button("Bewertung entfernen");
                clearRating.getStyleClass().add("tertiary-button");
                clearRating.setOnAction(ignored -> clear.run());
                getChildren().add(clearRating);
            }
        });
    }

    private static void toggle(RecipeFeedbackValue selected,
                               Optional<RecipeFeedbackValue> active,
                               Consumer<RecipeFeedbackValue> update,
                               Runnable clear) {
        if (clearsOnSelection(active, selected)) {
            clear.run();
        } else {
            update.accept(selected);
        }
    }

    static boolean clearsOnSelection(Optional<RecipeFeedbackValue> active,
                                     RecipeFeedbackValue selected) {
        return Objects.requireNonNull(active, "Active feedback must not be null.")
                .filter(Objects.requireNonNull(selected, "Selected feedback must not be null.")::equals)
                .isPresent();
    }

    static String ratingText(int rating) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Recipe rating must be between 1 and 5.");
        }
        return "Bewertung: " + rating + " von 5";
    }

    private static ToggleButton button(String text, Runnable action) {
        ToggleButton button = new ToggleButton(text);
        button.getStyleClass().add("secondary-button");
        button.setOnAction(ignored -> action.run());
        return button;
    }
}
