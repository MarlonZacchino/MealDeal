package de.mealdeal.service.recommendation;

import de.mealdeal.domain.RecipeFeedback;
import de.mealdeal.domain.RecipeFeedbackValue;
import de.mealdeal.domain.RecommendationAction;
import de.mealdeal.domain.RecommendationInteraction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationPersonalizationPolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");
    private static final UUID RECIPE_ID = RecommendationTestFixtures.id("r3-policy-recipe");
    private final RecommendationPersonalizationPolicy policy =
            new RecommendationPersonalizationPolicy();

    @ParameterizedTest
    @MethodSource("recencyCases")
    void freshnessUsesLinearFourteenDayWindow(
            Optional<Instant> lastCookedAt, String expected) {
        assertEquals(0, new BigDecimal(expected).compareTo(
                policy.freshness(lastCookedAt, NOW)));
    }

    static Stream<Arguments> recencyCases() {
        return Stream.of(
                Arguments.of(Optional.empty(), "1"),
                Arguments.of(Optional.of(NOW), "0"),
                Arguments.of(Optional.of(NOW.minusSeconds(86_400)),
                        new BigDecimal("1").divide(new BigDecimal("14"),
                                java.math.MathContext.DECIMAL128).toString()),
                Arguments.of(Optional.of(NOW.minusSeconds(7 * 86_400)), "0.5"),
                Arguments.of(Optional.of(NOW.minusSeconds(14 * 86_400)), "1"),
                Arguments.of(Optional.of(NOW.minusSeconds(20 * 86_400)), "1"));
    }

    @Test
    void freshnessIsMonotoneAsLastMealGetsOlder() {
        List<BigDecimal> values = List.of(0, 1, 7, 14, 30).stream()
                .map(days -> policy.freshness(
                        Optional.of(NOW.minusSeconds(days * 86_400L)), NOW))
                .toList();

        for (int index = 1; index < values.size(); index++) {
            assertTrue(values.get(index).compareTo(values.get(index - 1)) >= 0);
        }
    }

    @Test
    void explanationClassificationUsesClockZoneAndDocumentedThresholds() {
        assertEquals(RecipeRecency.NEVER_COOKED,
                policy.recency(Optional.empty(), NOW, ZoneOffset.UTC));
        assertEquals(RecipeRecency.COOKED_TODAY,
                policy.recency(Optional.of(NOW.minusSeconds(60)), NOW, ZoneOffset.UTC));
        assertEquals(RecipeRecency.COOKED_RECENTLY,
                policy.recency(Optional.of(NOW.minusSeconds(2 * 86_400)),
                        NOW, ZoneOffset.UTC));
        assertEquals(RecipeRecency.MID_WINDOW,
                policy.recency(Optional.of(NOW.minusSeconds(7 * 86_400)),
                        NOW, ZoneOffset.UTC));
        assertEquals(RecipeRecency.NOT_COOKED_RECENTLY,
                policy.recency(Optional.of(NOW.minusSeconds(14 * 86_400)),
                        NOW, ZoneOffset.UTC));
    }

    @ParameterizedTest
    @MethodSource("feedbackCases")
    void explicitFeedbackUsesTransparentMapping(
            RecipeFeedbackValue value, Integer rating, String expected) {
        RecipeFeedback feedback = new RecipeFeedback(
                RECIPE_ID, value, rating, NOW);

        assertEquals(0, new BigDecimal(expected).compareTo(
                policy.explicitPreference(Optional.of(feedback)).orElseThrow()));
    }

    static Stream<Arguments> feedbackCases() {
        return Stream.of(
                Arguments.of(RecipeFeedbackValue.DISLIKE, null, "-0.75"),
                Arguments.of(RecipeFeedbackValue.LIKE, null, "0.75"),
                Arguments.of(null, 1, "-1.00"),
                Arguments.of(null, 2, "-0.50"),
                Arguments.of(null, 3, "0.00"),
                Arguments.of(null, 4, "0.50"),
                Arguments.of(null, 5, "1.00"),
                Arguments.of(RecipeFeedbackValue.LIKE, 5, "1.00"));
    }

    @Test
    void missingFeedbackIsUnavailableInsteadOfInvented() {
        assertTrue(policy.explicitPreference(Optional.empty()).isEmpty());
    }

    @Test
    void interactionsAreWeakBoundedAndIgnoreShown() {
        assertInteraction("0", List.of());
        assertInteraction("0", List.of(event(RecommendationAction.SHOWN)));
        BigDecimal oneEvent = BigDecimal.ONE.divide(BigDecimal.valueOf(3),
                java.math.MathContext.DECIMAL128).multiply(new BigDecimal("0.5"));
        assertInteraction(oneEvent, List.of(event(RecommendationAction.SELECTED)));
        assertInteraction(oneEvent.negate(), List.of(event(RecommendationAction.DISMISSED)));
        assertInteraction("0", List.of(
                event(RecommendationAction.SELECTED), event(RecommendationAction.DISMISSED)));

        var severalSelected = policy.aggregateInteractions(List.of(
                event(RecommendationAction.SELECTED), event(RecommendationAction.SELECTED),
                event(RecommendationAction.SELECTED), event(RecommendationAction.SHOWN)));
        var manySelected = policy.aggregateInteractions(java.util.stream.IntStream.range(0, 100)
                .mapToObj(ignored -> event(RecommendationAction.SELECTED)).toList());
        var severalDismissed = policy.aggregateInteractions(List.of(
                event(RecommendationAction.DISMISSED),
                event(RecommendationAction.DISMISSED),
                event(RecommendationAction.DISMISSED)));

        assertTrue(severalSelected.value().compareTo(new BigDecimal("0.1667")) > 0);
        assertTrue(severalDismissed.value().compareTo(new BigDecimal("-0.1667")) < 0);
        assertTrue(manySelected.value().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(manySelected.value().compareTo(new BigDecimal("0.5")) < 0);
    }

    private void assertInteraction(String expected, List<RecommendationInteraction> events) {
        assertInteraction(new BigDecimal(expected), events);
    }

    private void assertInteraction(
            BigDecimal expected, List<RecommendationInteraction> events) {
        assertEquals(0, expected.compareTo(
                policy.aggregateInteractions(events).value()));
    }

    private static RecommendationInteraction event(RecommendationAction action) {
        return new RecommendationInteraction(
                UUID.randomUUID(), RECIPE_ID, action, 1, new BigDecimal("0.5"), NOW);
    }
}
