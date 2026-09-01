package de.mealdeal.ui.form;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.RecipeIngredientGroup;
import de.mealdeal.domain.RecipeIngredientOption;
import de.mealdeal.domain.Unit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IngredientGroupFormStateTest {

    @Test
    void newGroupStartsWithOneStandardOptionAndAddingKeepsIt() {
        IngredientGroupFormState state = new IngredientGroupFormState();
        UUID first = state.getOptionIds().getFirst();

        UUID added = state.addOption();

        assertEquals(List.of(first, added), state.getOptionIds());
        assertEquals(first, state.getStandardOptionId());
        assertNotEquals(first, added);
    }

    @Test
    void selectingAndRemovingStandardPromotesFirstRemainingOption() {
        IngredientGroupFormState state = new IngredientGroupFormState();
        UUID first = state.getOptionIds().getFirst();
        UUID second = state.addOption();
        UUID third = state.addOption();
        state.selectStandard(second);

        state.removeOption(second);

        assertEquals(List.of(first, third), state.getOptionIds());
        assertEquals(first, state.getStandardOptionId());
    }

    @Test
    void lastOptionCannotBeRemoved() {
        IngredientGroupFormState state = new IngredientGroupFormState();

        assertThrows(IllegalStateException.class,
                () -> state.removeOption(state.getOptionIds().getFirst()));
    }

    @Test
    void reconstructsExistingGroupAndOptionIdentities() {
        RecipeIngredientOption first = option("Kalb", "400", Unit.GRAM, 0);
        RecipeIngredientOption second = option("Hähnchen", "350", Unit.GRAM, 1);
        RecipeIngredientGroup group = new RecipeIngredientGroup(
                UUID.randomUUID(), List.of(first, second), second.getId());

        IngredientGroupFormState state = new IngredientGroupFormState(group);

        assertEquals(group.getId(), state.getGroupId());
        assertEquals(List.of(first.getId(), second.getId()), state.getOptionIds());
        assertEquals(second.getId(), state.getStandardOptionId());
    }

    private static RecipeIngredientOption option(String name, String quantity,
                                                 Unit unit, int position) {
        return new RecipeIngredientOption(new Ingredient(name), new BigDecimal(quantity),
                unit, position);
    }
}
