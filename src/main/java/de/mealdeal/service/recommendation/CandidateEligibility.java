package de.mealdeal.service.recommendation;

import java.util.List;
import java.util.Objects;

/** Result of hard candidate checks performed before any ranking score exists. */
public record CandidateEligibility(
        boolean eligible,
        List<RecommendationReasonCode> reasonCodes) {

    public CandidateEligibility {
        Objects.requireNonNull(reasonCodes, "Eligibility reason codes must not be null.");
        if (reasonCodes.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Eligibility reason codes must not contain null values.");
        }
        reasonCodes = List.copyOf(reasonCodes);
        if (eligible && !reasonCodes.isEmpty()) {
            throw new IllegalArgumentException("Eligible candidates must not have exclusion reasons.");
        }
        if (!eligible && reasonCodes.isEmpty()) {
            throw new IllegalArgumentException("Excluded candidates need at least one reason.");
        }
    }

    public static CandidateEligibility allowed() {
        return new CandidateEligibility(true, List.of());
    }

    public static CandidateEligibility excluded(List<RecommendationReasonCode> reasons) {
        return new CandidateEligibility(false, reasons);
    }
}
