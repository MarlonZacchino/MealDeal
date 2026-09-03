package de.mealdeal.ui.controller;

import de.mealdeal.domain.DishType;
import de.mealdeal.domain.Recipe;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.repository.RecipeRepository;
import de.mealdeal.ui.navigation.NavigationAware;
import de.mealdeal.ui.navigation.ViewNavigator;
import de.mealdeal.ui.navigation.ViewType;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Loads recipes through the repository and renders their compact list state. */
public final class RecipesController implements NavigationAware {

    private static final System.Logger LOGGER = System.getLogger(RecipesController.class.getName());
    private static final Comparator<Recipe> RECIPE_ORDER = Comparator
            .comparing(Recipe::getName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Recipe::getName)
            .thenComparing(Recipe::getId);

    private final RecipeRepository recipeRepository;
    private final ToggleGroup selection = new ToggleGroup();
    private final List<RecipeGrid> recipeGrids = new ArrayList<>();
    private final ListChangeListener<String> viewportListener = ignored -> layoutRecipeGrids();
    private Consumer<Recipe> detailNavigation;
    private ViewNavigator navigator;
    private Scene observedScene;

    @FXML
    private VBox recipeGroupsContainer;
    @FXML private GridPane mainRecipesContainer;
    @FXML private GridPane sideRecipesContainer;
    @FXML private GridPane dessertRecipesContainer;
    @FXML private TitledPane mainRecipesPane;
    @FXML private TitledPane sideRecipesPane;
    @FXML private TitledPane dessertRecipesPane;
    @FXML
    private VBox errorState;
    @FXML
    private Label errorMessage;

    /** Creates the controller with the repository used whenever the view is opened. */
    public RecipesController(RecipeRepository recipeRepository) {
        this(recipeRepository, ignored -> {
            throw new IllegalStateException("Navigator has not been set.");
        });
    }

    RecipesController(RecipeRepository recipeRepository, Consumer<Recipe> detailNavigation) {
        this.recipeRepository = Objects.requireNonNull(
                recipeRepository, "Recipe repository must not be null.");
        this.detailNavigation = Objects.requireNonNull(
                detailNavigation, "Detail navigation must not be null.");
    }

    @Override
    public void setNavigator(ViewNavigator navigator) {
        this.navigator = Objects.requireNonNull(navigator, "Navigator must not be null.");
        detailNavigation = navigator::navigateToRecipeDetail;
    }

    @FXML
    private void initialize() {
        recipeGroupsContainer.sceneProperty().addListener((ignored, previous, current) -> {
            if (previous != null) {
                previous.getRoot().getStyleClass().removeListener(viewportListener);
            }
            observedScene = current;
            if (current != null) {
                current.getRoot().getStyleClass().addListener(viewportListener);
            }
            layoutRecipeGrids();
        });
        refresh();
    }

    /** Reloads the current repository data and updates the visible list state. */
    @FXML
    public void refresh() {
        try {
            showRecipes(loadSortedRecipes());
        } catch (PersistenceException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not load recipes.", exception);
            showLoadError();
        }
    }

    @FXML
    private void openCreateRecipe() {
        navigator.navigateTo(ViewType.CREATE_RECIPE);
    }

    List<Recipe> loadSortedRecipes() {
        return recipeRepository.findAll().stream()
                .sorted(RECIPE_ORDER)
                .toList();
    }

    private void showRecipes(List<Recipe> recipes) {
        selection.selectToggle(null);
        recipeGrids.clear();
        Map<DishType, List<Recipe>> groups = groupByDishType(recipes);
        renderGroup(mainRecipesContainer, groups.get(DishType.MAIN), "Hauptgerichte");
        renderGroup(sideRecipesContainer, groups.get(DishType.SIDE), "Beilagen");
        renderGroup(dessertRecipesContainer, groups.get(DishType.DESSERT), "Nachtische");
        mainRecipesPane.setText(groupTitle("Hauptgerichte", groups.get(DishType.MAIN).size()));
        sideRecipesPane.setText(groupTitle("Beilagen", groups.get(DishType.SIDE).size()));
        dessertRecipesPane.setText(groupTitle("Nachtische", groups.get(DishType.DESSERT).size()));
        setVisibleState(recipeGroupsContainer);
    }

    Map<DishType, List<Recipe>> groupByDishType(List<Recipe> recipes) {
        EnumMap<DishType, List<Recipe>> groups = new EnumMap<>(DishType.class);
        for (DishType dishType : DishType.values()) {
            groups.put(dishType, recipes.stream()
                    .filter(recipe -> recipe.getDishType() == dishType)
                    .toList());
        }
        return Map.copyOf(groups);
    }

    private void renderGroup(GridPane container, List<Recipe> recipes, String groupName) {
        container.getChildren().clear();
        container.getColumnConstraints().clear();
        if (recipes.isEmpty()) {
            Label empty = new Label("Noch keine " + groupName.toLowerCase(java.util.Locale.GERMAN)
                    + " gespeichert.");
            empty.setWrapText(true);
            empty.setMaxWidth(Double.MAX_VALUE);
            empty.getStyleClass().add("recipe-group-empty");
            container.add(empty, 0, 0);
            GridPane.setHgrow(empty, Priority.ALWAYS);
            return;
        }
        RecipeGrid grid = new RecipeGrid(container,
                recipes.stream().map(this::createRecipeEntry).toList());
        recipeGrids.add(grid);
        layoutRecipeGrid(grid, currentRecipeColumnCount());
    }

    private static String groupTitle(String name, int count) {
        return name + " (" + count + ")";
    }

    private ToggleButton createRecipeEntry(Recipe recipe) {
        Label name = new Label(recipe.getName());
        name.getStyleClass().add("recipe-name");
        name.setMinWidth(0);
        name.setMaxWidth(Double.MAX_VALUE);
        name.setWrapText(true);
        HBox.setHgrow(name, Priority.ALWAYS);

        Label dishType = new Label(GermanRecipeDisplay.dishType(recipe.getDishType()));
        dishType.getStyleClass().add("recipe-type-badge");

        HBox heading = new HBox(12, name, dishType);
        heading.setAlignment(Pos.CENTER_LEFT);
        heading.setMinWidth(0);
        heading.setMaxWidth(Double.MAX_VALUE);

        Label facts = new Label(recipeFacts(recipe));
        facts.getStyleClass().add("recipe-facts");
        facts.setMinWidth(0);
        facts.setMaxWidth(Double.MAX_VALUE);
        facts.setWrapText(true);

        Label tastes = new Label(tasteSummary(recipe));
        tastes.getStyleClass().add("recipe-tastes");
        tastes.setMinWidth(0);
        tastes.setMaxWidth(Double.MAX_VALUE);
        tastes.setWrapText(true);

        VBox summary = new VBox(6, heading, facts, tastes);
        summary.setAlignment(Pos.CENTER_LEFT);
        summary.setMinWidth(0);
        summary.setMaxWidth(Double.MAX_VALUE);
        summary.setMaxHeight(Region.USE_PREF_SIZE);

        ToggleButton entry = new ToggleButton();
        entry.setGraphic(summary);
        entry.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        entry.setToggleGroup(selection);
        entry.setMinWidth(0);
        entry.setMaxWidth(Double.MAX_VALUE);
        entry.setMaxHeight(Region.USE_PREF_SIZE);
        entry.setAlignment(Pos.CENTER_LEFT);
        entry.setUserData(recipe.getId());
        entry.setAccessibleText("Gericht " + recipe.getName());
        entry.getStyleClass().add("recipe-list-item");
        entry.setOnAction(ignored -> openRecipe(recipe));
        summary.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(0, entry.getWidth()
                        - entry.getInsets().getLeft() - entry.getInsets().getRight()),
                entry.widthProperty(), entry.insetsProperty()));
        return entry;
    }

    private void layoutRecipeGrids() {
        int columns = currentRecipeColumnCount();
        recipeGrids.forEach(grid -> layoutRecipeGrid(grid, columns));
    }

    private int currentRecipeColumnCount() {
        return recipeColumnsFor(observedScene == null
                ? List.of() : observedScene.getRoot().getStyleClass());
    }

    static int recipeColumnsFor(List<String> viewportStyleClasses) {
        if (viewportStyleClasses.contains("viewport-compact")) {
            return 1;
        }
        if (viewportStyleClasses.contains("viewport-wide")) {
            return 3;
        }
        return 2;
    }

    private static void layoutRecipeGrid(RecipeGrid recipeGrid, int columns) {
        GridPane grid = recipeGrid.grid();
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        for (int columnIndex = 0; columnIndex < columns; columnIndex++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / columns);
            column.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(column);
        }
        for (int index = 0; index < recipeGrid.entries().size(); index++) {
            ToggleButton entry = recipeGrid.entries().get(index);
            grid.add(entry, index % columns, index / columns);
            GridPane.setHgrow(entry, Priority.ALWAYS);
            GridPane.setVgrow(entry, Priority.NEVER);
            GridPane.setFillWidth(entry, true);
            GridPane.setFillHeight(entry, false);
            GridPane.setValignment(entry, VPos.TOP);
        }
    }

    void openRecipe(Recipe recipe) {
        detailNavigation.accept(Objects.requireNonNull(recipe, "Recipe must not be null."));
    }

    private void showLoadError() {
        errorMessage.setText(
                "Die gespeicherten Gerichte konnten nicht geladen werden. Bitte versuche es erneut.");
        setVisibleState(errorState);
    }

    private void setVisibleState(VBox visibleState) {
        for (VBox state : List.of(recipeGroupsContainer, errorState)) {
            boolean visible = state == visibleState;
            state.setManaged(visible);
            state.setVisible(visible);
        }
    }

    private static String recipeFacts(Recipe recipe) {
        return servingText(recipe.getStandardServingCount())
                + "  ·  " + countText(recipe.getIngredients().size(), "Zutat", "Zutaten")
                + "  ·  " + countText(recipe.getSteps().size(), "Schritt", "Schritte");
    }

    private static String tasteSummary(Recipe recipe) {
        String names = recipe.getTastes().stream()
                .map(taste -> taste.getName())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .reduce((first, second) -> first + ", " + second)
                .orElse("Keine Angabe");
        return "Geschmack: " + names;
    }

    private static String servingText(int count) {
        return countText(count, "Person", "Personen");
    }

    private static String countText(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }

    private record RecipeGrid(GridPane grid, List<ToggleButton> entries) {
        private RecipeGrid {
            entries = List.copyOf(entries);
        }
    }
}
