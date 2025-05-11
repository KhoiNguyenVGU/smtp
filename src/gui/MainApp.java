package gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class MainApp extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/resources/LoginView.fxml"));
        primaryStage.setScene(new Scene(loader.load()));
        primaryStage.setTitle("Login - SMTP Email Sender");
        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/gui/resources/logo.png")));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}