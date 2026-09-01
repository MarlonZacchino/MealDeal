package de.mealdeal.domain;

/** Defines the role a recipe has in one planned meal. */
public enum MealRole {
    MAIN,
    SIDE,
    DESSERT;

    /** Returns the required meal role for the supplied recipe type. */
    public static MealRole forDishType(DishType dishType) {
        return MealRole.valueOf(dishType.name());
    }
}
