package de.mealdeal.domain;

/**
 * Units supported by the first version of MealDeal.
 *
 * <p>The dimension records compatibility without implementing conversion yet.
 * For example, grams and kilograms share the {@link UnitDimension#MASS}
 * dimension, while pieces do not.</p>
 */
public enum Unit {
    GRAM("g", UnitDimension.MASS),
    KILOGRAM("kg", UnitDimension.MASS),
    MILLILITER("ml", UnitDimension.VOLUME),
    LITER("l", UnitDimension.VOLUME),
    PIECE("piece", UnitDimension.COUNT),
    SLICE("slice", UnitDimension.COUNT),
    CLOVE("clove", UnitDimension.COUNT),
    SPRIG("sprig", UnitDimension.COUNT),
    TABLESPOON("tbsp", UnitDimension.KITCHEN_MEASURE),
    TEASPOON("tsp", UnitDimension.KITCHEN_MEASURE),
    PINCH("pinch", UnitDimension.KITCHEN_MEASURE);

    private final String symbol;
    private final UnitDimension dimension;

    Unit(String symbol, UnitDimension dimension) {
        this.symbol = symbol;
        this.dimension = dimension;
    }

    /**
     * Returns the short representation used when displaying a quantity.
     *
     * @return the unit symbol
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Returns the compatibility group of this unit.
     *
     * @return the unit dimension
     */
    public UnitDimension getDimension() {
        return dimension;
    }
}
