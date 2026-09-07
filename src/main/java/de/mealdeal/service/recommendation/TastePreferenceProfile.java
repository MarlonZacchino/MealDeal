package de.mealdeal.service.recommendation;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Explicit taste affinities in the inclusive range {@code -1..1}. */
public record TastePreferenceProfile(Map<UUID, BigDecimal> affinities) {

    private static final BigDecimal MINIMUM = BigDecimal.ONE.negate();
    private static final BigDecimal MAXIMUM = BigDecimal.ONE;

    public TastePreferenceProfile {
        Objects.requireNonNull(affinities, "Taste affinities must not be null.");
        Map<UUID, BigDecimal> checked = new LinkedHashMap<>();
        affinities.forEach((tasteId, affinity) -> {
            Objects.requireNonNull(tasteId, "Taste ID must not be null.");
            Objects.requireNonNull(affinity, "Taste affinity must not be null.");
            if (affinity.compareTo(MINIMUM) < 0 || affinity.compareTo(MAXIMUM) > 0) {
                throw new IllegalArgumentException("Taste affinity must be between -1 and 1.");
            }
            checked.put(tasteId, affinity);
        });
        affinities = Map.copyOf(checked);
    }

    public static TastePreferenceProfile empty() {
        return new TastePreferenceProfile(Map.of());
    }

    public Optional<BigDecimal> affinityFor(UUID tasteId) {
        return Optional.ofNullable(affinities.get(
                Objects.requireNonNull(tasteId, "Taste ID must not be null.")));
    }
}
