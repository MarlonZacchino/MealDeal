package de.mealdeal.service.recommendation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationContractTest {

    @Test
    void requestRequiresPositiveServingsAndWholeSecondTime() {
        assertThrows(IllegalArgumentException.class,
                () -> RecommendationRequest.forServings(0));
        assertThrows(IllegalArgumentException.class,
                () -> new RecommendationRequest(2, Optional.of(Duration.ZERO), Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new RecommendationRequest(
                        2, Optional.of(Duration.ofSeconds(-1)), Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new RecommendationRequest(
                        2, Optional.of(Duration.ofMillis(500)), Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new RecommendationRequest(
                        2, Optional.of(Duration.ofMillis(1500)), Optional.empty()));

        RecommendationRequest valid = new RecommendationRequest(
                2, Optional.of(Duration.ofSeconds(1)), Optional.empty());

        assertEquals(Duration.ofSeconds(1), valid.availableTime().orElseThrow());
    }

    @Test
    void tasteAffinityUsesClosedMinusOneToOneRange() {
        TastePreferenceProfile profile = new TastePreferenceProfile(Map.of(
                RecommendationTestFixtures.id("taste"), new BigDecimal("-1")));

        assertEquals(new BigDecimal("-1"),
                profile.affinityFor(RecommendationTestFixtures.id("taste")).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> new TastePreferenceProfile(Map.of(
                RecommendationTestFixtures.id("invalid"), new BigDecimal("1.01"))));
    }

    @Test
    void v1WeightsKeepR0ValuesAndUseReservedShareForRecipePreference() {
        RecommendationScoringProfile profile = RecommendationScoringProfile.v1();

        assertEquals("V1", profile.version());
        assertEquals(0, profile.weights().values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(BigDecimal.ONE));
        assertEquals(new BigDecimal("0.35"),
                profile.weightOf(RecommendationSignal.PANTRY_COVERAGE));
        assertEquals(new BigDecimal("0.05"),
                profile.weightOf(RecommendationSignal.RECIPE_PREFERENCE));
        assertEquals(0, profile.weightOf(RecommendationSignal.INGREDIENT_ALTERNATIVE_FIT)
                .compareTo(BigDecimal.ZERO));
    }

    @Test
    void unavailableSignalsAreOmittedAndActiveWeightsAreRenormalized() {
        EnumMap<RecommendationSignal, BigDecimal> values =
                new EnumMap<>(RecommendationSignal.class);
        values.put(RecommendationSignal.PANTRY_COVERAGE, BigDecimal.ONE);
        values.put(RecommendationSignal.MISSING_INGREDIENT_PENALTY, BigDecimal.ZERO);

        BigDecimal score = RecommendationScoringProfile.v1()
                .aggregate(new RecommendationSignals(values));

        assertEquals(0, score.compareTo(BigDecimal.ONE));
    }

    @Test
    void activeWeightRenormalizationUsesOnlyAvailablePositiveWeights() {
        RecommendationScoringProfile profile = RecommendationScoringProfile.v1();
        EnumMap<RecommendationSignal, BigDecimal> values =
                new EnumMap<>(RecommendationSignal.class);
        values.put(RecommendationSignal.PANTRY_COVERAGE, new BigDecimal("0.40"));
        values.put(RecommendationSignal.MISSING_INGREDIENT_PENALTY, new BigDecimal("0.20"));
        values.put(RecommendationSignal.TASTE_AFFINITY, new BigDecimal("0.80"));
        values.put(RecommendationSignal.INGREDIENT_ALTERNATIVE_FIT, BigDecimal.ZERO);

        BigDecimal allAvailable = profile.aggregate(new RecommendationSignals(values));
        BigDecimal expectedAll = new BigDecimal("0.38")
                .divide(new BigDecimal("0.65"), java.math.MathContext.DECIMAL128);
        assertEquals(0, expectedAll.compareTo(allAvailable));

        values.remove(RecommendationSignal.TASTE_AFFINITY);
        BigDecimal tasteUnavailable = profile.aggregate(new RecommendationSignals(values));
        assertEquals(0, new BigDecimal("0.52").compareTo(tasteUnavailable));

        values.remove(RecommendationSignal.MISSING_INGREDIENT_PENALTY);
        BigDecimal severalUnavailable = profile.aggregate(new RecommendationSignals(values));
        assertEquals(0, new BigDecimal("0.40").compareTo(severalUnavailable));
    }

    @Test
    void profileRejectsOnlyAnEntirelyUnweightedConfiguration() {
        EnumMap<RecommendationSignal, BigDecimal> weights =
                new EnumMap<>(RecommendationSignal.class);
        for (RecommendationSignal signal : RecommendationSignal.values()) {
            weights.put(signal, BigDecimal.ZERO);
        }

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new RecommendationScoringProfile(
                        "zero", weights,
                        new BigDecimal("0.80"), new BigDecimal("0.65"),
                        new BigDecimal("0.50"), new BigDecimal("0.70"),
                        new BigDecimal("0.20"), new BigDecimal("0.39")));

        assertTrue(exception.getMessage().contains("positive"));
    }

    @Test
    void qualitativeBandsUseExperimentalBoundariesWithoutPercentMeaning() {
        RecommendationScoringProfile profile = RecommendationScoringProfile.v1();

        assertEquals(RecommendationBand.SEHR_PASSEND,
                profile.bandFor(new BigDecimal("0.80")));
        assertEquals(RecommendationBand.GUT_PASSEND,
                profile.bandFor(new BigDecimal("0.65")));
        assertEquals(RecommendationBand.PASSEND,
                profile.bandFor(new BigDecimal("0.50")));
        assertEquals(RecommendationBand.WENIGER_PASSEND,
                profile.bandFor(new BigDecimal("0.49")));
    }

    @Test
    void householdAggregationGivesSeventyPercentWeightToLeastSatisfiedMember() {
        BigDecimal result = RecommendationScoringProfile.v1().aggregateHousehold(
                List.of(new BigDecimal("0.20"), BigDecimal.ONE));

        assertEquals(0, result.compareTo(new BigDecimal("0.32")));
    }

    @Test
    void contextRejectsDuplicateHouseholdMemberIdentities() {
        HouseholdMemberPreference member = new HouseholdMemberPreference(
                "Alex", TastePreferenceProfile.empty(), RecommendationConstraints.none());

        assertThrows(IllegalArgumentException.class, () -> new RecommendationContext(
                RecommendationRequest.forServings(2), List.of(),
                TastePreferenceProfile.empty(), List.of(member, member),
                RecommendationConstraints.none()));
    }
}
