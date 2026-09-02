package de.mealdeal.ui;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.IngredientCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngredientCategoryGroupingTest {

    @Test
    void groupsOnlyUsedCategoriesAndSortsBothLevelsByGermanName() {
        IngredientCategory fruit = new IngredientCategory("Obst", 0);
        IngredientCategory dairy = new IngredientCategory("Milchprodukte", 9);
        IngredientCategory empty = new IngredientCategory("Backzutaten", 1);
        Ingredient apple = new Ingredient("Apfel", fruit);
        Ingredient pear = new Ingredient("Birne", fruit);
        Ingredient yoghurt = new Ingredient("Naturjoghurt", dairy);

        var groups = IngredientCategoryGrouping.group(
                List.of(pear, yoghurt, apple), "", List.of());

        assertEquals(List.of("Milchprodukte", "Obst"), groups.stream()
                .map(group -> group.category().getName()).toList());
        assertEquals(List.of("Apfel", "Birne"), groups.get(1).ingredients().stream()
                .map(Ingredient::getName).toList());
        assertFalse(groups.stream().anyMatch(group -> group.category().equals(empty)));
    }

    @Test
    void filtersIngredientsExcludesSelectionsAndDropsEmptyCategories() {
        IngredientCategory fruit = new IngredientCategory("Obst", 0);
        IngredientCategory vegetables = new IngredientCategory("Gemüse", 1);
        Ingredient apple = new Ingredient("Apfel", fruit);
        Ingredient pear = new Ingredient("Birne", fruit);
        Ingredient carrot = new Ingredient("Karotte", vegetables);

        var groups = IngredientCategoryGrouping.group(
                List.of(apple, pear, carrot), "BIR", List.of(apple.getId()));

        assertEquals(List.of("Obst"), groups.stream()
                .map(group -> group.category().getName()).toList());
        assertEquals(List.of("Birne"), groups.getFirst().ingredients().stream()
                .map(Ingredient::getName).toList());
    }

    @Test
    void expandsOnlyWhileATextFilterIsActive() {
        assertFalse(IngredientCategoryGrouping.shouldExpandForFilter("  "));
        assertTrue(IngredientCategoryGrouping.shouldExpandForFilter("milch"));
    }
}
