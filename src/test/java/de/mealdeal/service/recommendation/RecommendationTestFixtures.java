package de.mealdeal.service.recommendation;

import de.mealdeal.domain.DishType;
import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.InventoryItem;
import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.RecipeIngredientGroup;
import de.mealdeal.domain.RecipeIngredientOption;
import de.mealdeal.domain.Taste;
import de.mealdeal.domain.Unit;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

final class RecommendationTestFixtures {

    private RecommendationTestFixtures() {
    }

    static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    static Ingredient ingredient(String name) {
        return new Ingredient(id("ingredient:" + name), name);
    }

    static Taste taste(String name) {
        return new Taste(id("taste:" + name), name);
    }

    static RecipeIngredientOption option(
            String key, Ingredient ingredient, String quantity, Unit unit, int position) {
        return new RecipeIngredientOption(
                id("option:" + key), ingredient, new BigDecimal(quantity), unit, position);
    }

    static RecipeIngredientGroup group(
            String key, List<RecipeIngredientOption> options, int standardIndex) {
        return new RecipeIngredientGroup(
                id("group:" + key), options, options.get(standardIndex).getId());
    }

    static Recipe recipe(String key, String name, List<RecipeIngredientGroup> groups, Taste taste) {
        return recipe(key, name, groups, List.of(taste), null, null, null, null, DishType.MAIN);
    }

    static Recipe recipe(
            String key, String name, List<RecipeIngredientGroup> groups, List<Taste> tastes,
            Integer preparationMinutes, Integer cookingMinutes,
            Integer bakingMinutes, Integer restingMinutes, DishType dishType) {
        return Recipe.withIngredientGroups(
                id("recipe:" + key), name, 2, groups, List.of(), tastes,
                preparationMinutes, cookingMinutes, bakingMinutes, restingMinutes,
                null, dishType);
    }

    static InventoryItem stock(
            String key, Ingredient ingredient, String quantity, Unit unit) {
        return new InventoryItem(
                id("inventory:" + key), ingredient, new BigDecimal(quantity), unit);
    }
}
