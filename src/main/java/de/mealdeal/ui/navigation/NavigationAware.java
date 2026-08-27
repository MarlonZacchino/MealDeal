package de.mealdeal.ui.navigation;

/** Implemented by controllers that initiate navigation actions. */
public interface NavigationAware {

    /** Supplies the navigator that owns the controller's current view. */
    void setNavigator(ViewNavigator navigator);
}
