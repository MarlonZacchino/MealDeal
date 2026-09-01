package de.mealdeal.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryItemTest {

    private final Ingredient ingredient = new Ingredient(
            "Mehl", IngredientCategories.BAKING);

    @Test
    void acceptsZeroAndPositiveStock() {
        assertEquals(BigDecimal.ZERO,
                new InventoryItem(ingredient, BigDecimal.ZERO, Unit.GRAM).getQuantity());
        assertEquals(new BigDecimal("1250.50"), new InventoryItem(
                ingredient, new BigDecimal("1250.50"), Unit.GRAM).getQuantity());
    }

    @Test
    void rejectsNegativeStockAndMissingValues() {
        assertThrows(IllegalArgumentException.class, () -> new InventoryItem(
                ingredient, new BigDecimal("-0.01"), Unit.GRAM));
        assertThrows(NullPointerException.class,
                () -> new InventoryItem(null, BigDecimal.ZERO, Unit.GRAM));
        assertThrows(NullPointerException.class,
                () -> new InventoryItem(ingredient, null, Unit.GRAM));
        assertThrows(NullPointerException.class,
                () -> new InventoryItem(ingredient, BigDecimal.ZERO, null));
    }

    @Test
    void convertsOnlyUnitsSupportedByUnitConverter() {
        InventoryItem mass = new InventoryItem(
                ingredient, new BigDecimal("1500"), Unit.GRAM);
        InventoryItem volume = new InventoryItem(
                ingredient, new BigDecimal("1.5"), Unit.LITER);
        InventoryItem slices = new InventoryItem(
                ingredient, new BigDecimal("4"), Unit.SLICE);

        assertEquals(new BigDecimal("1.5"), mass.getQuantityIn(Unit.KILOGRAM));
        assertEquals(new BigDecimal("1500.0"), volume.getQuantityIn(Unit.MILLILITER));
        assertEquals(new BigDecimal("4"), slices.getQuantityIn(Unit.SLICE));
        assertThrows(IllegalArgumentException.class,
                () -> slices.getQuantityIn(Unit.PIECE));
        assertThrows(IllegalArgumentException.class,
                () -> slices.getQuantityIn(Unit.CLOVE));
    }

    @Test
    void equalityUsesStableUuid() {
        UUID id = UUID.randomUUID();

        assertEquals(new InventoryItem(id, ingredient, BigDecimal.ZERO, Unit.GRAM),
                new InventoryItem(id, ingredient, BigDecimal.TEN, Unit.KILOGRAM));
    }
}
