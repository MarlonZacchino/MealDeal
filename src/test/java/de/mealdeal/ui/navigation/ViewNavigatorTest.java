package de.mealdeal.ui.navigation;

import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.Taste;
import de.mealdeal.ui.ViewLoadingException;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Tests navigation without controls, a Stage, a toolkit, or any user-input automation. */
class ViewNavigatorTest {

    private final Recipe recipe = new Recipe("Suppe", 2, List.of(), List.of(), List.of(new Taste("Herzhaft")));
    private final StackPane host = new StackPane();
    private final List<ViewType> loads = new ArrayList<>();
    private final List<ViewType> highlights = new ArrayList<>();
    private final List<OriginController> controllers = new ArrayList<>();
    private ViewType failingRoute;
    private final ViewNavigator navigator = navigator();

    private ViewNavigator navigator() {
        var result = new ViewNavigator(host, (type, initializer) -> {
            if (type == failingRoute) {
                throw new ViewLoadingException("Test load failure");
            }
            loads.add(type);
            OriginController controller = new OriginController();
            controllers.add(controller);
            // Detail rendering is covered separately with the real ingredient model.
            return new ViewNavigator.LoadedView(new StackPane(), controller);
        });
        result.setNavigationListener(highlights::add);
        return result;
    }

    @ParameterizedTest
    @EnumSource(value = ViewType.class,
            names = {"RECIPES", "RECOMMENDATION", "SEARCH", "WEEK_PLAN"})
    void detailReturnsToIdenticalOriginWithoutReloading(ViewType origin) {
        navigator.navigateTo(origin);
        var root = host.getChildren().getFirst();
        var controller = controllers.getFirst();
        Object state = new Object();
        root.setUserData(state);
        navigator.navigateToRecipeDetail(recipe);
        assertEquals(origin, highlights.getLast());
        navigator.returnFromRecipeDetail(false);

        assertSame(root, host.getChildren().getFirst());
        assertSame(state, host.getChildren().getFirst().getUserData());
        assertEquals(1, controller.returns);
        assertEquals(List.of(origin, ViewType.RECIPE_DETAIL), loads);
        assertEquals(origin, highlights.getLast());
    }

    @Test
    void repeatedDetailVisitsRetainOneOriginAndDoNotCreateAnotherRound() {
        navigator.navigateTo(ViewType.RECOMMENDATION);
        var root = host.getChildren().getFirst();
        for (int index = 0; index < 3; index++) {
            navigator.navigateToRecipeDetail(new RecipeDetailContext(recipe, 3, Map.of()));
            navigator.returnFromRecipeDetail(false);
            assertSame(root, host.getChildren().getFirst());
        }
        assertEquals(1, loads.stream().filter(type -> type == ViewType.RECOMMENDATION).count());
        assertEquals(3, controllers.getFirst().returns);
    }

    @Test
    void explicitMainNavigationReleasesThePreviousRound() {
        navigator.navigateTo(ViewType.RECOMMENDATION);
        var oldRound = host.getChildren().getFirst();
        navigator.navigateToRecipeDetail(recipe);
        navigator.navigateTo(ViewType.INVENTORY);
        navigator.navigateTo(ViewType.RECOMMENDATION);
        var newRound = host.getChildren().getFirst();
        assertNotSame(oldRound, newRound);
        navigator.navigateToRecipeDetail(recipe);
        navigator.returnFromRecipeDetail(false);
        assertSame(newRound, host.getChildren().getFirst());
        assertEquals(0, controllers.getFirst().returns);
    }

    @Test
    void editCancellationKeepsTheOriginButSavingReloadsItOnReturn() {
        navigator.navigateTo(ViewType.RECOMMENDATION);
        var original = host.getChildren().getFirst();
        navigator.navigateToRecipeDetail(recipe);
        navigator.navigateToRecipeEdit(recipe);
        navigator.navigateToRecipeDetail(recipe);
        navigator.returnFromRecipeDetail(false);
        assertSame(original, host.getChildren().getFirst());
        navigator.navigateToRecipeDetail(recipe);
        navigator.navigateToRecipeEdit(recipe);
        navigator.navigateToUpdatedRecipeDetail(recipe);
        navigator.returnFromRecipeDetail(false);
        assertNotSame(original, host.getChildren().getFirst());
        assertEquals(ViewType.RECOMMENDATION, loads.getLast());
    }

    @Test
    void deletingReloadsTheActualOrigin() {
        navigator.navigateTo(ViewType.WEEK_PLAN);
        var original = host.getChildren().getFirst();
        navigator.navigateToRecipeDetail(recipe);
        navigator.returnFromRecipeDetail(true);
        assertEquals(ViewType.WEEK_PLAN, loads.getLast());
        assertNotSame(original, host.getChildren().getFirst());
    }

    @Test
    void failedMainNavigationDoesNotDestroyThePendingReturn() {
        navigator.navigateTo(ViewType.RECOMMENDATION);
        var original = host.getChildren().getFirst();
        navigator.navigateToRecipeDetail(recipe);
        failingRoute = ViewType.INVENTORY;
        assertThrows(ViewLoadingException.class, () -> navigator.navigateTo(ViewType.INVENTORY));
        navigator.returnFromRecipeDetail(false);
        assertSame(original, host.getChildren().getFirst());
    }

    @Test
    void detailWithoutOriginFallsBackToLibrary() {
        navigator.navigateToRecipeDetail(recipe);
        navigator.returnFromRecipeDetail(false);
        assertEquals(ViewType.RECIPES, loads.getLast());
    }

    private static final class OriginController implements NavigationAware {
        private int returns;
        @Override public void setNavigator(ViewNavigator navigator) { }
        @Override public void onReturnFromDetail() { returns++; }
    }
}
