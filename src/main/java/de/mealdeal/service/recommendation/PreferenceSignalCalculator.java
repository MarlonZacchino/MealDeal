package de.mealdeal.service.recommendation;

import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.Taste;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Calculates individual taste affinity and hybrid household fairness. */
final class PreferenceSignalCalculator {

    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;
    private final RecommendationScoringProfile scoringProfile;

    PreferenceSignalCalculator(RecommendationScoringProfile scoringProfile) {
        this.scoringProfile = scoringProfile;
    }

    Optional<BigDecimal> individualScore(TastePreferenceProfile profile, Recipe recipe) {
        List<BigDecimal> matches = normalizedMatches(profile, recipe);
        return matches.isEmpty() ? Optional.empty() : Optional.of(average(matches));
    }

    HouseholdResult householdScore(
            Recipe recipe, List<HouseholdMemberPreference> members) {
        List<BigDecimal> memberScores = new ArrayList<>();
        boolean strongRejection = false;
        for (HouseholdMemberPreference member : members) {
            List<BigDecimal> affinities = normalizedMatches(member.tastePreferences(), recipe);
            if (affinities.isEmpty()) {
                continue;
            }
            memberScores.add(average(affinities));
            if (affinities.stream().anyMatch(scoringProfile::isStrongRejection)) {
                strongRejection = true;
            }
        }
        Optional<BigDecimal> score = memberScores.isEmpty()
                ? Optional.empty()
                : Optional.of(scoringProfile.aggregateHousehold(memberScores));
        return new HouseholdResult(score, strongRejection);
    }

    private static List<BigDecimal> normalizedMatches(
            TastePreferenceProfile profile, Recipe recipe) {
        return recipe.getTastes().stream()
                .map(Taste::getId)
                .map(profile::affinityFor)
                .flatMap(Optional::stream)
                .map(PreferenceSignalCalculator::normalize)
                .toList();
    }

    private static BigDecimal normalize(BigDecimal affinity) {
        return affinity.add(BigDecimal.ONE)
                .divide(BigDecimal.valueOf(2), CALCULATION_CONTEXT);
    }

    private static BigDecimal average(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), CALCULATION_CONTEXT);
    }

    record HouseholdResult(Optional<BigDecimal> score, boolean strongRejection) {
    }
}
