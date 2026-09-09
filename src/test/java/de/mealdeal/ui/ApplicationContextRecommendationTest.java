package de.mealdeal.ui;

import de.mealdeal.ui.controller.RecommendationController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ApplicationContextRecommendationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void productionCompositionProvidesRecommendationController() {
        ApplicationContext context = new ApplicationContext(
                temporaryDirectory.resolve("mealdeal.db"));

        assertInstanceOf(RecommendationController.class,
                context.createController(RecommendationController.class));
    }
}
