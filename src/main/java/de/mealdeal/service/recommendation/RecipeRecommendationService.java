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
    private final DesiredIngredientSignalCalculator desiredIngredientCalculator;

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
        pantryCalculator = new PantrySignalCalculator(
                checkedScaler,
                scoringProfile.weightOf(RecommendationSignal.PANTRY_COVERAGE),
                scoringProfile.weightOf(RecommendationSignal.MISSING_INGREDIENT_PENALTY));
        preferenceCalculator = new PreferenceSignalCalculator(scoringProfile);
        desiredIngredientCalculator = new DesiredIngredientSignalCalculator();
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
                    if (hasWeight(RecommendationSignal.TASTE_AFFINITY)) {
                        reasons.add(tasteReason(value));
                    }
                });
        desiredIngredientCalculator.score(context.desiredIngredients(), recipe)
                .ifPresent(value -> {
                    values.put(RecommendationSignal.DESIRED_INGREDIENT_FIT, value);
                    if (hasWeight(RecommendationSignal.DESIRED_INGREDIENT_FIT)) {
                        if (value.compareTo(BigDecimal.ONE) == 0) {
                            reasons.add(RecommendationReasonCode.DESIRED_INGREDIENT_MATCH);
                        } else if (value.signum() > 0) {
                            reasons.add(RecommendationReasonCode.DESIRED_INGREDIENT_PARTIAL_MATCH);
                        }
                    }
                });
        addTimeSignal(recipe, context, values, reasons);
        context.personalizationFor(recipe.getId()).ifPresent(personalization ->
                addPersonalizationSignals(personalization, values, reasons));

        PreferenceSignalCalculator.HouseholdResult household =
                preferenceCalculator.householdScore(recipe, context.householdPreferences());
        household.score().ifPresent(value -> {
            values.put(RecommendationSignal.HOUSEHOLD_PREFERENCE, value);
            if (!household.strongRejection()
                    && hasWeight(RecommendationSignal.HOUSEHOLD_PREFERENCE)
                    && value.compareTo(STRONG_MATCH_THRESHOLD) >= 0) {
                reasons.add(RecommendationReasonCode.HOUSEHOLD_STRONG_MATCH);
            }
        });
        if (household.strongRejection()) {
            reasons.add(RecommendationReasonCode.HOUSEHOLD_CONFLICT);
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
        return values;
    }

    private LinkedHashSet<RecommendationReasonCode> pantryReasons(
            PantrySignalCalculator.Result pantry) {
        LinkedHashSet<RecommendationReasonCode> reasons = new LinkedHashSet<>();
        if (hasWeight(RecommendationSignal.PANTRY_COVERAGE)) {
            if (pantry.missingGroupCount() == 0) {
                reasons.add(RecommendationReasonCode.PANTRY_FULL_COVERAGE);
            } else if (pantry.coverage().compareTo(STRONG_MATCH_THRESHOLD) >= 0) {
                reasons.add(RecommendationReasonCode.PANTRY_MOSTLY_COVERED);
            } else {
                reasons.add(RecommendationReasonCode.PANTRY_LOW_COVERAGE);
            }
        }
        if (hasWeight(RecommendationSignal.MISSING_INGREDIENT_PENALTY)) {
            if (pantry.missingGroupCount() == 1) {
                reasons.add(RecommendationReasonCode.MISSING_ONE_INGREDIENT_GROUP);
            } else if (pantry.missingGroupCount() > 1) {
                reasons.add(RecommendationReasonCode.MISSING_MULTIPLE_INGREDIENT_GROUPS);
            }
        }
        if (pantry.usesAlternative()) {
            reasons.add(RecommendationReasonCode.ALTERNATIVE_AVAILABLE);
        }
        if (pantry.alternativeImprovesCoverage()) {
            reasons.add(RecommendationReasonCode.ALTERNATIVE_IMPROVES_COVERAGE);
        }
        return reasons;
    }

    private void addTimeSignal(
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
            if (hasWeight(RecommendationSignal.PREPARATION_TIME_FIT)) {
                reasons.add(RecommendationReasonCode.TIME_WITHIN_LIMIT);
            }
            return;
        }
        BigDecimal fit = BigDecimal.valueOf(limit.toSeconds())
                .divide(BigDecimal.valueOf(total.toSeconds()), CALCULATION_CONTEXT);
        values.put(RecommendationSignal.PREPARATION_TIME_FIT, fit);
        if (hasWeight(RecommendationSignal.PREPARATION_TIME_FIT)) {
            reasons.add(RecommendationReasonCode.TIME_OVER_LIMIT);
        }
    }

    private void addPersonalizationSignals(
            RecipePersonalizationSignals personalization,
            Map<RecommendationSignal, BigDecimal> values,
            Set<RecommendationReasonCode> reasons) {
        values.put(RecommendationSignal.VARIETY_SCORE, personalization.freshness());
        values.put(RecommendationSignal.RECIPE_PREFERENCE,
                personalization.normalizedEffectivePreference());
        addRecencyReason(personalization, reasons);
        addPreferenceReason(personalization, reasons);
    }

    private void addRecencyReason(
            RecipePersonalizationSignals personalization,
            Set<RecommendationReasonCode> reasons) {
        if (!hasWeight(RecommendationSignal.VARIETY_SCORE)) {
            return;
        }
        switch (personalization.recency()) {
            case NEVER_COOKED -> reasons.add(RecommendationReasonCode.RECIPE_NEVER_COOKED);
            case COOKED_TODAY -> reasons.add(RecommendationReasonCode.RECIPE_COOKED_TODAY);
            case COOKED_RECENTLY -> reasons.add(RecommendationReasonCode.RECENTLY_COOKED);
            case NOT_COOKED_RECENTLY -> reasons.add(
                    RecommendationReasonCode.RECIPE_NOT_COOKED_RECENTLY);
            case MID_WINDOW -> {
                // A middle-window value contributes continuously but is not material to display.
            }
        }
    }

    private void addPreferenceReason(
            RecipePersonalizationSignals personalization,
            Set<RecommendationReasonCode> reasons) {
        if (!hasWeight(RecommendationSignal.RECIPE_PREFERENCE)) {
            return;
        }
        if (personalization.hasExplicitFeedback()) {
            BigDecimal preference = personalization.explicitPreference().orElseThrow();
            if (preference.compareTo(BigDecimal.ONE) == 0) {
                reasons.add(RecommendationReasonCode.RECIPE_STRONGLY_LIKED);
            } else if (preference.signum() > 0) {
                reasons.add(RecommendationReasonCode.RECIPE_EXPLICITLY_LIKED);
            } else if (preference.compareTo(BigDecimal.ONE.negate()) == 0) {
                reasons.add(RecommendationReasonCode.RECIPE_STRONGLY_DISLIKED);
            } else if (preference.signum() < 0) {
                reasons.add(RecommendationReasonCode.RECIPE_EXPLICITLY_DISLIKED);
            }
            return;
        }
        if (personalization.selectedCount() >= 2
                && personalization.selectedCount() > personalization.dismissedCount()) {
            reasons.add(RecommendationReasonCode.RECIPE_REPEATEDLY_SELECTED);
        } else if (personalization.dismissedCount() >= 2
                && personalization.dismissedCount() > personalization.selectedCount()) {
            reasons.add(RecommendationReasonCode.RECIPE_REPEATEDLY_DISMISSED);
        }
    }

    private boolean hasWeight(RecommendationSignal signal) {
        return scoringProfile.weightOf(signal).signum() > 0;
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
