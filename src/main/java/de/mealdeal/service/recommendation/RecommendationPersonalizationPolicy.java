package de.mealdeal.service.recommendation;

import de.mealdeal.domain.RecipeFeedback;
import de.mealdeal.domain.RecommendationAction;
import de.mealdeal.domain.RecommendationInteraction;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Pure V1 mappings from R2 history and feedback data to bounded scorer inputs. */
public final class RecommendationPersonalizationPolicy {

    public static final Duration RECENCY_WINDOW = Duration.ofDays(14);
    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;
    private static final BigDecimal INTERACTION_STRENGTH = new BigDecimal("0.5");

    /** Returns linear freshness from zero now to one after fourteen days or no history. */
    public BigDecimal freshness(Optional<Instant> lastCookedAt, Instant now) {
        Objects.requireNonNull(lastCookedAt, "Last-cooked timestamp must not be null.");
        Objects.requireNonNull(now, "Current timestamp must not be null.");
        if (lastCookedAt.isEmpty()) {
            return BigDecimal.ONE;
        }
        long elapsedSeconds = Duration.between(lastCookedAt.orElseThrow(), now).getSeconds();
        if (elapsedSeconds <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal ratio = BigDecimal.valueOf(elapsedSeconds).divide(
                BigDecimal.valueOf(RECENCY_WINDOW.toSeconds()), CALCULATION_CONTEXT);
        return ratio.min(BigDecimal.ONE);
    }

    /** Classifies only explanation thresholds; scoring remains continuous. */
    public RecipeRecency recency(
            Optional<Instant> lastCookedAt, Instant now, ZoneId zone) {
        Objects.requireNonNull(lastCookedAt, "Last-cooked timestamp must not be null.");
        Objects.requireNonNull(now, "Current timestamp must not be null.");
        Objects.requireNonNull(zone, "Clock zone must not be null.");
        if (lastCookedAt.isEmpty()) {
            return RecipeRecency.NEVER_COOKED;
        }
        Instant last = lastCookedAt.orElseThrow();
        LocalDate currentDate = LocalDate.ofInstant(now, zone);
        if (LocalDate.ofInstant(last, zone).equals(currentDate)) {
            return RecipeRecency.COOKED_TODAY;
        }
        Duration elapsed = Duration.between(last, now);
        if (elapsed.isNegative() || elapsed.compareTo(Duration.ofDays(7)) < 0) {
            return RecipeRecency.COOKED_RECENTLY;
        }
        if (elapsed.compareTo(RECENCY_WINDOW) < 0) {
            return RecipeRecency.MID_WINDOW;
        }
        return RecipeRecency.NOT_COOKED_RECENTLY;
    }

    /** Maps explicit Recipe feedback to {@code -1..1}; absence remains unavailable. */
    public Optional<BigDecimal> explicitPreference(Optional<RecipeFeedback> feedback) {
        Objects.requireNonNull(feedback, "Recipe feedback must not be null.");
        return feedback.map(value -> {
            if (value.getRating().isPresent()) {
                return switch (value.getRating().getAsInt()) {
                    case 1 -> new BigDecimal("-1.00");
                    case 2 -> new BigDecimal("-0.50");
                    case 3 -> new BigDecimal("0.00");
                    case 4 -> new BigDecimal("0.50");
                    case 5 -> new BigDecimal("1.00");
                    default -> throw new IllegalStateException("Recipe rating is outside 1..5.");
                };
            }
            return switch (value.getValue().orElseThrow()) {
                case LIKE -> new BigDecimal("0.75");
                case DISLIKE -> new BigDecimal("-0.75");
            };
        });
    }

    /** Aggregates only SELECTED and DISMISSED; SHOWN is intentionally neutral. */
    public InteractionPreference aggregateInteractions(
            List<RecommendationInteraction> interactions) {
        Objects.requireNonNull(interactions, "Recommendation interactions must not be null.");
        if (interactions.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Recommendation interactions must not contain null values.");
        }
        int selected = Math.toIntExact(interactions.stream()
                .filter(event -> event.getAction() == RecommendationAction.SELECTED).count());
        int dismissed = Math.toIntExact(interactions.stream()
                .filter(event -> event.getAction() == RecommendationAction.DISMISSED).count());
        int evidence = Math.addExact(selected, dismissed);
        if (evidence == 0) {
            return new InteractionPreference(BigDecimal.ZERO, selected, dismissed);
        }
        BigDecimal raw = BigDecimal.valueOf((long) selected - dismissed)
                .divide(BigDecimal.valueOf((long) evidence + 2), CALCULATION_CONTEXT);
        return new InteractionPreference(
                INTERACTION_STRENGTH.multiply(raw), selected, dismissed);
    }

    /** Bounded implicit result plus evidence counts used for honest explainability. */
    public record InteractionPreference(
            BigDecimal value, int selectedCount, int dismissedCount) {

        public InteractionPreference {
            Objects.requireNonNull(value, "Interaction preference must not be null.");
            if (value.compareTo(INTERACTION_STRENGTH.negate()) < 0
                    || value.compareTo(INTERACTION_STRENGTH) > 0) {
                throw new IllegalArgumentException(
                        "Interaction preference must be between -0.5 and 0.5.");
            }
            if (selectedCount < 0 || dismissedCount < 0) {
                throw new IllegalArgumentException(
                        "Interaction counts must not be negative.");
            }
        }
    }
}
