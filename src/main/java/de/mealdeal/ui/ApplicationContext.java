package de.mealdeal.ui;

import de.mealdeal.persistence.repository.IngredientRepository;
import de.mealdeal.persistence.repository.RecipeRepository;
import de.mealdeal.persistence.repository.TasteRepository;
import de.mealdeal.persistence.sqlite.SqliteDatabase;
import de.mealdeal.persistence.sqlite.SqliteIngredientRepository;
import de.mealdeal.persistence.sqlite.SqliteRecipeRepository;
import de.mealdeal.persistence.sqlite.SqliteTasteRepository;
import de.mealdeal.ui.controller.CreateRecipeController;
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
    private final IngredientRepository ingredientRepository;
    private final TasteRepository tasteRepository;

    /** Creates the production composition backed by the configured SQLite file. */
    public ApplicationContext(Path databasePath) {
        this(new SqliteDatabase(
                Objects.requireNonNull(databasePath, "Database path must not be null.")));
    }

    private ApplicationContext(SqliteDatabase database) {
        this(new SqliteRecipeRepository(database), new SqliteIngredientRepository(database),
                new SqliteTasteRepository(database));
    }

    /** Creates a composition with explicit repositories, primarily for isolated tests. */
    public ApplicationContext(RecipeRepository recipeRepository,
                              IngredientRepository ingredientRepository,
                              TasteRepository tasteRepository) {
        this.recipeRepository = Objects.requireNonNull(
                recipeRepository, "Recipe repository must not be null.");
        this.ingredientRepository = Objects.requireNonNull(
                ingredientRepository, "Ingredient repository must not be null.");
        this.tasteRepository = Objects.requireNonNull(
                tasteRepository, "Taste repository must not be null.");
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
        if (controllerType == CreateRecipeController.class) {
            return new CreateRecipeController(
                    recipeRepository, ingredientRepository, tasteRepository);
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
