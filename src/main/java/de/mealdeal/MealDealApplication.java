package de.mealdeal;

import de.mealdeal.ui.ApplicationContext;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Entry point for the MealDeal desktop application.
 */
public class MealDealApplication extends Application {

    private static final String APPLICATION_TITLE = "MealDeal";
    private static final double INITIAL_WINDOW_WIDTH = 1180;
    private static final double INITIAL_WINDOW_HEIGHT = 760;
    private static final double MINIMUM_WINDOW_WIDTH = 960;
    private static final double MINIMUM_WINDOW_HEIGHT = 640;

    /**
     * Starts JavaFX and displays the application shell in the primary window.
     *
     * @param primaryStage the main window supplied by JavaFX
     */
    @Override
    public void start(Stage primaryStage) {
        ApplicationContext applicationContext = new ApplicationContext();
        Parent mainView = applicationContext.loadMainView();
        Scene scene = new Scene(mainView, INITIAL_WINDOW_WIDTH, INITIAL_WINDOW_HEIGHT);

        primaryStage.setTitle(APPLICATION_TITLE);
        primaryStage.setMinWidth(MINIMUM_WINDOW_WIDTH);
        primaryStage.setMinHeight(MINIMUM_WINDOW_HEIGHT);
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
