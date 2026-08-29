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
        }
    }

    private static Stream<String> allFxmlResources() {
        return Stream.concat(
                Stream.of(MAIN_VIEW),
                Arrays.stream(ViewType.values()).map(ViewType::getResourcePath));
    }
}
