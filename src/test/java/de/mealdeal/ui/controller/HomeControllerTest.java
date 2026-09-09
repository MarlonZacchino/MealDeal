package de.mealdeal.ui.controller;

import de.mealdeal.ui.navigation.ViewType;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HomeControllerTest {

    @Test
    void centralActionNavigatesToRecommendation() {
        AtomicReference<ViewType> destination = new AtomicReference<>();
        HomeController controller = new HomeController(destination::set);

        controller.openRecommendation();

        assertEquals(ViewType.RECOMMENDATION, destination.get());
    }
}
