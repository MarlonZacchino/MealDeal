package de.mealdeal.ui;

import de.mealdeal.ui.navigation.ViewType;
import de.mealdeal.ui.controller.InventoryController;
import de.mealdeal.ui.controller.IngredientsController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxmlResourceTest {

    private static final String MAIN_VIEW = "/de/mealdeal/ui/main-view.fxml";

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
                    < fxml.indexOf("text=\"Gericht finden\""));
            assertTrue(fxml.contains("onAction=\"#resetFilters\""));
            assertTrue(fxml.contains("styleClass=\"secondary-button, search-reset-button\""));
            assertTrue(fxml.contains("onAction=\"#search\""));
            assertTrue(fxml.contains("fx:id=\"resultsContainer\" alignment=\"TOP_LEFT\""));
            assertTrue(fxml.contains("VBox.vgrow=\"NEVER\""));
            assertTrue(css.contains(".search-result-item {"));
            assertTrue(css.contains("-fx-max-height: -1"));
            assertTrue(css.contains(".search-options-pane > .title"));
            assertTrue(css.contains(".search-options-pane:expanded > .title"));
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
            assertTrue(fxml.contains("fx:id=\"timesContainer\""));
            assertTrue(fxml.contains("fx:id=\"nutritionSection\""));
            assertTrue(fxml.contains("fx:id=\"nutritionContainer\""));
            assertTrue(fxml.contains("fx:id=\"dishTypeLabel\""));
            assertTrue(css.contains(".page-container-detail"));
            assertTrue(css.contains("-fx-max-width: 1760px"));
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
                .contains("styleClass=\"expandable-pane, home-week-pane\""));
        assertFalse(fxml.contains("styleClass=\"page-header\""));
        assertFalse(fxml.contains("styleClass=\"page-title\""));
        assertFalse(fxml.contains("styleClass=\"page-subtitle\""));
        assertTrue(fxml.indexOf("text=\"Was möchtest du essen?\"")
                < fxml.indexOf("text=\"Heute\""));
        assertTrue(css.contains(".home-plan-main-entry"));
        assertTrue(css.contains(".home-plan-side-entry"));
        assertTrue(css.contains(".home-week-day"));
    }

    @Test
    void recipeFormDefinesOptionalTimeInputs() throws Exception {
        String fxml = readResource("/de/mealdeal/ui/create-recipe-view.fxml");

        assertTrue(fxml.contains("fx:id=\"preparationTimeField\""));
        assertTrue(fxml.contains("fx:id=\"cookingTimeField\""));
        assertTrue(fxml.contains("fx:id=\"bakingTimeField\""));
        assertFalse(fxml.contains("totalTimeField"));
        assertTrue(fxml.contains("Zeitangaben (optional, in Minuten)"));
        assertTrue(fxml.contains("fx:id=\"caloriesField\""));
        assertTrue(fxml.contains("fx:id=\"proteinField\""));
        assertTrue(fxml.contains("fx:id=\"carbohydratesField\""));
        assertTrue(fxml.contains("fx:id=\"fatField\""));
        assertTrue(fxml.contains("Nährwerte pro Portion"));
        assertTrue(fxml.contains("fx:id=\"dishTypeBox\""));
        assertTrue(fxml.contains("text=\"Gerichtstyp\""));
        assertTrue(fxml.contains("Zutatengruppe hinzufügen"));
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
        assertTrue(css.contains(".expandable-pane > .title"));
        assertTrue(css.contains("-fx-background-color: -md-accent"));
        assertTrue(css.contains(".expandable-pane > .title > .arrow-button .arrow"));
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
        assertTrue(css.contains(".meal-plan-side-row"));
        assertTrue(css.contains(".meal-plan-dessert-row"));
        assertTrue(css.contains(".meal-plan-day-card > .title"));
        assertTrue(css.contains(".meal-plan-day-summary"));
        assertFalse(css.contains("viewport-wide .meal-plan-days"));
        assertFalse(css.contains("viewport-extra-wide .meal-plan-days"));
        assertTrue(css.contains(".home-week-pane > *.content"));
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
        assertEquals(2, fxml.split("styleClass=\"expandable-pane, ingredient-management-pane\"", -1).length - 1);
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
