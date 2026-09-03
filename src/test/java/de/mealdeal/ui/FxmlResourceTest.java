package de.mealdeal.ui;

import de.mealdeal.ui.navigation.ViewType;
import de.mealdeal.ui.controller.InventoryController;
import de.mealdeal.ui.controller.IngredientsController;
import javafx.css.CssParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxmlResourceTest {

    private static final String MAIN_VIEW = "/de/mealdeal/ui/main-view.fxml";
    private static final String STYLESHEET = "/de/mealdeal/ui/styles.css";
    private static final Pattern CSS_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern CSS_RULE = Pattern.compile("([^{}]+)\\{([^{}]*)}",
            Pattern.DOTALL);
    private static final List<String> STYLESHEET_MODULES = List.of(
            "/de/mealdeal/ui/styles/tokens.css",
            "/de/mealdeal/ui/styles/base.css",
            "/de/mealdeal/ui/styles/controls.css",
            "/de/mealdeal/ui/styles/components.css",
            "/de/mealdeal/ui/styles/recipes.css",
            "/de/mealdeal/ui/styles/search.css",
            "/de/mealdeal/ui/styles/planning.css",
            "/de/mealdeal/ui/styles/stock.css",
            "/de/mealdeal/ui/styles/responsive.css");

    @ParameterizedTest
    @MethodSource("allFxmlResources")
    void fxmlResourceExistsAndContainsValidXml(String resourcePath) throws Exception {
        try (InputStream input = FxmlResourceTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(input, () -> "Missing FXML resource: " + resourcePath);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder().parse(input);

            assertNotNull(document.getDocumentElement());
        }
    }

    @Test
    void everyNavigationDestinationUsesItsOwnResource() {
        long distinctPaths = Arrays.stream(ViewType.values())
                .map(ViewType::getResourcePath)
                .distinct()
                .count();

        assertEquals(ViewType.values().length, distinctPaths);
    }

    @Test
    void ingredientSearchViewDefinesAVisibleEmptyResultState() throws Exception {
        try (InputStream input = FxmlResourceTest.class.getResourceAsStream(
                "/de/mealdeal/ui/search-view.fxml")) {
            assertNotNull(input);
            String fxml = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String css = readResource("/de/mealdeal/ui/styles.css");

            assertTrue(fxml.contains("fx:id=\"emptyState\""));
            assertTrue(fxml.contains("Keine passenden Gerichte gefunden"));
            assertTrue(fxml.contains("fx:id=\"tasteAndMode\""));
            assertTrue(fxml.contains("fx:id=\"tasteOrMode\""));
            assertTrue(fxml.contains("fx:id=\"tasteRankingMode\""));
            assertEquals(3, fxml.split("toggleGroup=\"\\$tasteModeGroup\"", -1).length - 1);
            assertTrue(fxml.contains("selected=\"true\" toggleGroup=\"$tasteModeGroup\""));
            assertTrue(fxml.contains("fx:id=\"searchOptionsPane\""));
            assertTrue(fxml.contains("fx:id=\"ingredientSelectionPane\""));
            assertTrue(fxml.contains("fx:id=\"tasteSelectionPane\""));
            assertEquals(3, fxml.split("collapsible=\"true\" expanded=\"false\"", -1).length - 1);
            assertTrue(fxml.contains("text=\"Filter &amp; Suchoptionen\""));
            assertTrue(fxml.contains("collapsible=\"true\" expanded=\"false\""));
            assertTrue(fxml.contains("text=\"Geschmacksfilter\""));
            assertTrue(fxml.contains("gilt nur für ausgewählte Geschmacksrichtungen"));
            assertTrue(fxml.indexOf("fx:id=\"searchOptionsPane\"")
                    < fxml.indexOf("fx:id=\"tasteAndMode\""));
            assertTrue(fxml.indexOf("fx:id=\"tasteRankingMode\"")
                    < fxml.indexOf("<Button text=\"Gericht finden\""));
            assertTrue(fxml.indexOf("<Button text=\"Gericht finden\"")
                    < fxml.indexOf("text=\"Alle Filter zurücksetzen\""));
            assertTrue(fxml.contains("<HBox alignment=\"CENTER_RIGHT\" spacing=\"12.0\">"));
            assertFalse(fxml.contains("styleClass=\"search-actions\""));
            assertFalse(css.contains(".search-actions"));
            assertTrue(fxml.contains("onAction=\"#resetFilters\""));
            assertTrue(fxml.contains("styleClass=\"secondary-button, search-reset-button\""));
            assertTrue(fxml.contains("onAction=\"#search\""));
            assertTrue(fxml.contains("fx:id=\"resultsContainer\" alignment=\"TOP_LEFT\""));
            assertTrue(fxml.contains("fx:id=\"availableIngredientsContainer\" spacing=\"10.0\""));
            assertTrue(fxml.contains("VBox.vgrow=\"NEVER\""));
            assertTrue(css.contains(".search-result-item {"));
            assertTrue(css.contains("-fx-max-height: -1"));
            assertTrue(css.contains(".expandable-card > .title"));
            assertTrue(css.contains(".ingredient-category-pane > .title"));
            assertTrue(css.contains(".ingredient-category-options .ingredient-option"));
            assertTrue(css.contains(".ingredient-category-options .ingredient-option:hover"));
            assertTrue(css.contains(".selected-ingredient-chip"));
        }
    }

    @Test
    void recipeDetailUsesResponsiveContentAndOptionalPreparationState() throws Exception {
        try (InputStream input = FxmlResourceTest.class.getResourceAsStream(
                "/de/mealdeal/ui/recipe-detail-view.fxml")) {
            assertNotNull(input);
            String fxml = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String css = readResource("/de/mealdeal/ui/styles.css");

            assertTrue(fxml.contains("fitToWidth=\"true\""));
            assertTrue(fxml.contains("page-container, page-container-detail"));
            assertTrue(fxml.contains("Noch keine Zubereitung hinterlegt."));
            assertTrue(fxml.contains("fx:id=\"timeSection\""));
            assertTrue(fxml.contains("fx:id=\"detailMetaGrid\""));
            assertTrue(fxml.contains("fx:id=\"detailSectionsGrid\""));
            assertEquals(2, fxml.split("<ColumnConstraints percentWidth=\"45.0\"/>", -1).length - 1);
            assertEquals(2, fxml.split("<ColumnConstraints percentWidth=\"55.0\"/>", -1).length - 1);
            assertTrue(fxml.contains("fx:id=\"nutritionSection\""));
            assertTrue(fxml.contains("fx:id=\"nutritionContainer\""));
            assertTrue(fxml.contains("fx:id=\"dishTypeLabel\""));
            assertTrue(fxml.contains("fx:id=\"tastesContainer\""));
            assertTrue(fxml.contains("styleClass=\"content-card, detail-section-card\""));
            assertTrue(fxml.contains("fx:id=\"preparationTimeValue\""));
            assertTrue(fxml.contains("fx:id=\"totalTimeValue\""));
            assertTrue(fxml.indexOf("text=\"Zurück\"") < fxml.indexOf("text=\"Bearbeiten\""));
            assertTrue(fxml.indexOf("text=\"Bearbeiten\"") < fxml.indexOf("text=\"Löschen\""));
            assertTrue(css.contains(".page-container-detail"));
            assertTrue(css.contains("-fx-max-width: 1760px"));
            String responsive = readResource("/de/mealdeal/ui/styles/responsive.css");
            assertTrue(responsive.contains(
                    ".root-shell.viewport-compact .recipe-detail-grid"));
        }
    }

    @Test
    void homeUsesOneCentralActionForCombinedSearch() throws Exception {
        String fxml = readResource("/de/mealdeal/ui/home-view.fxml");
        String css = readResource("/de/mealdeal/ui/styles.css");

        assertTrue(fxml.contains("text=\"Gericht finden\""));
        assertEquals(1, fxml.split("onAction=\"#openSearch\"", -1).length - 1);
        assertFalse(fxml.contains("Nach Zutaten suchen"));
        assertFalse(fxml.contains("Nach Geschmack suchen"));
        assertTrue(fxml.contains("fx:id=\"todayPlanContent\""));
        assertTrue(fxml.contains("fx:id=\"todayEmptyState\""));
        assertTrue(fxml.contains("fx:id=\"weekOverviewContainer\""));
        assertTrue(fxml.contains("fx:id=\"weekErrorState\""));
        int weekPaneStart = fxml.indexOf("<TitledPane text=\"Wochenplan\"");
        int weekPaneEnd = fxml.indexOf("</TitledPane>", weekPaneStart);
        assertTrue(weekPaneStart >= 0);
        assertTrue(weekPaneEnd > weekPaneStart);
        assertTrue(fxml.indexOf("fx:id=\"weekOverviewContainer\"") > weekPaneStart);
        assertTrue(fxml.indexOf("fx:id=\"weekOverviewContainer\"") < weekPaneEnd);
        assertTrue(fxml.substring(weekPaneStart, weekPaneEnd).contains("expanded=\"false\""));
        assertTrue(fxml.substring(weekPaneStart, weekPaneEnd)
                .contains("styleClass=\"expandable-card, home-week-pane\""));
        int searchTitle = fxml.indexOf("text=\"Was möchtest du essen?\"");
        int recipesTitle = fxml.indexOf("text=\"Meine Gerichte\"");
        int todayTitle = fxml.indexOf("text=\"Heute\"");
        int weekTitle = fxml.indexOf("<TitledPane text=\"Wochenplan\"");
        assertTrue(searchTitle < recipesTitle);
        assertTrue(recipesTitle < todayTitle);
        assertTrue(todayTitle < weekTitle);
        assertEquals(3, fxml.split("styleClass=\"content-card-header\"", -1).length - 1);
        assertEquals(3, fxml.split("styleClass=\"content-card-header-title\"", -1).length - 1);
        assertEquals(3, fxml.split("styleClass=\"content-card-body\"", -1).length - 1);
        assertEquals(1, fxml.split("<TitledPane", -1).length - 1);
        assertFalse(fxml.contains("text=\"Heute\" styleClass=\"section-title\""));
        assertFalse(fxml.contains("text=\"Meine Gerichte\" styleClass=\"section-title\""));
        assertFalse(fxml.contains("styleClass=\"dashboard-section"));
        assertFalse(fxml.contains("styleClass=\"page-header\""));
        assertFalse(fxml.contains("styleClass=\"page-title\""));
        assertFalse(fxml.contains("styleClass=\"page-subtitle\""));
        assertTrue(fxml.contains("onAction=\"#openRecipes\""));
        assertTrue(fxml.contains("onAction=\"#openCreateRecipe\""));
        assertEquals(2, fxml.split("onAction=\"#openWeekPlan\"", -1).length - 1);
        assertTrue(css.contains(".home-plan-main-entry"));
        assertTrue(css.contains(".home-plan-side-entry"));
        assertTrue(css.contains(".home-week-day"));
        assertTrue(css.contains(".content-card-header-title {"));
        assertTrue(css.contains(".home-week-pane > .title {"));
        assertTrue(css.contains(".content-card-header {"));
        assertTrue(css.contains(".content-card-body {"));
        assertTrue(css.contains(".root-shell.viewport-wide .content-card-header-title"));
        assertTrue(css.contains(".root-shell.viewport-extra-wide .content-card-header-title"));
    }

    @Test
    void recipeFormDefinesOptionalTimeInputs() throws Exception {
        String fxml = readResource("/de/mealdeal/ui/create-recipe-view.fxml");

        assertTrue(fxml.contains("fx:id=\"preparationTimeField\""));
        assertTrue(fxml.contains("fx:id=\"cookingTimeField\""));
        assertTrue(fxml.contains("fx:id=\"bakingTimeField\""));
        assertTrue(fxml.contains("fx:id=\"restingTimeField\""));
        assertFalse(fxml.contains("totalTimeField"));
        assertTrue(fxml.contains("Zeitangaben (optional)"));
        assertTrue(fxml.contains("fx:id=\"preparationTimeUnitBox\""));
        assertTrue(fxml.contains("fx:id=\"cookingTimeUnitBox\""));
        assertTrue(fxml.contains("fx:id=\"bakingTimeUnitBox\""));
        assertTrue(fxml.contains("fx:id=\"restingTimeUnitBox\""));
        assertTrue(fxml.contains("fx:id=\"caloriesField\""));
        assertTrue(fxml.contains("fx:id=\"proteinField\""));
        assertTrue(fxml.contains("fx:id=\"carbohydratesField\""));
        assertTrue(fxml.contains("fx:id=\"fatField\""));
        assertTrue(fxml.contains("Nährwerte pro Portion"));
        assertTrue(fxml.contains("fx:id=\"dishTypeBox\""));
        assertTrue(fxml.contains("text=\"Gerichtstyp\""));
        assertTrue(fxml.contains("Zutat hinzufügen"));
        assertTrue(fxml.indexOf("fx:id=\"ingredientRowsContainer\"")
                < fxml.indexOf("text=\"Zutat hinzufügen\""));
        assertTrue(fxml.contains("Alternativen mit eigener Menge und Einheit"));
        assertTrue(fxml.indexOf("text=\"Allgemein\"") < fxml.indexOf("text=\"Zutaten\""));
        assertTrue(fxml.indexOf("text=\"Zutaten\"")
                < fxml.indexOf("text=\"Geschmacksrichtung\""));
        assertTrue(fxml.indexOf("text=\"Geschmacksrichtung\"")
                < fxml.indexOf("text=\"Nährwerte pro Portion\""));
        assertTrue(fxml.indexOf("text=\"Nährwerte pro Portion\"")
                < fxml.indexOf("text=\"Zubereitung\""));
        assertTrue(readResource("/de/mealdeal/ui/styles.css")
                .contains(".ingredient-group-form"));
    }

    @Test
    void recipesViewDefinesIndependentDishTypeGroups() throws Exception {
        String fxml = readResource("/de/mealdeal/ui/recipes-view.fxml");
        String css = readResource("/de/mealdeal/ui/styles.css");

        assertTrue(fxml.contains("fx:id=\"mainRecipesPane\""));
        assertTrue(fxml.contains("fx:id=\"sideRecipesPane\""));
        assertTrue(fxml.contains("fx:id=\"dessertRecipesPane\""));
        assertTrue(fxml.contains("fx:id=\"mainRecipesContainer\""));
        assertTrue(fxml.contains("fx:id=\"sideRecipesContainer\""));
        assertTrue(fxml.contains("fx:id=\"dessertRecipesContainer\""));
        assertFalse(fxml.contains("<Accordion"));
        assertTrue(css.contains(".recipe-group-pane > .title"));
        assertTrue(css.contains(".recipe-group-empty"));
    }

    @Test
    void mainViewDefinesPersistentSidebarThemeToggle() throws Exception {
        String fxml = readResource(MAIN_VIEW);

        assertTrue(fxml.contains("fx:id=\"rootShell\""));
        assertTrue(fxml.contains("fx:id=\"themeToggle\""));
        assertTrue(fxml.contains("fx:id=\"ingredientsButton\""));
        assertTrue(fxml.contains("onAction=\"#showIngredients\""));
        assertTrue(fxml.contains("onAction=\"#toggleTheme\""));
        assertTrue(fxml.contains("VBox.vgrow=\"ALWAYS\""));
        assertTrue(fxml.contains("maxWidth=\"1.7976931348623157E308\""));
    }

    @Test
    void stylesheetDefinesCentralWineRedLightAndDarkPalettes() throws Exception {
        String css = readResource("/de/mealdeal/ui/styles.css");

        assertTrue(css.contains("-md-accent: #6f1d35"));
        assertTrue(css.contains(".root-shell.theme-dark"));
        assertTrue(css.contains("-fx-font-size: 36px"));
        assertTrue(css.contains("-fx-min-height: 44px"));
        assertTrue(css.contains(".root-shell.viewport-wide .page-container"));
        assertTrue(css.contains(".root-shell.viewport-extra-wide .page-container"));
        assertTrue(css.contains("-fx-max-width: 1640px"));
        assertTrue(css.contains(".content-card"));
        assertTrue(css.contains(".sub-card"));
        assertTrue(css.contains(".expandable-card > .title"));
        assertFalse(css.contains("\n.card {"));
        assertFalse(css.contains(".expandable-pane"));
        assertTrue(css.contains("-fx-background-color: -md-accent"));
        assertTrue(css.contains(".expandable-card > .title > .arrow-button .arrow"));
    }

    @Test
    void stylesheetEntryPointLoadsAcyclicModulesInRequiredOrder() throws Exception {
        CssResourceGraph graph = CssResourceGraph.load(STYLESHEET);

        assertEquals(STYLESHEET_MODULES, graph.directImports());
        assertEquals(STYLESHEET_MODULES.size() + 1, graph.resourcePaths().size());
        assertTrue(graph.resourcePaths().contains(STYLESHEET));
        assertTrue(graph.resourcePaths().containsAll(STYLESHEET_MODULES));
        assertEquals("/de/mealdeal/ui/styles/responsive.css",
                graph.directImports().getLast());
        assertTrue(graph.entryCss().lines()
                .filter(line -> !line.isBlank())
                .allMatch(line -> line.startsWith("@import url(\"styles/")
                        && line.endsWith(".css\");")));
    }

    @Test
    void stylesheetEntryPointAndRelativeImportsParseWithJavaFx() throws Exception {
        URL stylesheetUrl = FxmlResourceTest.class.getResource(STYLESHEET);
        assertNotNull(stylesheetUrl);
        CssParser.errorsProperty().clear();

        assertNotNull(new CssParser().parse(stylesheetUrl));
        assertTrue(CssParser.errorsProperty().isEmpty(),
                () -> "JavaFX CSS errors: " + CssParser.errorsProperty());
    }

    @Test
    void stylesheetModulesKeepSharedControlsAndFeatureRulesSeparated() throws Exception {
        assertTrue(readResource("/de/mealdeal/ui/styles/tokens.css")
                .contains(".root-shell.theme-dark"));
        String controls = readResource("/de/mealdeal/ui/styles/controls.css");
        assertTrue(controls.contains(".combo-box > .list-cell"));
        assertTrue(controls.contains(".spinner > .increment-arrow-button"));
        assertTrue(controls.contains(".scroll-bar .thumb"));
        String components = readResource("/de/mealdeal/ui/styles/components.css");
        assertTrue(components.contains(".content-card"));
        assertTrue(components.contains(".expandable-card > .title"));
        assertTrue(readResource("/de/mealdeal/ui/styles/recipes.css")
                .contains(".recipe-group-pane"));
        assertTrue(readResource("/de/mealdeal/ui/styles/search.css")
                .contains(".search-options-pane"));
        assertTrue(readResource("/de/mealdeal/ui/styles/planning.css")
                .contains(".meal-plan-day-card"));
        String stock = readResource("/de/mealdeal/ui/styles/stock.css");
        assertTrue(stock.contains(".inventory-row"));
        assertTrue(stock.contains(".shopping-list-row"));
        assertTrue(readResource("/de/mealdeal/ui/styles/responsive.css")
                .contains(".root-shell.viewport-extra-wide"));
    }

    @Test
    void stylesheetModulesDoNotDuplicateSelectorsOrProperties() throws Exception {
        Map<String, String> selectorOwners = new HashMap<>();
        Map<String, Set<String>> selectorProperties = new HashMap<>();
        for (String module : STYLESHEET_MODULES) {
            Matcher rules = CSS_RULE.matcher(
                    CSS_COMMENT.matcher(readResource(module)).replaceAll(""));
            while (rules.find()) {
                String selector = rules.group(1).strip().replaceAll("\\s+", " ");
                String previousOwner = selectorOwners.putIfAbsent(selector, module);
                assertTrue(previousOwner == null || previousOwner.equals(module),
                        () -> selector + " occurs in " + previousOwner + " and " + module);

                Set<String> properties = selectorProperties.computeIfAbsent(
                        selector, ignored -> new HashSet<>());
                for (String declaration : rules.group(2).split(";")) {
                    int separator = declaration.indexOf(':');
                    if (separator < 0) {
                        continue;
                    }
                    String property = declaration.substring(0, separator).strip();
                    assertTrue(properties.add(property),
                            () -> property + " is duplicated in " + selector);
                }
            }
        }
    }

    @Test
    void weekPlanViewDefinesFunctionalCurrentWeekStates() throws Exception {
        String fxml = readResource("/de/mealdeal/ui/week-plan-view.fxml");
        String css = readResource("/de/mealdeal/ui/styles.css");

        assertTrue(fxml.contains("fx:controller=\"de.mealdeal.ui.controller.WeekPlanController\""));
        assertTrue(fxml.contains("fx:id=\"weekRangeLabel\""));
        assertTrue(fxml.contains("fx:id=\"dayCardsContainer\""));
        assertTrue(fxml.contains("spacing=\"8.0\" styleClass=\"meal-plan-days\""));
        assertTrue(fxml.contains("Änderungen speichern"));
        assertTrue(fxml.contains("StackPane.alignment=\"BOTTOM_RIGHT\""));
        assertTrue(fxml.contains("styleClass=\"primary-button, meal-plan-save-button\""));
        assertTrue(fxml.indexOf("</ScrollPane>") < fxml.indexOf("fx:id=\"saveChangesButton\""));
        assertTrue(fxml.contains("right=\"20.0\" bottom=\"20.0\""));
        assertFalse(fxml.contains("fx:id=\"weekSaveBar\""));
        assertFalse(fxml.contains("meal-plan-save-overlay"));
        assertTrue(fxml.contains("<Region minHeight=\"110.0\"/>"));
        assertTrue(fxml.contains("fx:id=\"errorState\""));
        assertTrue(fxml.contains("onAction=\"#refresh\""));
        assertTrue(css.contains(".meal-plan-save-button"));
        assertTrue(css.contains(".meal-plan-save-message"));
        assertTrue(css.contains(".meal-plan-day-today"));
        assertTrue(css.contains(".meal-plan-controls"));
        assertTrue(css.contains(".meal-plan-role-section"));
        assertTrue(css.contains(".meal-plan-main-row"));
        assertTrue(css.contains(".meal-plan-side-row"));
        assertTrue(css.contains(".meal-plan-dessert-row"));
        assertTrue(css.contains(".meal-plan-role-header"));
        assertTrue(css.contains(".meal-plan-view-row"));
        assertTrue(css.contains(".meal-plan-view-actions"));
        assertTrue(css.contains(".meal-plan-serving-text"));
        assertTrue(css.contains(".meal-plan-day-edit-button"));
        assertTrue(css.contains(".meal-plan-day-heading-row"));
        assertTrue(css.contains(".meal-plan-control-field"));
        assertTrue(Pattern.compile(
                "\\.meal-plan-main-row,\\s*\\.meal-plan-side-row,\\s*"
                        + "\\.meal-plan-dessert-row\\s*\\{")
                .matcher(css).find());
        assertTrue(css.contains(".combo-box > .list-cell"));
        assertTrue(css.contains(".spinner > .text-field"));
        assertTrue(Pattern.compile(
                "\\.combo-box > \\.list-cell,\\s*"
                        + "\\.combo-box-base > \\.text-field,\\s*"
                        + "\\.spinner > \\.text-field\\s*\\{[^}]*"
                        + "-fx-background-color: transparent;[^}]*"
                        + "-fx-border-color: transparent;",
                Pattern.DOTALL).matcher(css).find());
        assertTrue(css.contains(".combo-box-base > .arrow-button"));
        assertTrue(css.contains(".spinner > .increment-arrow-button"));
        assertTrue(css.contains(".searchable-combo-box"));
        assertTrue(css.contains(".meal-plan-day-card.meal-plan-day-today > .title"));
        assertTrue(css.contains(".meal-plan-day-summary"));
        assertTrue(Pattern.compile(
                "\\.meal-plan-day-name,\\s*\\.meal-plan-day-summary\\s*\\{")
                .matcher(css).find());
        assertTrue(css.contains("-fx-font-size: 23px"));
        assertFalse(css.contains("viewport-wide .meal-plan-days"));
        assertFalse(css.contains("viewport-extra-wide .meal-plan-days"));
        String responsiveCss = readResource("/de/mealdeal/ui/styles/responsive.css");
        assertTrue(responsiveCss.contains(
                ".root-shell.viewport-compact .meal-plan-day-content"));
        assertTrue(responsiveCss.contains(
                ".root-shell.viewport-compact .meal-plan-role-section"));
        assertTrue(css.contains(".expandable-card > *.content"));
        assertTrue(css.contains(".home-plan-dessert-entry"));
    }

    @Test
    void shoppingListViewDefinesTodayWeekAndResultStates() throws Exception {
        String fxml = readResource("/de/mealdeal/ui/shopping-view.fxml");
        String css = readResource("/de/mealdeal/ui/styles.css");

        assertTrue(fxml.contains(
                "fx:controller=\"de.mealdeal.ui.controller.ShoppingListController\""));
        assertTrue(fxml.contains("fx:id=\"todayMode\""));
        assertTrue(fxml.contains("onAction=\"#showToday\""));
        assertTrue(fxml.contains("fx:id=\"weekMode\""));
        assertTrue(fxml.contains("onAction=\"#showCurrentWeek\""));
        assertTrue(fxml.contains("fx:id=\"withoutInventoryMode\""));
        assertTrue(fxml.contains("onAction=\"#showWithoutInventory\""));
        assertTrue(fxml.contains("fx:id=\"withInventoryMode\""));
        assertTrue(fxml.contains("onAction=\"#showWithInventory\""));
        assertTrue(fxml.contains("fx:id=\"withInventoryMode\" text=\"Mit Inventar\"\n                                      selected=\"true\""));
        assertTrue(fxml.contains("fx:id=\"itemsContainer\""));
        assertTrue(fxml.contains("fx:id=\"emptyState\""));
        assertTrue(fxml.contains("Für heute ist nichts einzukaufen."));
        assertTrue(fxml.contains("fx:id=\"errorState\""));
        assertTrue(css.contains(".shopping-mode-button:selected"));
        assertTrue(css.contains(".shopping-list-row"));
    }

    @Test
    void inventoryViewDefinesAddGroupedEditDeleteAndEmptyStates() throws Exception {
        String main = readResource(MAIN_VIEW);
        String fxml = readResource("/de/mealdeal/ui/inventory-view.fxml");
        String css = readResource("/de/mealdeal/ui/styles.css");

        assertTrue(main.contains("fx:id=\"inventoryButton\""));
        assertTrue(main.contains("onAction=\"#showInventory\""));
        assertTrue(fxml.contains("fx:controller=\"de.mealdeal.ui.controller.InventoryController\""));
        assertTrue(fxml.contains("fx:id=\"ingredientBox\""));
        assertTrue(fxml.contains("fx:id=\"quantityField\""));
        assertTrue(fxml.contains("fx:id=\"unitBox\""));
        assertTrue(fxml.contains("onAction=\"#addItem\""));
        assertTrue(fxml.contains("fx:id=\"categoryContainer\""));
        assertTrue(fxml.contains("fx:id=\"emptyState\""));
        assertFalse(fxml.contains("categoryManagementSection"));
        assertFalse(fxml.contains("ingredientManagementSection"));
        assertFalse(fxml.contains("Zutatenkategorien verwalten"));
        assertFalse(fxml.contains("Zentrale Zutaten bearbeiten"));
        assertTrue(css.contains(".inventory-category-card"));
        assertTrue(css.contains(".inventory-row"));
        assertTrue(css.contains(".inventory-category-management-row"));
        assertTrue(css.contains(".inventory-ingredient-management-row"));
    }

    @Test
    void inventoryViewReferencesExistingControllerFieldsAndActions() throws Exception {
        String fxml = readResource("/de/mealdeal/ui/inventory-view.fxml");
        assertControllerWiring(fxml, InventoryController.class);
    }

    @Test
    void ingredientsViewDefinesNavigationAndCompleteManagementWiring() throws Exception {
        String main = readResource(MAIN_VIEW);
        String fxml = readResource("/de/mealdeal/ui/ingredients-view.fxml");

        assertTrue(main.contains("fx:id=\"ingredientsButton\""));
        assertTrue(main.contains("text=\"Zutaten\""));
        assertTrue(main.contains("onAction=\"#showIngredients\""));
        assertTrue(fxml.contains(
                "fx:controller=\"de.mealdeal.ui.controller.IngredientsController\""));
        assertTrue(fxml.contains("fx:id=\"ingredientManagementSection\""));
        assertTrue(fxml.contains("fx:id=\"ingredientManagementContainer\""));
        assertTrue(fxml.contains("fx:id=\"categoryManagementSection\""));
        assertTrue(fxml.contains("fx:id=\"categoryNameField\""));
        assertTrue(fxml.contains("onAction=\"#addCategory\""));
        assertTrue(fxml.contains("fx:id=\"categoryManagementContainer\""));
        assertEquals(2, fxml.split("styleClass=\"expandable-card, ingredient-management-pane\"", -1).length - 1);
        String css = readResource("/de/mealdeal/ui/styles.css");
        assertTrue(css.contains(".ingredient-category-pane > .title"));
        assertTrue(css.contains(".ingredient-category-content"));
        assertFalse(css.contains(".inventory-ingredient-category-badge"));
        assertControllerWiring(fxml, IngredientsController.class);
    }

    private static void assertControllerWiring(String fxml, Class<?> controllerType) {
        var idMatcher = Pattern.compile("fx:id=\\\"([^\\\"]+)\\\"").matcher(fxml);
        while (idMatcher.find()) {
            String fieldName = idMatcher.group(1);
            assertDoesNotThrow(() -> controllerType.getDeclaredField(fieldName),
                    () -> "Missing " + controllerType.getSimpleName() + " field: " + fieldName);
        }
        var actionMatcher = Pattern.compile("onAction=\\\"#([^\\\"]+)\\\"").matcher(fxml);
        while (actionMatcher.find()) {
            String methodName = actionMatcher.group(1);
            assertDoesNotThrow(() -> controllerType.getDeclaredMethod(methodName),
                    () -> "Missing " + controllerType.getSimpleName() + " action: " + methodName);
        }
    }

    private static String readResource(String path) throws Exception {
        if (STYLESHEET.equals(path)) {
            return CssResourceGraph.load(path).combinedCss();
        }
        try (InputStream input = FxmlResourceTest.class.getResourceAsStream(path)) {
            assertNotNull(input, () -> "Missing resource: " + path);
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static Stream<String> allFxmlResources() {
        return Stream.concat(
                Stream.of(MAIN_VIEW),
                Arrays.stream(ViewType.values()).map(ViewType::getResourcePath));
    }
}
