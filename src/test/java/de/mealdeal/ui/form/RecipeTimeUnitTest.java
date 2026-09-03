package de.mealdeal.ui.form;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecipeTimeUnitTest {

    @Test
    void convertsEveryInputUnitToCanonicalDuration() {
        assertEquals(Duration.ofSeconds(30), RecipeTimeUnit.SECONDS.toDuration(30));
        assertEquals(Duration.ofMinutes(20), RecipeTimeUnit.MINUTES.toDuration(20));
        assertEquals(Duration.ofHours(2), RecipeTimeUnit.HOURS.toDuration(2));
    }

    @Test
    void choosesExactFriendlyEditUnitWithoutPrecisionLoss() {
        assertEquals(new RecipeTimeUnit.EditValue(2, RecipeTimeUnit.HOURS),
                RecipeTimeUnit.forEditing(Duration.ofHours(2)));
        assertEquals(new RecipeTimeUnit.EditValue(90, RecipeTimeUnit.MINUTES),
                RecipeTimeUnit.forEditing(Duration.ofMinutes(90)));
        assertEquals(new RecipeTimeUnit.EditValue(75, RecipeTimeUnit.SECONDS),
                RecipeTimeUnit.forEditing(Duration.ofSeconds(75)));
    }
}
