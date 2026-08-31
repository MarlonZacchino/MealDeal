package de.mealdeal.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NutritionInfoTest {

    @Test
    void allowsAllValuesToBeAbsent() {
        NutritionInfo nutrition = new NutritionInfo(null, null, null, null);

        assertTrue(nutrition.getCaloriesKcal().isEmpty());
        assertTrue(nutrition.getProteinGrams().isEmpty());
        assertTrue(nutrition.getCarbohydrateGrams().isEmpty());
        assertTrue(nutrition.getFatGrams().isEmpty());
        assertFalse(nutrition.hasAnyValue());
    }

    @Test
    void keepsPartialAndZeroNutritionValues() {
        NutritionInfo nutrition = new NutritionInfo(0, new BigDecimal("0.5"), null,
                BigDecimal.ZERO);

        assertEquals(0, nutrition.getCaloriesKcal().orElseThrow());
        assertEquals(new BigDecimal("0.5"), nutrition.getProteinGrams().orElseThrow());
        assertTrue(nutrition.getCarbohydrateGrams().isEmpty());
        assertEquals(BigDecimal.ZERO, nutrition.getFatGrams().orElseThrow());
    }

    @Test
    void rejectsNegativeNutritionValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new NutritionInfo(-1, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new NutritionInfo(null, new BigDecimal("-0.1"), null, null));
    }
}
