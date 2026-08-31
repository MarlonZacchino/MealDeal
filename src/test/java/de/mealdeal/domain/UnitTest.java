package de.mealdeal.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class UnitTest {

    @Test
    void massUnitsShareDimension() {
        assertEquals(Unit.GRAM.getDimension(), Unit.KILOGRAM.getDimension());
    }

    @Test
    void volumeUnitsShareDimension() {
        assertEquals(Unit.MILLILITER.getDimension(), Unit.LITER.getDimension());
    }

    @Test
    void incompatibleUnitsHaveDifferentDimensions() {
        assertNotEquals(Unit.PIECE.getDimension(), Unit.GRAM.getDimension());
    }

    @Test
    void sliceSharesCountDimensionWithoutBeingEquivalentToPiece() {
        assertEquals(UnitDimension.COUNT, Unit.SLICE.getDimension());
        assertNotEquals(Unit.SLICE, Unit.PIECE);
    }
}
