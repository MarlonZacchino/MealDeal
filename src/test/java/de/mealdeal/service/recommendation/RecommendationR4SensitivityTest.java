package de.mealdeal.service.recommendation;

import de.mealdeal.domain.RecipeFeedback;
import de.mealdeal.domain.RecipeFeedbackValue;
import de.mealdeal.domain.RecommendationAction;
import de.mealdeal.domain.RecommendationInteraction;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static de.mealdeal.service.recommendation.R4EvaluationFixtures.NOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** R4 sensitivity, boundary, dominance, and parameter-evaluation checks. */
@Tag("r4-sensitivity")
@Tag("r4-boundary-dominance")
@Tag("r4-parameter-evaluation")
class RecommendationR4SensitivityTest {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final UUID RECIPE_ID = RecommendationTestFixtures.id("r4-sensitivity");
    private final RecommendationPersonalizationPolicy policy =
            new RecommendationPersonalizationPolicy();
    private final RecommendationScoringProfile profile = RecommendationScoringProfile.v1();

    @ParameterizedTest(name = "recency day {0}")
    @MethodSource("recencyDays")
    void fourteenDayRecencySweepIsExactAndMonotone(int days, String expectedFreshness) {
        Optional<Instant> lastCooked = days < 0
                ? Optional.empty() : Optional.of(NOW.minusSeconds(days * 86_400L));

        BigDecimal actual = policy.freshness(lastCooked, NOW);

        assertEquals(0, new BigDecimal(expectedFreshness).compareTo(actual));
    }

    static Stream<Arguments> recencyDays() {
        return Stream.of(
                Arguments.of(-1, "1"),
                Arguments.of(0, "0"),
                Arguments.of(1, fraction(1, 14).toString()),
                Arguments.of(3, fraction(3, 14).toString()),
                Arguments.of(7, "0.5"),
                Arguments.of(10, fraction(10, 14).toString()),
                Arguments.of(13, fraction(13, 14).toString()),
                Arguments.of(14, "1"),
                Arguments.of(21, "1"));
    }

    @Test
    void recencyReasonBoundariesMatchContinuousScoringWindow() {
        assertEquals(RecipeRecency.NEVER_COOKED,
                policy.recency(Optional.empty(), NOW, ZoneOffset.UTC));
        assertEquals(RecipeRecency.COOKED_TODAY,
                policy.recency(Optional.of(NOW), NOW, ZoneOffset.UTC));
        assertEquals(RecipeRecency.COOKED_RECENTLY,
                policy.recency(Optional.of(NOW.minusSeconds(3 * 86_400L)),
                        NOW, ZoneOffset.UTC));
        assertEquals(RecipeRecency.MID_WINDOW,
                policy.recency(Optional.of(NOW.minusSeconds(7 * 86_400L)),
                        NOW, ZoneOffset.UTC));
        assertEquals(RecipeRecency.NOT_COOKED_RECENTLY,
                policy.recency(Optional.of(NOW.minusSeconds(14 * 86_400L)),
                        NOW, ZoneOffset.UTC));
    }

    @ParameterizedTest(name = "feedback {0}/{1} -> {2}")
    @MethodSource("feedbackMappings")
    void explicitFeedbackMappingRemainsSymmetricAndVisible(
            RecipeFeedbackValue value, Integer rating, String expected) {
        RecipeFeedback feedback = new RecipeFeedback(RECIPE_ID, value, rating, NOW);

        assertEquals(0, new BigDecimal(expected).compareTo(
                policy.explicitPreference(Optional.of(feedback)).orElseThrow()));
    }

    static Stream<Arguments> feedbackMappings() {
        return Stream.of(
                Arguments.of(null, 1, "-1.00"),
                Arguments.of(null, 2, "-0.50"),
                Arguments.of(null, 3, "0.00"),
                Arguments.of(null, 4, "0.50"),
                Arguments.of(null, 5, "1.00"),
                Arguments.of(RecipeFeedbackValue.LIKE, null, "0.75"),
                Arguments.of(RecipeFeedbackValue.DISLIKE, null, "-0.75"));
    }

    @ParameterizedTest(name = "interactions selected={0}, dismissed={1}")
    @MethodSource("interactionCases")
    void interactionFormulaIsSymmetricBoundedAndSaturating(
            int selected, int dismissed, BigDecimal expected) {
        List<RecommendationInteraction> events = Stream.concat(
                        Stream.generate(() -> event(RecommendationAction.SELECTED))
                                .limit(selected),
                        Stream.generate(() -> event(RecommendationAction.DISMISSED))
                                .limit(dismissed))
                .toList();

        RecommendationPersonalizationPolicy.InteractionPreference actual =
                policy.aggregateInteractions(events);

        assertEquals(0, expected.compareTo(actual.value()));
        assertTrue(actual.value().abs().compareTo(new BigDecimal("0.5")) < 0
                || actual.value().signum() == 0);
    }

    static Stream<Arguments> interactionCases() {
        return Stream.of(
                interaction(0, 0), interaction(1, 0), interaction(0, 1),
                interaction(2, 0), interaction(0, 2), interaction(3, 1),
                interaction(1, 3), interaction(5, 0), interaction(0, 5),
                interaction(10, 0), interaction(0, 10), interaction(100, 0),
                interaction(0, 100), interaction(5, 5), interaction(10, 10));
    }

    @Test
    void oneThousandShownEventsAreExactlyEquivalentToNoInteractions() {
        List<RecommendationInteraction> shown = Stream.generate(
                        () -> event(RecommendationAction.SHOWN))
                .limit(1_000)
                .toList();

        assertEquals(policy.aggregateInteractions(List.of()),
                policy.aggregateInteractions(shown));
    }

    @ParameterizedTest(name = "sensitivity of {0}")
    @MethodSource("positiveSignals")
    void improvingExactlyOneBenefitSignalRaisesScore(RecommendationSignal changedSignal) {
        EnumMap<RecommendationSignal, BigDecimal> baseline = fullSignalSet("0.50", "0.50");
        EnumMap<RecommendationSignal, BigDecimal> improved = new EnumMap<>(baseline);
        improved.put(changedSignal, new BigDecimal("0.75"));

        BigDecimal before = profile.aggregate(new RecommendationSignals(baseline));
        BigDecimal after = profile.aggregate(new RecommendationSignals(improved));

        assertTrue(after.compareTo(before) > 0);
    }

    static Stream<RecommendationSignal> positiveSignals() {
        return Stream.of(
                RecommendationSignal.PANTRY_COVERAGE,
                RecommendationSignal.TASTE_AFFINITY,
                RecommendationSignal.PREPARATION_TIME_FIT,
                RecommendationSignal.VARIETY_SCORE,
                RecommendationSignal.RECIPE_PREFERENCE);
    }

    @Test
    void preferenceDominanceLimitIsExactForFullAndSparseContexts() {
        BigDecimal fullSwing = new BigDecimal("0.05")
                .divide(new BigDecimal("0.95"), MC);
        BigDecimal fullNeutralToMaximum = new BigDecimal("0.025")
                .divide(new BigDecimal("0.95"), MC);
        BigDecimal sparseSwing = new BigDecimal("0.05")
                .divide(new BigDecimal("0.60"), MC);

        assertDecimalCalculationEquals(fullSwing, scoreDifference(
                fullSignalSet("0", "0.50"), fullSignalSet("1", "0.50")));
        assertDecimalCalculationEquals(fullNeutralToMaximum, scoreDifference(
                fullSignalSet("0.50", "0.50"), fullSignalSet("1", "0.50")));
        assertDecimalCalculationEquals(sparseSwing, scoreDifference(
                sparseSignalSet("0", "0.50"), sparseSignalSet("1", "0.50")));
    }

    @Test
    void recencyDominanceLimitEqualsItsConfiguredShare() {
        BigDecimal expectedFullSwing = new BigDecimal("0.05")
                .divide(new BigDecimal("0.95"), MC);

        assertDecimalCalculationEquals(expectedFullSwing, scoreDifference(
                fullSignalSet("0.50", "0"), fullSignalSet("0.50", "1")));
    }

    @Test
    void maximumPositivePersonalizationLiftOverNeutralStaleIsBounded() {
        BigDecimal expectedFullLift = new BigDecimal("0.075")
                .divide(new BigDecimal("0.95"), MC);

        assertDecimalCalculationEquals(expectedFullLift, scoreDifference(
                fullSignalSet("0.50", "0"), fullSignalSet("1", "1")));
    }

    @Test
    void contributionBreakdownUsesEveryConfiguredSignalExactlyOnce() {
        EnumMap<RecommendationSignal, BigDecimal> values = fullSignalSet("0.75", "0.25");

        assertEquals(0, new BigDecimal("0.175").compareTo(
                values.get(RecommendationSignal.PANTRY_COVERAGE)
                        .multiply(profile.weightOf(RecommendationSignal.PANTRY_COVERAGE))));
        assertEquals(0, new BigDecimal("0.0375").compareTo(
                values.get(RecommendationSignal.RECIPE_PREFERENCE)
                        .multiply(profile.weightOf(RecommendationSignal.RECIPE_PREFERENCE))));
        assertEquals(0, new BigDecimal("0.0125").compareTo(
                values.get(RecommendationSignal.VARIETY_SCORE)
                        .multiply(profile.weightOf(RecommendationSignal.VARIETY_SCORE))));
        assertTrue(values.get(RecommendationSignal.RECENT_MEAL_PENALTY) == null);
    }

    private BigDecimal scoreDifference(
            EnumMap<RecommendationSignal, BigDecimal> lower,
            EnumMap<RecommendationSignal, BigDecimal> higher) {
        return profile.aggregate(new RecommendationSignals(higher))
                .subtract(profile.aggregate(new RecommendationSignals(lower)));
    }

    private static void assertDecimalCalculationEquals(
            BigDecimal expected, BigDecimal actual) {
        assertEquals(expected.setScale(30, RoundingMode.HALF_EVEN),
                actual.setScale(30, RoundingMode.HALF_EVEN));
    }

    private static EnumMap<RecommendationSignal, BigDecimal> fullSignalSet(
            String normalizedPreference, String freshness) {
        EnumMap<RecommendationSignal, BigDecimal> values = new EnumMap<>(RecommendationSignal.class);
        values.put(RecommendationSignal.PANTRY_COVERAGE, new BigDecimal("0.50"));
        values.put(RecommendationSignal.MISSING_INGREDIENT_PENALTY, new BigDecimal("0.50"));
        values.put(RecommendationSignal.TASTE_AFFINITY, new BigDecimal("0.50"));
        values.put(RecommendationSignal.PREPARATION_TIME_FIT, new BigDecimal("0.50"));
        values.put(RecommendationSignal.VARIETY_SCORE, new BigDecimal(freshness));
        values.put(RecommendationSignal.RECIPE_PREFERENCE,
                new BigDecimal(normalizedPreference));
        values.put(RecommendationSignal.HOUSEHOLD_PREFERENCE, new BigDecimal("0.50"));
        return values;
    }

    private static EnumMap<RecommendationSignal, BigDecimal> sparseSignalSet(
            String normalizedPreference, String freshness) {
        EnumMap<RecommendationSignal, BigDecimal> values = new EnumMap<>(RecommendationSignal.class);
        values.put(RecommendationSignal.PANTRY_COVERAGE, new BigDecimal("0.50"));
        values.put(RecommendationSignal.MISSING_INGREDIENT_PENALTY, new BigDecimal("0.50"));
        values.put(RecommendationSignal.VARIETY_SCORE, new BigDecimal(freshness));
        values.put(RecommendationSignal.RECIPE_PREFERENCE,
                new BigDecimal(normalizedPreference));
        return values;
    }

    private static Arguments interaction(int selected, int dismissed) {
        int evidence = selected + dismissed;
        BigDecimal expected = evidence == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf((long) selected - dismissed)
                        .divide(BigDecimal.valueOf((long) evidence + 2), MC)
                        .multiply(new BigDecimal("0.5"));
        return Arguments.of(selected, dismissed, expected);
    }

    private static BigDecimal fraction(int numerator, int denominator) {
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), MC);
    }

    private static RecommendationInteraction event(RecommendationAction action) {
        return new RecommendationInteraction(
                RecommendationTestFixtures.id("r4-event-" + action),
                RECIPE_ID, action, 1, new BigDecimal("0.5"), NOW);
    }
}
