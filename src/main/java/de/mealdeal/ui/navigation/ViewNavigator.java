package de.mealdeal.ui.navigation;

import de.mealdeal.domain.Recipe;
import de.mealdeal.ui.ApplicationContext;
import de.mealdeal.ui.ViewLoadingException;
import de.mealdeal.ui.controller.CreateRecipeController;
import de.mealdeal.ui.controller.RecipeDetailController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.BiFunction;

/** Loads FXML views into one existing content area of the primary window. */
public final class ViewNavigator {

    private final StackPane contentHost;
    private final BiFunction<ViewType, Consumer<Object>, LoadedView> viewLoader;
    private Consumer<ViewType> navigationListener = ignored -> { };
    private LoadedView currentView;
    private ViewType currentType;
    private LoadedView detailOrigin;
    private ViewType originType;
    private RecipeDetailContext detailContext;

    /** Creates a navigator bound to one content area of the application shell. */
    public ViewNavigator(StackPane contentHost, ApplicationContext applicationContext) {
        this.contentHost = Objects.requireNonNull(contentHost, "Content host must not be null.");
        Objects.requireNonNull(applicationContext, "Application context must not be null.");
        viewLoader = (type, initializer) -> loadView(applicationContext, type, initializer);
    }

    /** Allows navigation tests to supply views without starting the JavaFX toolkit. */
    ViewNavigator(StackPane host, BiFunction<ViewType, Consumer<Object>, LoadedView> loader) {
        contentHost = Objects.requireNonNull(host);
        viewLoader = Objects.requireNonNull(loader);
    }

    /** Registers the callback used to reflect the active destination in the shell. */
    public void setNavigationListener(Consumer<ViewType> navigationListener) {
        this.navigationListener = Objects.requireNonNull(
                navigationListener, "Navigation listener must not be null.");
    }

    /** Replaces the content in the current window with the requested view. */
    public void navigateTo(ViewType viewType) {
        LoadedView loaded = viewLoader.apply(viewType, ignored -> { });
        detailOrigin = null;
        originType = null;
        detailContext = null;
        show(loaded, viewType, viewType);
    }

    /** Opens the detail view for one recipe in the existing content area. */
    public void navigateToRecipeDetail(Recipe recipe) {
        RecipeDetailContext context = detailContext != null
                && detailContext.recipe() == recipe ? detailContext : RecipeDetailContext.standard(recipe);
        navigateToRecipeDetail(context);
    }

    /** Opens the shared detail view with temporary request-specific choices. */
    public void navigateToRecipeDetail(RecipeDetailContext context) {
        Objects.requireNonNull(context, "Detail context must not be null.");
        LoadedView loaded = viewLoader.apply(ViewType.RECIPE_DETAIL, controller -> {
            if (!(controller instanceof RecipeDetailController detailController)) {
                throw new ViewLoadingException("Recipe detail view has an unexpected controller.");
            }
            detailController.showRecipe(context);
        });
        if (currentType != ViewType.RECIPE_DETAIL && currentType != ViewType.CREATE_RECIPE) {
            detailOrigin = currentView;
            originType = currentType;
        }
        detailContext = context;
        show(loaded, ViewType.RECIPE_DETAIL, originRoute());
    }

    /** Recipe edits invalidate old result snapshots, while preserving the origin route. */
    public void navigateToUpdatedRecipeDetail(Recipe recipe) {
        navigateToRecipeDetail(RecipeDetailContext.standard(recipe));
        detailOrigin = null;
    }

    /** Restores the origin once; deletion instead reloads it to avoid stale results. */
    public void returnFromRecipeDetail(boolean recipeChanged) {
        if (detailOrigin == null || recipeChanged) {
            navigateTo(originRoute());
            return;
        }
        LoadedView restored = detailOrigin;
        ViewType route = originRoute();
        detailOrigin = null;
        originType = null;
        detailContext = null;
        show(restored, route, route);
        if (restored.controller() instanceof NavigationAware aware) {
            aware.onReturnFromDetail();
        }
    }

    /** Opens the shared recipe form prefilled for editing the supplied recipe. */
    public void navigateToRecipeEdit(Recipe recipe) {
        Objects.requireNonNull(recipe, "Recipe must not be null.");
        LoadedView loaded = viewLoader.apply(ViewType.CREATE_RECIPE, controller -> {
            if (!(controller instanceof CreateRecipeController formController)) {
                throw new ViewLoadingException("Recipe form has an unexpected controller.");
            }
            formController.editRecipe(recipe);
        });
        show(loaded, ViewType.CREATE_RECIPE, originRoute());
    }

    private ViewType originRoute() {
        return originType == null ? ViewType.RECIPES : originType;
    }

    private void show(LoadedView loaded, ViewType type, ViewType highlightedRoute) {
        currentView = loaded;
        currentType = type;
        contentHost.getChildren().setAll(loaded.root());
        navigationListener.accept(highlightedRoute);
    }

    record LoadedView(Parent root, Object controller) { }

    private LoadedView loadView(ApplicationContext applicationContext, ViewType viewType,
                                Consumer<Object> controllerInitializer) {
        Objects.requireNonNull(viewType, "View type must not be null.");
        URL resource = ViewNavigator.class.getResource(viewType.getResourcePath());
        if (resource == null) {
            throw new ViewLoadingException("View resource not found: " + viewType.getResourcePath());
        }

        FXMLLoader loader = new FXMLLoader(resource);
        loader.setControllerFactory(applicationContext::createController);
        try {
            Parent view = loader.load();
            Object controller = loader.getController();
            controllerInitializer.accept(controller);
            if (controller instanceof NavigationAware navigationAware) {
                navigationAware.setNavigator(this);
            }
            return new LoadedView(view, controller);
        } catch (IOException exception) {
            throw new ViewLoadingException(
                    "Could not load view " + viewType + " from "
                            + viewType.getResourcePath() + ".", exception);
        }
    }
}
