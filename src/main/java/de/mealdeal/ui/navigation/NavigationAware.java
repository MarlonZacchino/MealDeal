package de.mealdeal.ui.navigation;

/** Implemented by controllers that initiate navigation actions. */
public interface NavigationAware {

    /** Supplies the navigator that owns the controller's current view. */
    void setNavigator(ViewNavigator navigator);

    /** Refreshes external presentation data after restoring this exact view instance. */
    default void onReturnFromDetail() { }
}
