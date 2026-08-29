package de.mealdeal.ui.theme;

/** Visual modes supported by the local MealDeal user interface. */
public enum ThemeMode {
    LIGHT,
    DARK;

    ThemeMode toggled() {
        return this == LIGHT ? DARK : LIGHT;
    }
}
