package de.mealdeal.service.recommendation;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Available normalized signal values; an absent entry means data was unavailable. */
public final class RecommendationSignals {

    private final Map<RecommendationSignal, BigDecimal> values;

    public RecommendationSignals(Map<RecommendationSignal, BigDecimal> values) {
        Objects.requireNonNull(values, "Recommendation signals must not be null.");
        EnumMap<RecommendationSignal, BigDecimal> checked =
                new EnumMap<>(RecommendationSignal.class);
        values.forEach((signal, value) -> {
            Objects.requireNonNull(signal, "Recommendation signal must not be null.");
            Objects.requireNonNull(value, "Recommendation signal value must not be null.");
            if (value.compareTo(BigDecimal.ZERO) < 0
                    || value.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException(
                        "Recommendation signal values must be between 0 and 1.");
            }
            checked.put(signal, value);
        });
        this.values = Map.copyOf(checked);
    }

    public Optional<BigDecimal> valueOf(RecommendationSignal signal) {
        return Optional.ofNullable(values.get(
                Objects.requireNonNull(signal, "Recommendation signal must not be null.")));
    }

    public Map<RecommendationSignal, BigDecimal> asMap() {
        return values;
    }
}
