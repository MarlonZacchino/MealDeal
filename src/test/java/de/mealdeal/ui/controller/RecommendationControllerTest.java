package de.mealdeal.ui.controller;

import de.mealdeal.service.recommendation.RecommendationReasonCode;
import de.mealdeal.domain.RecipeFeedbackValue;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationControllerTest {

    @Test
    void servingInputRejectsBlankFractionalAndOutOfRangeValuesInGerman() {
        for (String input : List.of("", " ", "abc", "0", "-1", "1000", "2,5")) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> RecommendationController.parseServings(input));
            assertTrue(error.getMessage().contains("Personenzahl"));
        }
        assertEquals(2, RecommendationController.parseServings(" 2 "));
        assertEquals(999, RecommendationController.parseServings("999"));
    }

    @Test
    void timeWarningRemainsVisibleAfterFourPositiveReasons() {
        var reasons = RecommendationResultCardFactory.visibleReasons(List.of(
                RecommendationReasonCode.PANTRY_FULL_COVERAGE,
                RecommendationReasonCode.ALTERNATIVE_AVAILABLE,
                RecommendationReasonCode.ALTERNATIVE_IMPROVES_COVERAGE,
                RecommendationReasonCode.TASTE_STRONG_MATCH,
                RecommendationReasonCode.TIME_OVER_LIMIT));

        assertEquals(4, reasons.size());
        assertTrue(reasons.contains(RecommendationResultCardFactory.reason(
                RecommendationReasonCode.TIME_OVER_LIMIT).orElseThrow()));
    }

    @Test
    void duplicateHouseholdExplanationDoesNotConsumeTwoReasonSlots() {
        var reasons = RecommendationResultCardFactory.visibleReasons(List.of(
                RecommendationReasonCode.HOUSEHOLD_CONFLICT,
                RecommendationReasonCode.HOUSEHOLD_MEMBER_DISLIKES));
        assertEquals(1, reasons.size());
    }

    @Test
    void missingHistoryIsDescribedAsMissingRecordRatherThanNeverCooked() {
        String text = RecommendationResultCardFactory.reason(
                RecommendationReasonCode.RECIPE_NEVER_COOKED).orElseThrow().text();
        assertTrue(text.contains("erfasst"));
        assertFalse(text.contains("nie gekocht"));
    }

    @Test
    void optionalMaximumTimeAcceptsBlankAndPositiveWholeMinutes() {
        assertTrue(RecommendationController.parseMaximumTime("  ").isEmpty());
        assertEquals(Duration.ofMinutes(45),
                RecommendationController.parseMaximumTime(" 45 ").orElseThrow());
    }

    @Test
    void optionalMaximumTimeRejectsNonPositiveAndFractionalValues() {
        assertThrows(IllegalArgumentException.class,
                () -> RecommendationController.parseMaximumTime("0"));
        assertThrows(IllegalArgumentException.class,
                () -> RecommendationController.parseMaximumTime("-5"));
        assertThrows(IllegalArgumentException.class,
                () -> RecommendationController.parseMaximumTime("1,5"));
    }

    @Test
    void presentationMapsUserFacingReasonsWithoutRawScores() {
        var positive = RecommendationResultCardFactory.reason(
                RecommendationReasonCode.PANTRY_FULL_COVERAGE).orElseThrow();
        var negative = RecommendationResultCardFactory.reason(
                RecommendationReasonCode.RECENTLY_COOKED).orElseThrow();

        assertFalse(positive.negative());
        assertTrue(negative.negative());
        assertFalse(positive.text().contains("%"));
        assertFalse(positive.text().matches(".*\\b0[,.]\\d+.*"));
        assertTrue(RecommendationResultCardFactory.reason(
                RecommendationReasonCode.RECIPE_HARD_EXCLUDED).isEmpty());
    }

    @Test
    void activeFeedbackSelectionClearsWhileOtherSelectionReplacesIt() {
        assertTrue(RecipeFeedbackView.clearsOnSelection(
                Optional.of(RecipeFeedbackValue.LIKE), RecipeFeedbackValue.LIKE));
        assertFalse(RecipeFeedbackView.clearsOnSelection(
                Optional.of(RecipeFeedbackValue.LIKE), RecipeFeedbackValue.DISLIKE));
        assertFalse(RecipeFeedbackView.clearsOnSelection(
                Optional.empty(), RecipeFeedbackValue.LIKE));
    }

    @Test
    void existingNumericRatingKeepsAConciseNonRedundantPresentation() {
        assertEquals("Bewertung: 3 von 5", RecipeFeedbackView.ratingText(3));
        assertThrows(IllegalArgumentException.class, () -> RecipeFeedbackView.ratingText(0));
    }

    @Test
    void desiredIngredientReasonsAreConciseAndUserFacing() {
        assertTrue(RecommendationResultCardFactory.reason(
                RecommendationReasonCode.DESIRED_INGREDIENT_MATCH).orElseThrow()
                .text().contains("Wunschzutaten"));
        assertTrue(RecommendationResultCardFactory.reason(
                RecommendationReasonCode.DESIRED_INGREDIENT_PARTIAL_MATCH).orElseThrow()
                .text().contains("Teil"));
    }
}
