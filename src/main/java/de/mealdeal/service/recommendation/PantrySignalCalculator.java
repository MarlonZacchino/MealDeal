package de.mealdeal.service.recommendation;

import de.mealdeal.domain.InventoryItem;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredientGroup;
import de.mealdeal.domain.RecipeIngredientOption;
import de.mealdeal.domain.Unit;
import de.mealdeal.domain.UnitConverter;
import de.mealdeal.service.RecipeScaler;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Calculates one exact, shared-budget pantry assignment for all recipe groups. */
final class PantrySignalCalculator {

    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;
    private static final Comparator<GroupChoices> GROUP_ORDER =
            Comparator.comparing(GroupChoices::groupId);
    private static final Comparator<OptionRequirement> OPTION_TIE_ORDER =
            Comparator.comparing(OptionRequirement::standard).reversed()
                    .thenComparingInt(OptionRequirement::position)
                    .thenComparing(OptionRequirement::optionId);
    private static final Comparator<OptionRequirement> REQUIREMENT_ORDER =
            Comparator.comparing(OptionRequirement::quantity)
                    .thenComparing(OptionRequirement::groupId)
                    .thenComparing(OPTION_TIE_ORDER);

    private final RecipeScaler recipeScaler;
    private final BigDecimal coverageWeight;
    private final BigDecimal missingWeight;

    PantrySignalCalculator(
            RecipeScaler recipeScaler,
            BigDecimal coverageWeight,
            BigDecimal missingWeight) {
        this.recipeScaler = Objects.requireNonNull(recipeScaler,
                "Recipe scaler must not be null.");
        this.coverageWeight = requireNonNegative(coverageWeight, "Coverage weight");
        this.missingWeight = requireNonNegative(missingWeight, "Missing weight");
    }

    Result calculate(Recipe recipe, RecommendationContext context,
                     Set<UUID> excludedIngredients) {
        List<RecipeIngredientGroup> scaledGroups = recipeScaler.scaleIngredientGroups(
                recipe, context.request().servingCount());
        Map<ResourceKey, BigDecimal> inventory = inventoryByResource(
                context.inventorySnapshot());
        List<GroupChoices> choices = scaledGroups.stream()
                .map(group -> choicesFor(group, excludedIngredients))
                .sorted(GROUP_ORDER)
                .toList();

        Allocation optimum = new SharedBudgetOptimizer(
                choices, inventory, coverageWeight, missingWeight).solve();
        Allocation standardsOnly = standardsOnly(choices)
                .map(standardChoices -> new SharedBudgetOptimizer(
                        standardChoices, inventory, coverageWeight, missingWeight).solve())
                .orElse(null);

        int groupCount = choices.size();
        BigDecimal coverage = optimum.coverageSum()
                .divide(BigDecimal.valueOf(groupCount), CALCULATION_CONTEXT);
        int missingGroups = groupCount - optimum.fullyCoveredGroupCount();
        boolean usesAlternative = optimum.selectedOptions().entrySet().stream()
                .anyMatch(entry -> !standardOptionId(choices, entry.getKey())
                        .equals(entry.getValue()));
        boolean alternativeImprovesCoverage = usesAlternative
                && standardsOnly != null
                && optimum.coverageSum().compareTo(standardsOnly.coverageSum()) > 0;

        return new Result(coverage, missingGroups, groupCount,
                optimum.selectedOptions(), usesAlternative, alternativeImprovesCoverage);
    }

    private static GroupChoices choicesFor(
            RecipeIngredientGroup group, Set<UUID> excludedIngredients) {
        List<OptionRequirement> options = group.getOptions().stream()
                .filter(option -> !excludedIngredients.contains(option.getIngredient().getId()))
                .map(option -> requirement(group, option))
                .sorted(OPTION_TIE_ORDER)
                .toList();
        if (options.isEmpty()) {
            throw new IllegalStateException(
                    "Eligibility must leave at least one ingredient option per group.");
        }
        return new GroupChoices(group.getId(), group.getStandardOptionId(), options);
    }

    private static OptionRequirement requirement(
            RecipeIngredientGroup group, RecipeIngredientOption option) {
        Unit resourceUnit = canonicalUnit(option.getUnit());
        BigDecimal quantity = UnitConverter.convert(
                option.getQuantity(), option.getUnit(), resourceUnit);
        return new OptionRequirement(
                group.getId(), option.getId(),
                new ResourceKey(option.getIngredient().getId(), resourceUnit),
                quantity, option.getPosition(),
                option.getId().equals(group.getStandardOptionId()));
    }

    private static java.util.Optional<List<GroupChoices>> standardsOnly(
            List<GroupChoices> groups) {
        List<GroupChoices> standards = new ArrayList<>();
        for (GroupChoices group : groups) {
            java.util.Optional<OptionRequirement> standard = group.options().stream()
                    .filter(OptionRequirement::standard)
                    .findFirst();
            if (standard.isEmpty()) {
                return java.util.Optional.empty();
            }
            standards.add(new GroupChoices(
                    group.groupId(), group.standardOptionId(), List.of(standard.orElseThrow())));
        }
        return java.util.Optional.of(List.copyOf(standards));
    }

    private static UUID standardOptionId(List<GroupChoices> groups, UUID groupId) {
        return groups.stream()
                .filter(group -> group.groupId().equals(groupId))
                .findFirst()
                .orElseThrow()
                .standardOptionId();
    }

    private static Map<ResourceKey, BigDecimal> inventoryByResource(
            List<InventoryItem> inventory) {
        Map<ResourceKey, BigDecimal> quantities = new HashMap<>();
        for (InventoryItem item : inventory) {
            Unit resourceUnit = canonicalUnit(item.getUnit());
            ResourceKey key = new ResourceKey(item.getIngredient().getId(), resourceUnit);
            BigDecimal amount = UnitConverter.convert(
                    item.getQuantity(), item.getUnit(), resourceUnit);
            quantities.merge(key, amount, BigDecimal::add);
        }
        return Map.copyOf(quantities);
    }

    private static Unit canonicalUnit(Unit unit) {
        return switch (unit) {
            case KILOGRAM -> Unit.GRAM;
            case LITER -> Unit.MILLILITER;
            default -> unit;
        };
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String label) {
        Objects.requireNonNull(value, label + " must not be null.");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(label + " must not be negative.");
        }
        return value;
    }

    /**
     * Enumerates option assignments and prunes only with an optimistic upper bound.
     *
     * <p>The bound lets every remaining group reuse the complete inventory independently,
     * so it can never underestimate a branch. Options are visited in the contract's tie
     * order; an equal-bound later branch therefore cannot improve either score or tie.</p>
     */
    private static final class SharedBudgetOptimizer {

        private final List<GroupChoices> groups;
        private final Map<ResourceKey, BigDecimal> inventory;
        private final BigDecimal coverageWeight;
        private final BigDecimal missingWeight;
        private final List<BigDecimal> independentUpperBounds;
        private Allocation best;

        private SharedBudgetOptimizer(
                List<GroupChoices> groups,
                Map<ResourceKey, BigDecimal> inventory,
                BigDecimal coverageWeight,
                BigDecimal missingWeight) {
            this.groups = groups;
            this.inventory = inventory;
            this.coverageWeight = coverageWeight;
            this.missingWeight = missingWeight;
            independentUpperBounds = groups.stream()
                    .map(this::independentUpperBound)
                    .toList();
        }

        private Allocation solve() {
            search(0, new ArrayList<>());
            return Objects.requireNonNull(best, "Pantry assignment must have a solution.");
        }

        private void search(int groupIndex, List<OptionRequirement> selected) {
            if (best != null) {
                BigDecimal upperBound = evaluate(selected).objective();
                for (int index = groupIndex; index < groups.size(); index++) {
                    upperBound = upperBound.add(independentUpperBounds.get(index));
                }
                if (upperBound.compareTo(best.objective()) <= 0) {
                    return;
                }
            }
            if (groupIndex == groups.size()) {
                Allocation candidate = evaluate(selected);
                if (best == null || candidate.objective().compareTo(best.objective()) > 0) {
                    best = candidate;
                }
                return;
            }

            for (OptionRequirement option : groups.get(groupIndex).options()) {
                selected.add(option);
                search(groupIndex + 1, selected);
                selected.removeLast();
            }
        }

        private BigDecimal independentUpperBound(GroupChoices group) {
            BigDecimal bestContribution = BigDecimal.ZERO;
            for (OptionRequirement option : group.options()) {
                BigDecimal available = inventory.getOrDefault(
                        option.resource(), BigDecimal.ZERO);
                boolean complete = available.compareTo(option.quantity()) >= 0;
                BigDecimal coverage = complete
                        ? BigDecimal.ONE
                        : available.divide(option.quantity(), CALCULATION_CONTEXT);
                BigDecimal contribution = coverageWeight.multiply(coverage);
                if (complete) {
                    contribution = contribution.add(missingWeight);
                }
                bestContribution = bestContribution.max(contribution);
            }
            return bestContribution;
        }

        private Allocation evaluate(List<OptionRequirement> selected) {
            Map<ResourceKey, List<OptionRequirement>> byResource = new HashMap<>();
            for (OptionRequirement option : selected) {
                byResource.computeIfAbsent(option.resource(), ignored -> new ArrayList<>())
                        .add(option);
            }

            BigDecimal coverageSum = BigDecimal.ZERO;
            int fullyCovered = 0;
            for (Map.Entry<ResourceKey, List<OptionRequirement>> entry : byResource.entrySet()) {
                BigDecimal remaining = inventory.getOrDefault(entry.getKey(), BigDecimal.ZERO);
                // A smaller requirement has at least the coverage gain per inventory unit
                // and reaches the identical full-group bonus no later than a larger one.
                List<OptionRequirement> requirements = entry.getValue().stream()
                        .sorted(REQUIREMENT_ORDER)
                        .toList();
                for (OptionRequirement requirement : requirements) {
                    boolean complete = remaining.compareTo(requirement.quantity()) >= 0;
                    BigDecimal used = remaining.min(requirement.quantity());
                    BigDecimal coverage = complete
                            ? BigDecimal.ONE
                            : used.divide(requirement.quantity(), CALCULATION_CONTEXT);
                    coverageSum = coverageSum.add(coverage);
                    if (complete) {
                        fullyCovered++;
                    }
                    remaining = remaining.subtract(used);
                }
            }

            BigDecimal objective = coverageWeight.multiply(coverageSum)
                    .add(missingWeight.multiply(BigDecimal.valueOf(fullyCovered)));
            Map<UUID, UUID> selectedOptions = new LinkedHashMap<>();
            selected.stream()
                    .sorted(Comparator.comparing(OptionRequirement::groupId))
                    .forEach(option -> selectedOptions.put(
                            option.groupId(), option.optionId()));
            return new Allocation(objective, coverageSum, fullyCovered,
                    new LinkedHashMap<>(selectedOptions));
        }
    }

    private record ResourceKey(UUID ingredientId, Unit unit) {
    }

    private record GroupChoices(
            UUID groupId,
            UUID standardOptionId,
            List<OptionRequirement> options) {
    }

    private record OptionRequirement(
            UUID groupId,
            UUID optionId,
            ResourceKey resource,
            BigDecimal quantity,
            int position,
            boolean standard) {
    }

    private record Allocation(
            BigDecimal objective,
            BigDecimal coverageSum,
            int fullyCoveredGroupCount,
            Map<UUID, UUID> selectedOptions) {
    }

    record Result(
            BigDecimal coverage,
            int missingGroupCount,
            int groupCount,
            Map<UUID, UUID> suggestedOptions,
            boolean usesAlternative,
            boolean alternativeImprovesCoverage) {
    }
}
