package de.mealdeal.ui;

import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.repository.IngredientRepository;
import de.mealdeal.persistence.repository.MealPlanRepository;
import de.mealdeal.persistence.repository.RecipeRepository;
import de.mealdeal.persistence.repository.TasteRepository;
import de.mealdeal.persistence.sqlite.SqliteDatabase;
import de.mealdeal.persistence.sqlite.SqliteIngredientRepository;
import de.mealdeal.persistence.sqlite.SqliteMealPlanRepository;
import de.mealdeal.persistence.sqlite.SqliteRecipeRepository;
import de.mealdeal.persistence.sqlite.SqliteTasteRepository;
import de.mealdeal.ui.controller.CreateRecipeController;
import de.mealdeal.ui.controller.HomeController;
import de.mealdeal.ui.controller.IngredientSearchController;
import de.mealdeal.ui.controller.MainController;
import de.mealdeal.ui.controller.RecipeDetailController;
import de.mealdeal.ui.controller.RecipesController;
import de.mealdeal.ui.controller.ShoppingListController;
import de.mealdeal.ui.controller.WeekPlanController;
import de.mealdeal.service.CombinedRecipeSearchService;
import de.mealdeal.service.RecipeScaler;
import de.mealdeal.service.RecipeSearchService;
import de.mealdeal.service.ShoppingListService;
import de.mealdeal.service.WeeklyMealPlanService;
import de.mealdeal.ui.theme.ThemeService;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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
    private final MealPlanRepository mealPlanRepository;
    private final RecipeScaler recipeScaler = new RecipeScaler();
    private final RecipeSearchService recipeSearchService = new RecipeSearchService();
    private final CombinedRecipeSearchService combinedSearchService =
            new CombinedRecipeSearchService(recipeSearchService);
    private final ThemeService themeService;
    private final WeeklyMealPlanService weeklyMealPlanService;
    private final ShoppingListService shoppingListService;

    /**
     * Preserves the earlier isolated-test composition without requiring a meal-plan fixture.
     * The week view reports its normal load error when this limited composition is used.
     */
    public ApplicationContext(RecipeRepository recipeRepository,
                              IngredientRepository ingredientRepository,
                              TasteRepository tasteRepository) {
        this(recipeRepository, ingredientRepository, tasteRepository,
                new UnavailableMealPlanRepository(), new ThemeService());
    }

    /** Preserves the earlier isolated-test composition with an explicit theme service. */
    public ApplicationContext(RecipeRepository recipeRepository,
                              IngredientRepository ingredientRepository,
                              TasteRepository tasteRepository,
                              ThemeService themeService) {
        this(recipeRepository, ingredientRepository, tasteRepository,
                new UnavailableMealPlanRepository(), themeService);
    }

    /** Creates the production composition backed by the configured SQLite file. */
    public ApplicationContext(Path databasePath) {
        this(new SqliteDatabase(
                        Objects.requireNonNull(databasePath, "Database path must not be null.")),
                new ThemeService(databasePath.resolveSibling(ThemeService.SETTINGS_FILE_NAME)));
    }

    private ApplicationContext(SqliteDatabase database, ThemeService themeService) {
        this(new SqliteRecipeRepository(database), new SqliteIngredientRepository(database),
                new SqliteTasteRepository(database), new SqliteMealPlanRepository(database),
                themeService);
    }

    /** Creates a composition with explicit repositories, primarily for isolated tests. */
    public ApplicationContext(RecipeRepository recipeRepository,
                              IngredientRepository ingredientRepository,
                              TasteRepository tasteRepository,
                              MealPlanRepository mealPlanRepository) {
        this(recipeRepository, ingredientRepository, tasteRepository,
                mealPlanRepository, new ThemeService());
    }

    /** Creates a composition with explicit repositories and theme service. */
    public ApplicationContext(RecipeRepository recipeRepository,
                              IngredientRepository ingredientRepository,
                              TasteRepository tasteRepository,
                              MealPlanRepository mealPlanRepository,
                              ThemeService themeService) {
        this.recipeRepository = Objects.requireNonNull(
                recipeRepository, "Recipe repository must not be null.");
        this.ingredientRepository = Objects.requireNonNull(
                ingredientRepository, "Ingredient repository must not be null.");
        this.tasteRepository = Objects.requireNonNull(
                tasteRepository, "Taste repository must not be null.");
        this.mealPlanRepository = Objects.requireNonNull(
                mealPlanRepository, "Meal plan repository must not be null.");
        this.themeService = Objects.requireNonNull(
                themeService, "Theme service must not be null.");
        this.weeklyMealPlanService = new WeeklyMealPlanService(
                this.mealPlanRepository, this.recipeRepository);
        this.shoppingListService = new ShoppingListService(this.mealPlanRepository);
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
            return new MainController(this, themeService);
        }
        if (controllerType == HomeController.class) {
            return new HomeController();
        }
        if (controllerType == RecipesController.class) {
            return new RecipesController(recipeRepository);
        }
        if (controllerType == RecipeDetailController.class) {
            return new RecipeDetailController(recipeRepository, recipeScaler);
        }
        if (controllerType == IngredientSearchController.class) {
            return new IngredientSearchController(
                    ingredientRepository, tasteRepository, recipeRepository,
                    recipeSearchService, combinedSearchService);
        }
        if (controllerType == CreateRecipeController.class) {
            return new CreateRecipeController(
                    recipeRepository, ingredientRepository, tasteRepository);
        }
        if (controllerType == WeekPlanController.class) {
            return new WeekPlanController(weeklyMealPlanService);
        }
        if (controllerType == ShoppingListController.class) {
            return new ShoppingListController(shoppingListService);
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

    private static final class UnavailableMealPlanRepository implements MealPlanRepository {

        @Override
        public void save(MealPlanEntry entry) {
            throw unavailable();
        }

        @Override
        public Optional<MealPlanEntry> findById(UUID id) {
            throw unavailable();
        }

        @Override
        public Optional<MealPlanEntry> findByDate(LocalDate date) {
            throw unavailable();
        }

        @Override
        public List<MealPlanEntry> findBetween(
                LocalDate startInclusive, LocalDate endInclusive) {
            throw unavailable();
        }

        @Override
        public boolean deleteById(UUID id) {
            throw unavailable();
        }

        @Override
        public int deleteBefore(LocalDate cutoffExclusive) {
            throw unavailable();
        }

        private static PersistenceException unavailable() {
            return new PersistenceException(
                    "Meal plan repository is not configured in this isolated composition.");
        }
    }
}
