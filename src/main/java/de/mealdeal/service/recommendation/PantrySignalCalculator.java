package de.mealdeal.service.recommendation;

import de.mealdeal.domain.InventoryItem;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredientGroup;
import de.mealdeal.domain.RecipeIngredientOption;
import de.mealdeal.domain.UnitConverter;
import de.mealdeal.service.RecipeScaler;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Calculates group-based pantry coverage and a stable best option per group. */
final class PantrySignalCalculator {

    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;
    private final RecipeScaler recipeScaler;

    PantrySignalCalculator(RecipeScaler recipeScaler) {
        this.recipeScaler = Objects.requireNonNull(recipeScaler, "Recipe scaler must not be null.");
    }

    Result calculate(Recipe recipe, RecommendationContext context, Set<UUID> excludedIngredients) {
        List<RecipeIngredientGroup> groups = recipeScaler.scaleIngredientGroups(
                recipe, context.request().servingCount());
        BigDecimal coverageSum = BigDecimal.ZERO;
        int missingGroups = 0;
        List<BigDecimal> alternativeImprovements = new ArrayList<>();
        Map<UUID, UUID> suggestedOptions = new LinkedHashMap<>();
        boolean rescuedByAlternative = false;

        for (RecipeIngredientGroup group : groups) {
            List<RecipeIngredientOption> allowedOptions = group.getOptions().stream()
                    .filter(option -> !excludedIngredients.contains(option.getIngredient().getId()))
                    .toList();
            OptionCoverage best = bestCoverage(group, allowedOptions, context.inventorySnapshot());
            BigDecimal defaultCoverage = defaultCoverage(
                    group, context.inventorySnapshot(), excludedIngredients);
            coverageSum = coverageSum.add(best.coverage());
            suggestedOptions.put(group.getId(), best.option().getId());
            if (best.coverage().compareTo(BigDecimal.ONE) < 0) {
                missingGroups++;
            }
            boolean hasAllowedAlternative = allowedOptions.stream()
                    .anyMatch(option -> !option.getId().equals(group.getStandardOptionId()));
            if (hasAllowedAlternative) {
                BigDecimal improvement = best.coverage().subtract(defaultCoverage)
                        .max(BigDecimal.ZERO);
                alternativeImprovements.add(improvement);
                if (!best.option().getId().equals(group.getStandardOptionId())
                        && improvement.signum() > 0) {
                    rescuedByAlternative = true;
                }
            }
        }

        BigDecimal coverage = coverageSum.divide(
                BigDecimal.valueOf(groups.size()), CALCULATION_CONTEXT);
        Optional<BigDecimal> alternativeFit = alternativeImprovements.isEmpty()
                ? Optional.empty() : Optional.of(average(alternativeImprovements));
        return new Result(coverage, missingGroups, groups.size(), alternativeFit,
                new LinkedHashMap<>(suggestedOptions), rescuedByAlternative);
    }

    private static BigDecimal defaultCoverage(
            RecipeIngredientGroup group,
            List<InventoryItem> inventory,
            Set<UUID> excludedIngredients) {
        return group.getOptions().stream()
                .filter(option -> option.getId().equals(group.getStandardOptionId()))
                .filter(option -> !excludedIngredients.contains(option.getIngredient().getId()))
                .findFirst()
                .map(option -> coverage(option, inventory))
                .orElse(BigDecimal.ZERO);
    }

    private static OptionCoverage bestCoverage(
            RecipeIngredientGroup group,
            List<RecipeIngredientOption> options,
            List<InventoryItem> inventory) {
        OptionCoverage best = null;
        for (RecipeIngredientOption option : options) {
            OptionCoverage candidate = new OptionCoverage(option, coverage(option, inventory));
            if (best == null || isBetter(group, candidate, best)) {
                best = candidate;
            }
        }
        return Objects.requireNonNull(best,
                "Eligibility must leave at least one ingredient option per group.");
    }

    private static boolean isBetter(
            RecipeIngredientGroup group, OptionCoverage candidate, OptionCoverage current) {
        int coverageComparison = candidate.coverage().compareTo(current.coverage());
        if (coverageComparison != 0) {
            return coverageComparison > 0;
        }
        boolean candidateDefault = candidate.option().getId().equals(group.getStandardOptionId());
        boolean currentDefault = current.option().getId().equals(group.getStandardOptionId());
        if (candidateDefault != currentDefault) {
            return candidateDefault;
        }
        int positionComparison = Integer.compare(
                candidate.option().getPosition(), current.option().getPosition());
        return positionComparison < 0
                || positionComparison == 0
                && candidate.option().getId().compareTo(current.option().getId()) < 0;
    }

    private static BigDecimal coverage(
            RecipeIngredientOption option, List<InventoryItem> inventory) {
        BigDecimal available = inventory.stream()
                .filter(item -> item.getIngredient().getId().equals(
                        option.getIngredient().getId()))
                .filter(item -> UnitConverter.canConvert(item.getUnit(), option.getUnit()))
                .map(item -> UnitConverter.convert(
                        item.getQuantity(), item.getUnit(), option.getUnit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return available.divide(option.getQuantity(), CALCULATION_CONTEXT).min(BigDecimal.ONE);
    }

    private static BigDecimal average(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), CALCULATION_CONTEXT);
    }

    private record OptionCoverage(RecipeIngredientOption option, BigDecimal coverage) {
    }

    record Result(
            BigDecimal coverage,
            int missingGroupCount,
            int groupCount,
            Optional<BigDecimal> alternativeFit,
            Map<UUID, UUID> suggestedOptions,
            boolean rescuedByAlternative) {
    }
}
