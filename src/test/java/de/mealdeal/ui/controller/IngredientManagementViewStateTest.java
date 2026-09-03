package de.mealdeal.ui.controller;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.IngredientCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngredientManagementViewStateTest {

    @Test
    void startsWithoutASelectionAndKeepsCategoriesAndIngredientsAlphabetical() {
        IngredientCategory vegetables = new IngredientCategory("Gemüse", 0);
        IngredientCategory baking = new IngredientCategory("Backzutaten", 1);
        Ingredient onion = new Ingredient("Zwiebeln", vegetables);
        Ingredient carrot = new Ingredient("Karotten", vegetables);
        Ingredient flour = new Ingredient("Mehl", baking);
        IngredientManagementViewState state = new IngredientManagementViewState();

        state.updateIngredients(List.of(onion, flour, carrot));

        assertTrue(state.selectedGroup().isEmpty());
        assertEquals(List.of("Backzutaten", "Gemüse"), state.groups().stream()
                .map(group -> group.category().getName()).toList());
        assertEquals(List.of("Karotten", "Zwiebeln"), state.groups().get(1).ingredients().stream()
                .map(Ingredient::getName).toList());
    }

    @Test
    void selectingAndSwitchingCategoriesExposesOnlyTheirIngredients() {
        IngredientCategory fruit = new IngredientCategory("Obst", 0);
        IngredientCategory meat = new IngredientCategory("Fleisch", 1);
        Ingredient apple = new Ingredient("Apfel", fruit);
        Ingredient chicken = new Ingredient("Hähnchenbrust", meat);
        IngredientManagementViewState state = new IngredientManagementViewState();
        state.updateIngredients(List.of(apple, chicken));

        state.selectCategory(meat.getId());

        assertTrue(state.isSelected(meat.getId()));
        assertEquals(List.of("Hähnchenbrust"), state.selectedGroup().orElseThrow()
                .ingredients().stream().map(Ingredient::getName).toList());

        state.selectCategory(fruit.getId());

        assertFalse(state.isSelected(meat.getId()));
        assertTrue(state.isSelected(fruit.getId()));
        assertEquals(List.of("Apfel"), state.selectedGroup().orElseThrow()
                .ingredients().stream().map(Ingredient::getName).toList());
    }

    @Test
    void refreshKeepsAStillAvailableSelectionAndClearsAnEmptyCategory() {
        IngredientCategory fruit = new IngredientCategory("Obst", 0);
        IngredientCategory vegetables = new IngredientCategory("Gemüse", 1);
        Ingredient apple = new Ingredient("Apfel", fruit);
        Ingredient pear = new Ingredient("Birne", fruit);
        Ingredient carrot = new Ingredient("Karotte", vegetables);
        IngredientManagementViewState state = new IngredientManagementViewState();
        state.updateIngredients(List.of(apple, pear, carrot));
        state.selectCategory(fruit.getId());

        Ingredient movedApple = new Ingredient(apple.getId(), apple.getName(), vegetables);
        state.updateIngredients(List.of(movedApple, pear, carrot));

        assertTrue(state.isSelected(fruit.getId()));
        assertEquals(List.of("Birne"), state.selectedGroup().orElseThrow()
                .ingredients().stream().map(Ingredient::getName).toList());

        Ingredient movedPear = new Ingredient(pear.getId(), pear.getName(), vegetables);
        state.updateIngredients(List.of(movedApple, movedPear, carrot));

        assertTrue(state.selectedGroup().isEmpty());
        assertFalse(state.groups().stream()
                .anyMatch(group -> group.category().getId().equals(fruit.getId())));
    }
}
