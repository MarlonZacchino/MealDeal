package de.mealdeal.ui.navigation;

import de.mealdeal.domain.Recipe;
import de.mealdeal.ui.ApplicationContext;
import de.mealdeal.ui.ViewLoadingException;
import de.mealdeal.ui.controller.RecipeDetailController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.function.Consumer;

/** Loads FXML views into one existing content area of the primary window. */
public final class ViewNavigator {

    private final StackPane contentHost;
    private final ApplicationContext applicationContext;
    private Consumer<ViewType> navigationListener = ignored -> { };

    /** Creates a navigator bound to one content area of the application shell. */
    public ViewNavigator(StackPane contentHost, ApplicationContext applicationContext) {
        this.contentHost = Objects.requireNonNull(contentHost, "Content host must not be null.");
        this.applicationContext = Objects.requireNonNull(
                applicationContext, "Application context must not be null.");
    }

    /** Registers the callback used to reflect the active destination in the shell. */
    public void setNavigationListener(Consumer<ViewType> navigationListener) {
        this.navigationListener = Objects.requireNonNull(
                navigationListener, "Navigation listener must not be null.");
    }

    /** Replaces the content in the current window with the requested view. */
    public void navigateTo(ViewType viewType) {
        loadView(viewType, ignored -> { });
    }

    /** Opens the detail view for one recipe in the existing content area. */
    public void navigateToRecipeDetail(Recipe recipe) {
        Objects.requireNonNull(recipe, "Recipe must not be null.");
        loadView(ViewType.RECIPE_DETAIL, controller -> {
            if (!(controller instanceof RecipeDetailController detailController)) {
                throw new ViewLoadingException("Recipe detail view has an unexpected controller.");
            }
            detailController.showRecipe(recipe);
        });
    }

    private void loadView(ViewType viewType, Consumer<Object> controllerInitializer) {
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
            contentHost.getChildren().setAll(view);
            navigationListener.accept(viewType);
        } catch (IOException exception) {
            throw new ViewLoadingException(
                    "Could not load view " + viewType + " from "
                            + viewType.getResourcePath() + ".", exception);
        }
    }
}
