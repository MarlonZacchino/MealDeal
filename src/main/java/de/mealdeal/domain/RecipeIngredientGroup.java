package de.mealdeal.domain;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * An ordered set of interchangeable ingredient options for one recipe need.
 *
 * <p>Exactly one option is designated as the standard choice. The group owns
 * no duplicate option data; callers retrieve that central choice through
 * {@link #getStandardOption()}.</p>
 */
public final class RecipeIngredientGroup {

    private final UUID id;
    private final List<RecipeIngredientOption> options;
    private final UUID standardOptionId;

    /** Creates a group with a new stable technical identity. */
    public RecipeIngredientGroup(List<RecipeIngredientOption> options, UUID standardOptionId) {
        this(UUID.randomUUID(), options, standardOptionId);
    }

    /** Creates a group using an option instance as its standard choice. */
    public RecipeIngredientGroup(List<RecipeIngredientOption> options,
                                 RecipeIngredientOption standardOption) {
        this(UUID.randomUUID(), options, requireStandardOptionId(standardOption));
    }

    /** Recreates a group with its existing technical identity. */
    public RecipeIngredientGroup(UUID id, List<RecipeIngredientOption> options,
                                 UUID standardOptionId) {
        this.id = Objects.requireNonNull(id, "Recipe ingredient group ID must not be null.");
        this.options = validateAndOrder(options);
        this.standardOptionId = Objects.requireNonNull(
                standardOptionId, "Standard option ID must not be null.");
        if (this.options.stream().noneMatch(option -> option.getId().equals(standardOptionId))) {
            throw new IllegalArgumentException("Standard option must belong to its ingredient group.");
        }
    }

    /** Recreates a group with an option instance as its standard choice. */
    public RecipeIngredientGroup(UUID id, List<RecipeIngredientOption> options,
                                 RecipeIngredientOption standardOption) {
        this(id, options, requireStandardOptionId(standardOption));
    }

    public UUID getId() { return id; }

    /** Returns immutable options sorted by their stable recipe-local position. */
    public List<RecipeIngredientOption> getOptions() { return options; }

    /** Returns the one required standard option belonging to this group. */
    public RecipeIngredientOption getStandardOption() {
        return options.stream().filter(option -> option.getId().equals(standardOptionId))
                .findFirst().orElseThrow();
    }

    public UUID getStandardOptionId() { return standardOptionId; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof RecipeIngredientGroup group && id.equals(group.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    private static List<RecipeIngredientOption> validateAndOrder(
            List<RecipeIngredientOption> options) {
        if (options == null) {
            throw new NullPointerException("Recipe ingredient group options must not be null.");
        }
        if (options.isEmpty()) {
            throw new IllegalArgumentException("Recipe ingredient group must contain at least one option.");
        }
        if (options.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Recipe ingredient group options must not contain null values.");
        }
        Set<UUID> optionIds = new HashSet<>();
        Set<Integer> positions = new HashSet<>();
        for (RecipeIngredientOption option : options) {
            if (!optionIds.add(option.getId())) {
                throw new IllegalArgumentException(
                        "Recipe ingredient group options must have unique identities.");
            }
            if (!positions.add(option.getPosition())) {
                throw new IllegalArgumentException(
                        "Recipe ingredient group option positions must be unique.");
            }
        }
        return options.stream().sorted(Comparator.comparingInt(RecipeIngredientOption::getPosition))
                .toList();
    }

    private static UUID requireStandardOptionId(RecipeIngredientOption standardOption) {
        return Objects.requireNonNull(standardOption, "Standard option must not be null.").getId();
    }
}
