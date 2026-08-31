package de.mealdeal.ui;

import de.mealdeal.ui.navigation.ViewType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

            assertTrue(fxml.contains("fx:id=\"emptyState\""));
            assertTrue(fxml.contains("Keine passenden Gerichte gefunden"));
            assertTrue(fxml.contains("fx:id=\"tasteAndMode\""));
            assertTrue(fxml.contains("fx:id=\"tasteOrMode\""));
            assertTrue(fxml.contains("fx:id=\"tasteRankingMode\""));
            assertTrue(fxml.contains("onAction=\"#resetFilters\""));
            assertTrue(fxml.contains("onAction=\"#search\""));
            assertTrue(fxml.contains("fx:id=\"resultsContainer\" alignment=\"TOP_LEFT\""));
            assertTrue(fxml.contains("VBox.vgrow=\"NEVER\""));
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
            assertTrue(css.contains(".page-container-detail"));
            assertTrue(css.contains("-fx-max-width: 1760px"));
        }
    }

    @Test
    void homeUsesOneCentralActionForCombinedSearch() throws Exception {
        String fxml = readResource("/de/mealdeal/ui/home-view.fxml");

        assertTrue(fxml.contains("text=\"Gericht finden\""));
        assertEquals(1, fxml.split("onAction=\"#openSearch\"", -1).length - 1);
        assertFalse(fxml.contains("Nach Zutaten suchen"));
        assertFalse(fxml.contains("Nach Geschmack suchen"));
    }

    @Test
    void recipeFormDefinesOptionalTimeInputs() throws Exception {
        String fxml = readResource("/de/mealdeal/ui/create-recipe-view.fxml");

        assertTrue(fxml.contains("fx:id=\"preparationTimeField\""));
        assertTrue(fxml.contains("fx:id=\"cookingTimeField\""));
        assertFalse(fxml.contains("totalTimeField"));
        assertTrue(fxml.contains("Zeitangaben (optional, in Minuten)"));
        assertTrue(fxml.contains("fx:id=\"caloriesField\""));
        assertTrue(fxml.contains("fx:id=\"proteinField\""));
        assertTrue(fxml.contains("fx:id=\"carbohydratesField\""));
        assertTrue(fxml.contains("fx:id=\"fatField\""));
        assertTrue(fxml.contains("Nährwerte pro Portion"));
    }

    @Test
    void mainViewDefinesPersistentSidebarThemeToggle() throws Exception {
        String fxml = readResource(MAIN_VIEW);

        assertTrue(fxml.contains("fx:id=\"rootShell\""));
        assertTrue(fxml.contains("fx:id=\"themeToggle\""));
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
    }

    @Test
    void weekPlanViewDefinesFunctionalCurrentWeekStates() throws Exception {
        String fxml = readResource("/de/mealdeal/ui/week-plan-view.fxml");
        String css = readResource("/de/mealdeal/ui/styles.css");

        assertTrue(fxml.contains("fx:controller=\"de.mealdeal.ui.controller.WeekPlanController\""));
        assertTrue(fxml.contains("fx:id=\"weekRangeLabel\""));
        assertTrue(fxml.contains("fx:id=\"dayCardsContainer\""));
        assertTrue(fxml.contains("fx:id=\"errorState\""));
        assertTrue(fxml.contains("onAction=\"#refresh\""));
        assertTrue(css.contains(".meal-plan-day-today"));
        assertTrue(css.contains(".meal-plan-controls"));
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
        assertTrue(fxml.contains("fx:id=\"itemsContainer\""));
        assertTrue(fxml.contains("fx:id=\"emptyState\""));
        assertTrue(fxml.contains("Für heute ist nichts einzukaufen."));
        assertTrue(fxml.contains("fx:id=\"errorState\""));
        assertTrue(css.contains(".shopping-mode-button:selected"));
        assertTrue(css.contains(".shopping-list-row"));
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
