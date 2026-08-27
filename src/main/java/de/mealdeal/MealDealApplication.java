package de.mealdeal;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Entry point for the MealDeal desktop application.
 */
public class MealDealApplication extends Application {

    private static final String APPLICATION_TITLE = "MealDeal";
    private static final double INITIAL_WINDOW_WIDTH = 800;
    private static final double INITIAL_WINDOW_HEIGHT = 600;

    /**
     * Starts JavaFX and displays the initial empty application window.
     *
     * @param primaryStage the main window supplied by JavaFX
     */
    @Override
    public void start(Stage primaryStage) {
        Scene scene = new Scene(new StackPane(), INITIAL_WINDOW_WIDTH, INITIAL_WINDOW_HEIGHT);

        primaryStage.setTitle(APPLICATION_TITLE);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * Launches the application.
     *
     * @param arguments command-line arguments passed to JavaFX
     */
    public static void main(String[] arguments) {
        launch(arguments);
    }
}
