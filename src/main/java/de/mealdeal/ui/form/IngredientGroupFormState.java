package de.mealdeal.ui.form;

import de.mealdeal.domain.RecipeIngredientGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Maintains stable form identities and the one standard option of a group. */
public final class IngredientGroupFormState {

    private final UUID groupId;
    private final List<UUID> optionIds;
    private UUID standardOptionId;

    /** Creates a new group whose first option is automatically the standard. */
    public IngredientGroupFormState() {
        groupId = UUID.randomUUID();
        UUID firstOptionId = UUID.randomUUID();
        optionIds = new ArrayList<>(List.of(firstOptionId));
        standardOptionId = firstOptionId;
    }

    /** Recreates the identity/default state of an existing domain group. */
    public IngredientGroupFormState(RecipeIngredientGroup group) {
        this(Objects.requireNonNull(group, "Ingredient group must not be null.").getId(),
                group.getOptions().stream().map(option -> option.getId()).toList(),
                group.getStandardOptionId());
    }

    IngredientGroupFormState(UUID groupId, List<UUID> optionIds, UUID standardOptionId) {
        this.groupId = Objects.requireNonNull(groupId, "Ingredient group ID must not be null.");
        this.optionIds = new ArrayList<>(Objects.requireNonNull(
                optionIds, "Option IDs must not be null."));
        if (this.optionIds.isEmpty()) {
            throw new IllegalArgumentException("Ingredient group must contain an option.");
        }
        if (this.optionIds.stream().anyMatch(Objects::isNull)
                || this.optionIds.stream().distinct().count() != this.optionIds.size()) {
            throw new IllegalArgumentException("Ingredient option IDs must be present and unique.");
        }
        this.standardOptionId = Objects.requireNonNull(
                standardOptionId, "Standard option ID must not be null.");
        if (!this.optionIds.contains(standardOptionId)) {
            throw new IllegalArgumentException("Standard option must belong to the group.");
        }
    }

    public UUID addOption() {
        UUID optionId = UUID.randomUUID();
        optionIds.add(optionId);
        return optionId;
    }

    /** Removes an option and deterministically promotes the first remaining one if needed. */
    public void removeOption(UUID optionId) {
        if (optionIds.size() == 1) {
            throw new IllegalStateException("The last ingredient option cannot be removed.");
        }
        if (!optionIds.remove(optionId)) {
            throw new IllegalArgumentException("Ingredient option does not belong to the group.");
        }
        if (optionId.equals(standardOptionId)) {
            standardOptionId = optionIds.getFirst();
        }
    }

    public void selectStandard(UUID optionId) {
        if (!optionIds.contains(optionId)) {
            throw new IllegalArgumentException("Standard option must belong to the group.");
        }
        standardOptionId = optionId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public List<UUID> getOptionIds() {
        return List.copyOf(optionIds);
    }

    public UUID getStandardOptionId() {
        return standardOptionId;
    }
}
