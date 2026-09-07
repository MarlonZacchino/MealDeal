package de.mealdeal.service.recommendation;

import de.mealdeal.domain.DishType;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Inputs that describe one concrete cooking decision. */
public record RecommendationRequest(
        int servingCount,
        Optional<Duration> availableTime,
        Optional<DishType> desiredDishType) {

    public RecommendationRequest {
        if (servingCount <= 0) {
            throw new IllegalArgumentException("Serving count must be greater than zero.");
        }
        availableTime = Objects.requireNonNull(
                availableTime, "Available time must not be null.");
        desiredDishType = Objects.requireNonNull(
                desiredDishType, "Desired dish type must not be null.");
        availableTime.ifPresent(duration -> {
            if (duration.isZero() || duration.isNegative() || duration.getNano() != 0) {
                throw new IllegalArgumentException(
                        "Available time must be positive and use whole seconds.");
            }
        });
    }

    /** Creates an unconstrained request for the given serving count. */
    public static RecommendationRequest forServings(int servingCount) {
        return new RecommendationRequest(servingCount, Optional.empty(), Optional.empty());
    }
}
