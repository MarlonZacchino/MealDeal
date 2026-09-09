package de.mealdeal.service.recommendation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Versioned experimental weights, fairness parameters, and qualitative score bands. */
public final class RecommendationScoringProfile {

    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;
    private final String version;
    private final Map<RecommendationSignal, BigDecimal> weights;
    private final BigDecimal verySuitableThreshold;
    private final BigDecimal goodSuitableThreshold;
    private final BigDecimal suitableThreshold;
    private final BigDecimal leastMiseryWeight;
    private final BigDecimal strongRejectionThreshold;
    private final BigDecimal strongRejectionScoreCap;

    /** Creates a fully explicit profile so experiments remain centralized and testable. */
    public RecommendationScoringProfile(
            String version,
            Map<RecommendationSignal, BigDecimal> weights,
            BigDecimal verySuitableThreshold,
            BigDecimal goodSuitableThreshold,
            BigDecimal suitableThreshold,
            BigDecimal leastMiseryWeight,
            BigDecimal strongRejectionThreshold,
            BigDecimal strongRejectionScoreCap) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Scoring profile version must not be blank.");
        }
        this.version = version.strip();
        this.weights = validateWeights(weights);
        this.verySuitableThreshold = normalized(
                verySuitableThreshold, "Very-suitable threshold");
        this.goodSuitableThreshold = normalized(
                goodSuitableThreshold, "Good-suitable threshold");
        this.suitableThreshold = normalized(suitableThreshold, "Suitable threshold");
        if (this.verySuitableThreshold.compareTo(this.goodSuitableThreshold) <= 0
                || this.goodSuitableThreshold.compareTo(this.suitableThreshold) <= 0) {
            throw new IllegalArgumentException(
                    "Recommendation band thresholds must be strictly descending.");
        }
        this.leastMiseryWeight = normalized(leastMiseryWeight, "Least-misery weight");
        this.strongRejectionThreshold = normalized(
                strongRejectionThreshold, "Strong-rejection threshold");
        this.strongRejectionScoreCap = normalized(
                strongRejectionScoreCap, "Strong-rejection score cap");
    }

    /** Returns the initial pantry-first experiment profile. */
    public static RecommendationScoringProfile v1() {
        EnumMap<RecommendationSignal, BigDecimal> weights =
                new EnumMap<>(RecommendationSignal.class);
        weights.put(RecommendationSignal.PANTRY_COVERAGE, new BigDecimal("0.35"));
        weights.put(RecommendationSignal.MISSING_INGREDIENT_PENALTY, new BigDecimal("0.15"));
        weights.put(RecommendationSignal.TASTE_AFFINITY, new BigDecimal("0.15"));
        weights.put(RecommendationSignal.DESIRED_INGREDIENT_FIT, new BigDecimal("0.05"));
        weights.put(RecommendationSignal.PREPARATION_TIME_FIT, new BigDecimal("0.10"));
        weights.put(RecommendationSignal.INGREDIENT_ALTERNATIVE_FIT, BigDecimal.ZERO);
        weights.put(RecommendationSignal.RECENT_MEAL_PENALTY, new BigDecimal("0.05"));
        weights.put(RecommendationSignal.VARIETY_SCORE, new BigDecimal("0.05"));
        weights.put(RecommendationSignal.RECIPE_PREFERENCE, new BigDecimal("0.05"));
        weights.put(RecommendationSignal.HOUSEHOLD_PREFERENCE, new BigDecimal("0.10"));
        return new RecommendationScoringProfile(
                "V1.1", weights,
                new BigDecimal("0.80"), new BigDecimal("0.65"), new BigDecimal("0.50"),
                new BigDecimal("0.70"), new BigDecimal("0.20"), new BigDecimal("0.39"));
    }

    public String version() {
        return version;
    }

    public Map<RecommendationSignal, BigDecimal> weights() {
        return weights;
    }

    public BigDecimal weightOf(RecommendationSignal signal) {
        return weights.getOrDefault(
                Objects.requireNonNull(signal, "Recommendation signal must not be null."),
                BigDecimal.ZERO);
    }

    /** Aggregates only available signals and renormalizes their configured weights. */
    public BigDecimal aggregate(RecommendationSignals signals) {
        Objects.requireNonNull(signals, "Recommendation signals must not be null.");
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal activeWeight = BigDecimal.ZERO;
        for (var entry : signals.asMap().entrySet()) {
            BigDecimal weight = weightOf(entry.getKey());
            if (weight.signum() == 0) {
                continue;
            }
            BigDecimal benefit = isPenalty(entry.getKey())
                    ? BigDecimal.ONE.subtract(entry.getValue()) : entry.getValue();
            weightedSum = weightedSum.add(benefit.multiply(weight));
            activeWeight = activeWeight.add(weight);
        }
        if (activeWeight.signum() == 0) {
            throw new IllegalArgumentException("At least one weighted signal must be available.");
        }
        return weightedSum.divide(activeWeight, CALCULATION_CONTEXT);
    }

    /** Combines member scores with minimum-first fairness instead of a plain average. */
    public BigDecimal aggregateHousehold(List<BigDecimal> memberScores) {
        if (memberScores == null || memberScores.isEmpty()) {
            throw new IllegalArgumentException("Household scores must not be empty.");
        }
        List<BigDecimal> checked = memberScores.stream()
                .map(score -> normalized(score, "Household member score"))
                .toList();
        BigDecimal minimum = checked.stream().min(BigDecimal::compareTo).orElseThrow();
        BigDecimal average = checked.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(checked.size()), CALCULATION_CONTEXT);
        return minimum.multiply(leastMiseryWeight)
                .add(average.multiply(BigDecimal.ONE.subtract(leastMiseryWeight)));
    }

    public boolean isStrongRejection(BigDecimal normalizedAffinity) {
        return normalized(normalizedAffinity, "Normalized affinity")
                .compareTo(strongRejectionThreshold) <= 0;
    }

    public BigDecimal applyStrongRejectionCap(BigDecimal score) {
        return normalized(score, "Recommendation score").min(strongRejectionScoreCap);
    }

    public RecommendationBand bandFor(BigDecimal score) {
        BigDecimal checked = normalized(score, "Recommendation score");
        if (checked.compareTo(verySuitableThreshold) >= 0) {
            return RecommendationBand.SEHR_PASSEND;
        }
        if (checked.compareTo(goodSuitableThreshold) >= 0) {
            return RecommendationBand.GUT_PASSEND;
        }
        if (checked.compareTo(suitableThreshold) >= 0) {
            return RecommendationBand.PASSEND;
        }
        return RecommendationBand.WENIGER_PASSEND;
    }

    private static boolean isPenalty(RecommendationSignal signal) {
        return signal == RecommendationSignal.MISSING_INGREDIENT_PENALTY
                || signal == RecommendationSignal.RECENT_MEAL_PENALTY;
    }

    private static Map<RecommendationSignal, BigDecimal> validateWeights(
            Map<RecommendationSignal, BigDecimal> weights) {
        Objects.requireNonNull(weights, "Recommendation weights must not be null.");
        EnumMap<RecommendationSignal, BigDecimal> checked =
                new EnumMap<>(RecommendationSignal.class);
        for (RecommendationSignal signal : RecommendationSignal.values()) {
            BigDecimal weight = Objects.requireNonNull(
                    weights.get(signal), "Missing weight for " + signal + ".");
            if (weight.signum() < 0) {
                throw new IllegalArgumentException("Recommendation weights must not be negative.");
            }
            checked.put(signal, weight);
        }
        if (checked.values().stream().allMatch(weight -> weight.signum() == 0)) {
            throw new IllegalArgumentException(
                    "At least one recommendation weight must be positive.");
        }
        return Map.copyOf(checked);
    }

    private static BigDecimal normalized(BigDecimal value, String label) {
        Objects.requireNonNull(value, label + " must not be null.");
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(label + " must be between 0 and 1.");
        }
        return value;
    }
}
