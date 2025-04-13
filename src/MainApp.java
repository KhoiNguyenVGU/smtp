// filepath: c:\Users\Khoi Nguyen\OneDrive - student.vgu.edu.vn\Desktop\VGU\Academic Year 2\Computer Networks 2\Project\smtp\src\MainApp.java
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("SMTPView.fxml")); // Adjust path if needed
        primaryStage.setScene(new Scene(loader.load()));
        primaryStage.setTitle("SMTP Email Sender");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
