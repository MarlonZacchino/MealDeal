package de.mealdeal.ui;

import de.mealdeal.persistence.repository.RecipeRepository;
import de.mealdeal.persistence.sqlite.SqliteDatabase;
import de.mealdeal.persistence.sqlite.SqliteRecipeRepository;
import de.mealdeal.ui.controller.HomeController;
import de.mealdeal.ui.controller.MainController;
import de.mealdeal.ui.controller.RecipesController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Manually composes the JavaFX shell and its controllers.
 *
 * <p>Later phases can pass repositories and services into controller
 * constructors here without introducing a dependency-injection framework.</p>
 */
public final class ApplicationContext {

    private static final String MAIN_VIEW_RESOURCE = "/de/mealdeal/ui/main-view.fxml";
    private final RecipeRepository recipeRepository;

    /** Creates the production composition backed by the configured SQLite file. */
    public ApplicationContext(Path databasePath) {
        this(new SqliteRecipeRepository(new SqliteDatabase(
                Objects.requireNonNull(databasePath, "Database path must not be null."))));
    }

    /** Creates a composition with an explicit repository, primarily for isolated tests. */
    public ApplicationContext(RecipeRepository recipeRepository) {
        this.recipeRepository = Objects.requireNonNull(
                recipeRepository, "Recipe repository must not be null.");
    }

    /** Loads the application's single main view. */
    public Parent loadMainView() {
        FXMLLoader loader = new FXMLLoader(resource(MAIN_VIEW_RESOURCE));
        loader.setControllerFactory(this::createController);
        try {
            return loader.load();
        } catch (IOException exception) {
            throw new ViewLoadingException("Could not load the MealDeal main view.", exception);
        }
    }

    /** Creates UI controllers explicitly so dependencies remain visible. */
    public Object createController(Class<?> controllerType) {
        Objects.requireNonNull(controllerType, "Controller type must not be null.");
        if (controllerType == MainController.class) {
            return new MainController(this);
        }
        if (controllerType == HomeController.class) {
            return new HomeController();
        }
        if (controllerType == RecipesController.class) {
            return new RecipesController(recipeRepository);
        }
        try {
            return controllerType.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                 | NoSuchMethodException exception) {
            throw new ViewLoadingException(
                    "Could not create controller " + controllerType.getName() + ".", exception);
        }
    }

    private static java.net.URL resource(String path) {
        java.net.URL resource = ApplicationContext.class.getResource(path);
        if (resource == null) {
            throw new ViewLoadingException("UI resource not found: " + path);
        }
        return resource;
    }
}
