package de.mealdeal.service.recommendation;

import de.mealdeal.domain.Recipe;
import de.mealdeal.service.RecipeScaler;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Persistence-free R0 baseline for deterministic pantry-aware recommendations.
 *
 * <p>Hard constraints remove candidates before this service calculates normalized
 * signals. Missing signal data is omitted and the profile renormalizes only the
 * remaining configured weights.</p>
 */
public final class RecipeRecommendationService {

    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;
    private static final BigDecimal STRONG_MATCH_THRESHOLD = new BigDecimal("0.75");
    private static final BigDecimal NEUTRAL_AFFINITY = new BigDecimal("0.50");

    private static final Comparator<Recipe> RECIPE_ORDER = Comparator
            .comparing(Recipe::getName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Recipe::getName)
            .thenComparing(Recipe::getId);

    private static final Comparator<RecipeRecommendation> RECOMMENDATION_ORDER = Comparator
            .comparing(RecipeRecommendation::score, Comparator.reverseOrder())
            .thenComparingInt(RecipeRecommendation::missingIngredientGroupCount)
            .thenComparing(recommendation -> recommendation.signals()
                            .valueOf(RecommendationSignal.PANTRY_COVERAGE).orElseThrow(),
                    Comparator.reverseOrder())
            .thenComparing(recommendation -> recommendation.relevantDuration().isEmpty())
            .thenComparingLong(recommendation -> recommendation.relevantDuration()
                    .map(Duration::getSeconds).orElse(Long.MAX_VALUE))
            .thenComparing(RecipeRecommendation::recipe, RECIPE_ORDER);

    private final RecommendationScoringProfile scoringProfile;
    private final CandidateEligibilityEvaluator eligibilityEvaluator;
    private final PantrySignalCalculator pantryCalculator;
    private final PreferenceSignalCalculator preferenceCalculator;

    /** Creates the baseline with the experimental V1 scoring profile. */
    public RecipeRecommendationService() {
        this(RecommendationScoringProfile.v1(), new RecipeScaler());
    }

    /** Creates an explicitly versioned and independently testable baseline. */
    public RecipeRecommendationService(
            RecommendationScoringProfile scoringProfile, RecipeScaler recipeScaler) {
        this.scoringProfile = Objects.requireNonNull(
                scoringProfile, "Scoring profile must not be null.");
        RecipeScaler checkedScaler = Objects.requireNonNull(
                recipeScaler, "Recipe scaler must not be null.");
        eligibilityEvaluator = new CandidateEligibilityEvaluator();
        pantryCalculator = new PantrySignalCalculator(checkedScaler);
        preferenceCalculator = new PreferenceSignalCalculator(scoringProfile);
    }

    /** Evaluates eligibility, scores allowed candidates, and returns stable ranking order. */
    public RecommendationOutcome recommend(
            Collection<Recipe> candidates, RecommendationContext context) {
        Objects.requireNonNull(candidates, "Recommendation candidates must not be null.");
        Objects.requireNonNull(context, "Recommendation context must not be null.");
        if (candidates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Recommendation candidates must not contain null values.");
        }

        List<RecipeRecommendation> recommendations = new ArrayList<>();
        List<RecommendationExclusion> exclusions = new ArrayList<>();
        for (Recipe recipe : candidates) {
            CandidateEligibility eligibility = evaluateEligibility(recipe, context);
            if (eligibility.eligible()) {
                recommendations.add(score(recipe, context));
            } else {
                exclusions.add(new RecommendationExclusion(recipe, eligibility.reasonCodes()));
            }
        }
        recommendations.sort(RECOMMENDATION_ORDER);
        exclusions.sort(Comparator.comparing(RecommendationExclusion::recipe, RECIPE_ORDER));
        return new RecommendationOutcome(recommendations, exclusions);
    }

    /** Applies all request and household hard constraints without calculating a score. */
    public CandidateEligibility evaluateEligibility(
            Recipe recipe, RecommendationContext context) {
        return eligibilityEvaluator.evaluate(recipe, context);
    }

    public RecommendationScoringProfile scoringProfile() {
        return scoringProfile;
    }

    private RecipeRecommendation score(Recipe recipe, RecommendationContext context) {
        Set<UUID> excludedIngredients = eligibilityEvaluator.excludedIngredientIds(context);
        PantrySignalCalculator.Result pantry = pantryCalculator.calculate(
                recipe, context, excludedIngredients);
        EnumMap<RecommendationSignal, BigDecimal> values = pantrySignals(pantry);
        LinkedHashSet<RecommendationReasonCode> reasons = pantryReasons(pantry);
        if (eligibilityEvaluator.hasIgnoredExcludedAlternative(recipe, excludedIngredients)) {
            reasons.add(RecommendationReasonCode.HARD_EXCLUDED_ALTERNATIVE_IGNORED);
        }

        preferenceCalculator.individualScore(context.tastePreferences(), recipe)
                .ifPresent(value -> {
                    values.put(RecommendationSignal.TASTE_AFFINITY, value);
                    reasons.add(tasteReason(value));
                });
        addTimeSignal(recipe, context, values, reasons);

        PreferenceSignalCalculator.HouseholdResult household =
                preferenceCalculator.householdScore(recipe, context.householdPreferences());
        household.score().ifPresent(value -> {
            values.put(RecommendationSignal.HOUSEHOLD_PREFERENCE, value);
            if (value.compareTo(STRONG_MATCH_THRESHOLD) >= 0) {
                reasons.add(RecommendationReasonCode.HOUSEHOLD_STRONG_MATCH);
            }
        });
        if (household.strongRejection()) {
            reasons.add(RecommendationReasonCode.HOUSEHOLD_MEMBER_DISLIKES);
        }

        RecommendationSignals signals = new RecommendationSignals(values);
        BigDecimal score = scoringProfile.aggregate(signals);
        if (household.strongRejection()) {
            score = scoringProfile.applyStrongRejectionCap(score);
        }
        return new RecipeRecommendation(
                recipe, score, scoringProfile.bandFor(score), signals,
                pantry.missingGroupCount(), recipe.getTotalTime(),
                pantry.suggestedOptions(), List.copyOf(reasons));
    }

    private static EnumMap<RecommendationSignal, BigDecimal> pantrySignals(
            PantrySignalCalculator.Result pantry) {
        EnumMap<RecommendationSignal, BigDecimal> values =
                new EnumMap<>(RecommendationSignal.class);
        values.put(RecommendationSignal.PANTRY_COVERAGE, pantry.coverage());
        values.put(RecommendationSignal.MISSING_INGREDIENT_PENALTY,
                ratio(pantry.missingGroupCount(), pantry.groupCount()));
        pantry.alternativeFit().ifPresent(value ->
                values.put(RecommendationSignal.INGREDIENT_ALTERNATIVE_FIT, value));
        return values;
    }

    private static LinkedHashSet<RecommendationReasonCode> pantryReasons(
            PantrySignalCalculator.Result pantry) {
        LinkedHashSet<RecommendationReasonCode> reasons = new LinkedHashSet<>();
        if (pantry.coverage().compareTo(BigDecimal.ONE) == 0) {
            reasons.add(RecommendationReasonCode.PANTRY_FULL_COVERAGE);
        } else if (pantry.coverage().compareTo(STRONG_MATCH_THRESHOLD) >= 0) {
            reasons.add(RecommendationReasonCode.PANTRY_MOSTLY_COVERED);
        } else {
            reasons.add(RecommendationReasonCode.PANTRY_LOW_COVERAGE);
        }
        if (pantry.missingGroupCount() == 1) {
            reasons.add(RecommendationReasonCode.MISSING_ONE_INGREDIENT_GROUP);
        } else if (pantry.missingGroupCount() > 1) {
            reasons.add(RecommendationReasonCode.MISSING_MULTIPLE_INGREDIENT_GROUPS);
        }
        if (pantry.rescuedByAlternative()) {
            reasons.add(RecommendationReasonCode.ALTERNATIVE_AVAILABLE);
        }
        return reasons;
    }

    private static void addTimeSignal(
            Recipe recipe,
            RecommendationContext context,
            Map<RecommendationSignal, BigDecimal> values,
            Set<RecommendationReasonCode> reasons) {
        if (context.request().availableTime().isEmpty()) {
            return;
        }
        if (recipe.getTotalTime().isEmpty()) {
            reasons.add(RecommendationReasonCode.TIME_UNKNOWN);
            return;
        }
        Duration limit = context.request().availableTime().orElseThrow();
        Duration total = recipe.getTotalTime().orElseThrow();
        if (total.compareTo(limit) <= 0) {
            values.put(RecommendationSignal.PREPARATION_TIME_FIT, BigDecimal.ONE);
            reasons.add(RecommendationReasonCode.TIME_WITHIN_LIMIT);
            return;
        }
        BigDecimal fit = BigDecimal.valueOf(limit.toSeconds())
                .divide(BigDecimal.valueOf(total.toSeconds()), CALCULATION_CONTEXT);
        values.put(RecommendationSignal.PREPARATION_TIME_FIT, fit);
        reasons.add(RecommendationReasonCode.TIME_OVER_LIMIT);
    }

    private static RecommendationReasonCode tasteReason(BigDecimal affinity) {
        if (affinity.compareTo(STRONG_MATCH_THRESHOLD) >= 0) {
            return RecommendationReasonCode.TASTE_STRONG_MATCH;
        }
        if (affinity.compareTo(NEUTRAL_AFFINITY) >= 0) {
            return RecommendationReasonCode.TASTE_MATCH;
        }
        return RecommendationReasonCode.TASTE_MISMATCH;
    }

    private static BigDecimal ratio(int numerator, int denominator) {
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), CALCULATION_CONTEXT);
    }
}
