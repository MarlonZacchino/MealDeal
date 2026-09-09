package de.mealdeal.ui.controller;

import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeFeedback;
import de.mealdeal.domain.RecipeFeedbackValue;
import de.mealdeal.service.recommendation.RecipeRecommendation;
import de.mealdeal.service.recommendation.RecommendationReasonCode;
import de.mealdeal.service.recommendation.RecommendationSession;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Objects;
import java.util.Optional;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Builds compact recommendation cards from already ranked presentation data. */
final class RecommendationResultCardFactory {

    private static final int MAX_VISIBLE_REASONS = 4;
    private final Consumer<Recipe> openAction;
    private final Consumer<Recipe> dismissAction;
    private final BiConsumer<Recipe, RecipeFeedbackValue> feedbackAction;
    private final Consumer<Recipe> clearFeedbackAction;
    private final Consumer<Recipe> cookedAction;

    RecommendationResultCardFactory(
            Consumer<Recipe> openAction,
            Consumer<Recipe> dismissAction,
            BiConsumer<Recipe, RecipeFeedbackValue> feedbackAction,
            Consumer<Recipe> clearFeedbackAction,
            Consumer<Recipe> cookedAction) {
        this.openAction = Objects.requireNonNull(openAction, "Open action must not be null.");
        this.dismissAction = Objects.requireNonNull(
                dismissAction, "Dismiss action must not be null.");
        this.feedbackAction = Objects.requireNonNull(
                feedbackAction, "Feedback action must not be null.");
        this.clearFeedbackAction = Objects.requireNonNull(
                clearFeedbackAction, "Clear-feedback action must not be null.");
        this.cookedAction = Objects.requireNonNull(
                cookedAction, "Cooked action must not be null.");
    }

    VBox create(
            RecommendationSession session,
            RecipeRecommendation recommendation) {
        Objects.requireNonNull(session, "Recommendation session must not be null.");
        Objects.requireNonNull(recommendation, "Recommendation must not be null.");
        Recipe recipe = recommendation.recipe();

        Label name = new Label(recipe.getName());
        name.setWrapText(true);
        name.setMinWidth(0);
        name.setMaxWidth(Double.MAX_VALUE);
        name.getStyleClass().add("recipe-name");
        Label type = new Label(GermanRecipeDisplay.dishType(recipe.getDishType()));
        type.getStyleClass().add("recipe-type-badge");
        HBox.setHgrow(name, Priority.ALWAYS);
        HBox header = new HBox(12, name, type);
        header.setAlignment(Pos.CENTER_LEFT);

        String duration = recommendation.relevantDuration()
                .map(GermanRecipeDisplay::duration)
                .map(value -> "Zeit: " + value)
                .orElse("Keine Zeitangabe");
        Label facts = new Label(duration + "  ·  Für " + session.requestedServings()
                + (session.requestedServings() == 1 ? " Person" : " Personen"));
        facts.setWrapText(true);
        facts.getStyleClass().add("recipe-facts");

        VBox reasons = new VBox(6);
        visibleReasons(recommendation.reasonCodes()).stream()
                .map(RecommendationResultCardFactory::reasonLabel)
                .forEach(reasons.getChildren()::add);

        Button open = button("Gericht öffnen", "primary-button",
                () -> openAction.accept(recipe));
        Button dismiss = button("Gerade nicht", "tertiary-button",
                () -> dismissAction.accept(recipe));
        RecipeFeedbackView feedback = new RecipeFeedbackView(session.feedbackFor(recipe.getId()),
                value -> feedbackAction.accept(recipe, value), () -> clearFeedbackAction.accept(recipe));

        Button cooked = button(session.isCooked(recipe.getId())
                        ? "Als gekocht gespeichert" : "Als gekocht markieren",
                "secondary-button", () -> cookedAction.accept(recipe));
        cooked.setDisable(session.isCooked(recipe.getId()));

        FlowPane actions = new FlowPane(10, 10, open, cooked);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getStyleClass().add("recommendation-card-actions");

        FlowPane tertiaryActions = new FlowPane(10, 10, dismiss);
        tertiaryActions.setAlignment(Pos.CENTER_LEFT);
        tertiaryActions.getStyleClass().add("recommendation-tertiary-actions");

        VBox card = new VBox(13, header, facts, reasons, actions, feedback, tertiaryActions);
        card.getStyleClass().addAll("content-card", "recommendation-card");
        return card;
    }

    static List<ReasonPresentation> visibleReasons(List<RecommendationReasonCode> codes) {
        // A short list must not hide a time warning behind several positive pantry hints.
        return codes.stream().map(RecommendationResultCardFactory::reason)
                .flatMap(Optional::stream).distinct()
                .sorted(Comparator.comparing(ReasonPresentation::negative).reversed())
                .limit(MAX_VISIBLE_REASONS).toList();
    }

    static Optional<ReasonPresentation> reason(RecommendationReasonCode code) {
        return Optional.ofNullable(switch (code) {
            case PANTRY_FULL_COVERAGE -> positive("Alle benötigten Zutaten sind vorhanden.");
            case PANTRY_MOSTLY_COVERED -> positive("Die benötigten Mengen sind größtenteils gedeckt.");
            case PANTRY_LOW_COVERAGE -> negative("Die benötigten Mengen sind nur teilweise oder gar nicht gedeckt.");
            case MISSING_ONE_INGREDIENT_GROUP -> negative("Für eine Zutat reicht der Vorrat noch nicht aus.");
            case MISSING_MULTIPLE_INGREDIENT_GROUPS -> negative("Für mehrere Zutaten reicht der Vorrat noch nicht aus.");
            case ALTERNATIVE_AVAILABLE -> positive("Eine passende Zutatenalternative ist verfügbar.");
            case ALTERNATIVE_IMPROVES_COVERAGE -> positive("Eine Alternative passt zu deinem Inventar.");
            case TASTE_STRONG_MATCH -> positive("Passt sehr gut zu deinem Geschmack.");
            case TASTE_MATCH -> positive("Passt zu deinem Geschmack.");
            case TASTE_MISMATCH -> negative("Passt weniger zu deinem gewählten Geschmack.");
            case DESIRED_INGREDIENT_MATCH -> positive("Enthält deine Wunschzutaten.");
            case DESIRED_INGREDIENT_PARTIAL_MATCH -> positive(
                    "Enthält einen Teil deiner Wunschzutaten.");
            case TIME_WITHIN_LIMIT -> positive("Passt in deine verfügbare Zeit.");
            case TIME_OVER_LIMIT -> negative("Dauert länger als deine verfügbare Zeit.");
            case TIME_UNKNOWN -> negative("Für dieses Gericht fehlt eine Zeitangabe.");
            case RECIPE_NEVER_COOKED -> positive("Bisher nicht als gekocht erfasst.");
            case RECIPE_NOT_COOKED_RECENTLY -> positive("Lange nicht gekocht.");
            case RECIPE_COOKED_TODAY -> negative("Heute bereits gekocht.");
            case RECENTLY_COOKED -> negative("Erst kürzlich gekocht.");
            case RECIPE_EXPLICITLY_LIKED, RECIPE_STRONGLY_LIKED ->
                    positive("Du magst dieses Gericht.");
            case RECIPE_EXPLICITLY_DISLIKED, RECIPE_STRONGLY_DISLIKED ->
                    negative("Du magst dieses Gericht weniger.");
            case RECIPE_REPEATEDLY_SELECTED -> positive("Von dir wiederholt ausgewählt.");
            case RECIPE_REPEATEDLY_DISMISSED -> negative("Von dir wiederholt für diesmal ausgeblendet.");
            case HOUSEHOLD_STRONG_MATCH -> positive("Passt gut für euren Haushalt.");
            case HOUSEHOLD_CONFLICT, HOUSEHOLD_MEMBER_DISLIKES ->
                    negative("Im Haushalt gibt es unterschiedliche Vorlieben.");
            case HARD_EXCLUDED_ALTERNATIVE_IGNORED ->
                    positive("Eine ausgeschlossene Alternative wird nicht verwendet.");
            case VARIETY_BONUS, RECIPE_HARD_EXCLUDED,
                    INGREDIENT_GROUP_HARD_EXCLUDED, DISH_TYPE_MISMATCH,
                    MISSING_REQUIRED_INGREDIENT_STRUCTURE -> null;
        });
    }

    private static Label reasonLabel(ReasonPresentation reason) {
        Label label = new Label((reason.negative() ? "⚠ " : "✓ ") + reason.text());
        label.setWrapText(true);
        label.getStyleClass().add(reason.negative()
                ? "recommendation-reason-negative" : "recommendation-reason-positive");
        return label;
    }

    private static Button button(String text, String styleClass, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add(styleClass);
        button.setOnAction(ignored -> action.run());
        return button;
    }

    private static ReasonPresentation positive(String text) {
        return new ReasonPresentation(text, false);
    }

    private static ReasonPresentation negative(String text) {
        return new ReasonPresentation(text, true);
    }

    record ReasonPresentation(String text, boolean negative) {
    }
}
