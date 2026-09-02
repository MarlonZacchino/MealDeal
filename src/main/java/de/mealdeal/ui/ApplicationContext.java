package de.mealdeal.ui;

import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.repository.IngredientRepository;
import de.mealdeal.persistence.repository.IngredientCategoryRepository;
import de.mealdeal.persistence.repository.MealPlanRepository;
import de.mealdeal.persistence.repository.InventoryRepository;
import de.mealdeal.persistence.repository.InventoryConsumptionRepository;
import de.mealdeal.persistence.repository.RecipeRepository;
import de.mealdeal.persistence.repository.TasteRepository;
import de.mealdeal.persistence.sqlite.SqliteDatabase;
import de.mealdeal.persistence.sqlite.SqliteIngredientRepository;
import de.mealdeal.persistence.sqlite.SqliteIngredientCategoryRepository;
import de.mealdeal.persistence.sqlite.SqliteMealPlanRepository;
import de.mealdeal.persistence.sqlite.SqliteInventoryRepository;
import de.mealdeal.persistence.sqlite.SqliteInventoryConsumptionRepository;
import de.mealdeal.persistence.sqlite.SqliteRecipeRepository;
import de.mealdeal.persistence.sqlite.SqliteTasteRepository;
import de.mealdeal.ui.controller.CreateRecipeController;
import de.mealdeal.ui.controller.HomeController;
import de.mealdeal.ui.controller.IngredientSearchController;
import de.mealdeal.ui.controller.InventoryController;
import de.mealdeal.ui.controller.MainController;
import de.mealdeal.ui.controller.RecipeDetailController;
import de.mealdeal.ui.controller.RecipesController;
import de.mealdeal.ui.controller.ShoppingListController;
import de.mealdeal.ui.controller.WeekPlanController;
import de.mealdeal.service.CombinedRecipeSearchService;
import de.mealdeal.service.InventoryService;
import de.mealdeal.service.InventoryConsumptionService;
import de.mealdeal.service.IngredientCategoryService;
import de.mealdeal.service.IngredientManagementService;
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
    private final IngredientCategoryRepository ingredientCategoryRepository;
    private final TasteRepository tasteRepository;
    private final MealPlanRepository mealPlanRepository;
    private final InventoryRepository inventoryRepository;
    private final RecipeScaler recipeScaler = new RecipeScaler();
    private final RecipeSearchService recipeSearchService = new RecipeSearchService();
    private final CombinedRecipeSearchService combinedSearchService =
            new CombinedRecipeSearchService(recipeSearchService);
    private final ThemeService themeService;
    private final WeeklyMealPlanService weeklyMealPlanService;
    private final ShoppingListService shoppingListService;
    private final InventoryService inventoryService;
    private final IngredientCategoryService ingredientCategoryService;
    private final IngredientManagementService ingredientManagementService;
    private final InventoryConsumptionService inventoryConsumptionService;

    /**
     * Preserves the earlier isolated-test composition without requiring a meal-plan fixture.
     * The week view reports its normal load error when this limited composition is used.
     */
    public ApplicationContext(RecipeRepository recipeRepository,
                              IngredientRepository ingredientRepository,
                              TasteRepository tasteRepository) {
        this(recipeRepository, ingredientRepository, tasteRepository,
                new UnavailableMealPlanRepository(), new CatalogIngredientCategoryRepository(),
                new ThemeService());
    }

    /** Preserves the earlier isolated-test composition with an explicit theme service. */
    public ApplicationContext(RecipeRepository recipeRepository,
                              IngredientRepository ingredientRepository,
                              TasteRepository tasteRepository,
                              ThemeService themeService) {
        this(recipeRepository, ingredientRepository, tasteRepository,
                new UnavailableMealPlanRepository(), new CatalogIngredientCategoryRepository(),
                themeService);
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
                new SqliteIngredientCategoryRepository(database),
                new SqliteInventoryRepository(database),
                new SqliteInventoryConsumptionRepository(database), themeService);
        inventoryConsumptionService.consumePastEntries();
    }

    /** Creates a composition with explicit repositories, primarily for isolated tests. */
    public ApplicationContext(RecipeRepository recipeRepository,
                              IngredientRepository ingredientRepository,
                              TasteRepository tasteRepository,
                              MealPlanRepository mealPlanRepository) {
        this(recipeRepository, ingredientRepository, tasteRepository,
                mealPlanRepository, new CatalogIngredientCategoryRepository(),
                new ThemeService());
    }

    /** Creates a composition with explicit repositories and theme service. */
    public ApplicationContext(RecipeRepository recipeRepository,
                              IngredientRepository ingredientRepository,
                              TasteRepository tasteRepository,
                              MealPlanRepository mealPlanRepository,
                              ThemeService themeService) {
        this(recipeRepository, ingredientRepository, tasteRepository, mealPlanRepository,
                new CatalogIngredientCategoryRepository(), themeService);
    }

    /** Creates a composition with explicit persistence-backed category reference data. */
    public ApplicationContext(RecipeRepository recipeRepository,
                              IngredientRepository ingredientRepository,
                              TasteRepository tasteRepository,
                              MealPlanRepository mealPlanRepository,
                              IngredientCategoryRepository ingredientCategoryRepository,
                              ThemeService themeService) {
        this(recipeRepository, ingredientRepository, tasteRepository, mealPlanRepository,
                ingredientCategoryRepository, new EmptyInventoryRepository(),
                null, themeService, false);
    }

    /** Creates a composition with explicit inventory persistence, primarily for tests. */
    public ApplicationContext(RecipeRepository recipeRepository,
                              IngredientRepository ingredientRepository,
                              TasteRepository tasteRepository,
                              MealPlanRepository mealPlanRepository,
                              IngredientCategoryRepository ingredientCategoryRepository,
                              InventoryRepository inventoryRepository,
                              ThemeService themeService) {
        this(recipeRepository, ingredientRepository, tasteRepository, mealPlanRepository,
                ingredientCategoryRepository, inventoryRepository,
                null, themeService, false);
    }

    /** Creates a composition with explicit consumption persistence. */
    public ApplicationContext(RecipeRepository recipeRepository,
                              IngredientRepository ingredientRepository,
                              TasteRepository tasteRepository,
                              MealPlanRepository mealPlanRepository,
                              IngredientCategoryRepository ingredientCategoryRepository,
                              InventoryRepository inventoryRepository,
                              InventoryConsumptionRepository consumptionRepository,
                              ThemeService themeService) {
        this(recipeRepository, ingredientRepository, tasteRepository, mealPlanRepository,
                ingredientCategoryRepository, inventoryRepository, consumptionRepository,
                themeService, true);
    }

    private ApplicationContext(RecipeRepository recipeRepository,
                               IngredientRepository ingredientRepository,
                               TasteRepository tasteRepository,
                               MealPlanRepository mealPlanRepository,
                               IngredientCategoryRepository ingredientCategoryRepository,
                               InventoryRepository inventoryRepository,
                               InventoryConsumptionRepository consumptionRepository,
                               ThemeService themeService,
                               boolean consumptionEnabled) {
        this.recipeRepository = Objects.requireNonNull(
                recipeRepository, "Recipe repository must not be null.");
        this.ingredientRepository = Objects.requireNonNull(
                ingredientRepository, "Ingredient repository must not be null.");
        this.ingredientCategoryRepository = Objects.requireNonNull(
                ingredientCategoryRepository,
                "Ingredient category repository must not be null.");
        this.tasteRepository = Objects.requireNonNull(
                tasteRepository, "Taste repository must not be null.");
        this.mealPlanRepository = Objects.requireNonNull(
                mealPlanRepository, "Meal plan repository must not be null.");
        this.inventoryRepository = Objects.requireNonNull(
                inventoryRepository, "Inventory repository must not be null.");
        this.themeService = Objects.requireNonNull(
                themeService, "Theme service must not be null.");
        this.weeklyMealPlanService = new WeeklyMealPlanService(
                this.mealPlanRepository, this.recipeRepository);
        this.inventoryConsumptionService = consumptionEnabled
                ? new InventoryConsumptionService(this.mealPlanRepository,
                        this.inventoryRepository, Objects.requireNonNull(consumptionRepository,
                                "Consumption repository must not be null."))
                : null;
        this.inventoryService = new InventoryService(
                this.inventoryRepository, this.ingredientRepository);
        this.ingredientCategoryService = new IngredientCategoryService(
                this.ingredientCategoryRepository);
        this.ingredientManagementService = new IngredientManagementService(
                this.ingredientRepository, this.ingredientCategoryService);
        this.shoppingListService = consumptionEnabled
                ? new ShoppingListService(this.mealPlanRepository, this.inventoryRepository,
                        this.inventoryConsumptionService)
                : new ShoppingListService(this.mealPlanRepository, this.inventoryRepository);
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
            return new HomeController(weeklyMealPlanService);
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
                    recipeRepository, ingredientRepository, tasteRepository,
                    ingredientCategoryRepository);
        }
        if (controllerType == WeekPlanController.class) {
            return new WeekPlanController(weeklyMealPlanService);
        }
        if (controllerType == ShoppingListController.class) {
            return new ShoppingListController(shoppingListService);
        }
        if (controllerType == InventoryController.class) {
            return new InventoryController(inventoryService, ingredientCategoryService,
                    ingredientManagementService, inventoryConsumptionService);
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
        public void applyChanges(List<MealPlanEntry> entriesToSave, List<UUID> entryIdsToDelete) {
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

    private static final class CatalogIngredientCategoryRepository
            implements IngredientCategoryRepository {

        @Override
        public void save(de.mealdeal.domain.IngredientCategory category) {
            throw new UnsupportedOperationException("The built-in category catalog is read-only.");
        }

        @Override
        public Optional<de.mealdeal.domain.IngredientCategory> findById(UUID id) {
            return de.mealdeal.domain.IngredientCategories.all().stream()
                    .filter(category -> category.getId().equals(id)).findFirst();
        }

        @Override
        public List<de.mealdeal.domain.IngredientCategory> findAll() {
            return de.mealdeal.domain.IngredientCategories.all();
        }

        @Override
        public void replaceAll(List<de.mealdeal.domain.IngredientCategory> categories) {
            throw new UnsupportedOperationException("The built-in category catalog is read-only.");
        }

        @Override
        public int countIngredients(UUID categoryId) {
            return 0;
        }

        @Override
        public void deleteAndReassign(UUID categoryId, UUID fallbackCategoryId,
                                      List<de.mealdeal.domain.IngredientCategory> categories) {
            throw new UnsupportedOperationException("The built-in category catalog is read-only.");
        }
    }

    private static final class EmptyInventoryRepository implements InventoryRepository {
        @Override public void save(de.mealdeal.domain.InventoryItem item) {
            throw new UnsupportedOperationException(
                    "Inventory repository is not configured in this isolated composition.");
        }
        @Override public Optional<de.mealdeal.domain.InventoryItem> findById(UUID id) {
            return Optional.empty();
        }
        @Override public List<de.mealdeal.domain.InventoryItem> findAll() {
            return List.of();
        }
        @Override public boolean deleteById(UUID id) { return false; }
    }

}
