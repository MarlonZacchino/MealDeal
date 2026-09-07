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

class RecommendationContractTest {

    @Test
    void requestRequiresPositiveServingsAndTime() {
        assertThrows(IllegalArgumentException.class,
                () -> RecommendationRequest.forServings(0));
        assertThrows(IllegalArgumentException.class,
                () -> new RecommendationRequest(2, Optional.of(Duration.ZERO), Optional.empty()));
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
    void v1WeightsAreCentralAndSumToOne() {
        RecommendationScoringProfile profile = RecommendationScoringProfile.v1();

        assertEquals("V1", profile.version());
        assertEquals(0, profile.weights().values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(BigDecimal.ONE));
        assertEquals(new BigDecimal("0.35"),
                profile.weightOf(RecommendationSignal.PANTRY_COVERAGE));
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
