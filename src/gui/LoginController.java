package gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.fxml.FXMLLoader;
import javafx.stage.Modality;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Base64;

public class LoginController {

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private void handleLogin() {
        String email = emailField.getText();
        String password = passwordField.getText();

        // Create and show the "Logging in..." popup
        Stage loggingInPopup = createLoggingInPopup();
        loggingInPopup.show();

        // Run the login process in a background thread
        new Thread(() -> {
            LoginResult result = validateCredentials(email, password);

            // Close the "Logging in..." popup on the JavaFX Application Thread
            Platform.runLater(loggingInPopup::close);

            Platform.runLater(() -> {
                if (result == LoginResult.SUCCESS) {
                    try {
                        // Load the main view
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/SMTPView.fxml"));
                        Stage stage = (Stage) emailField.getScene().getWindow();
                        stage.setScene(new Scene(loader.load()));

                        // Pass the email and password to the main controller
                        SMTPController controller = loader.getController();
                        controller.setUserCredentials(email, password);

                        stage.setTitle("SMTP Email Sender");
                        stage.show();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else if (result == LoginResult.NO_INTERNET) {
                    showErrorPopup("No Internet Connection", "Could not connect to the SMTP server. Please check your internet connection.");
                } else {
                    showErrorPopup("Invalid Credentials", "The provided email or app password is incorrect.");
                }
            });
        }).start();
    }

    private enum LoginResult {
        SUCCESS, INVALID_CREDENTIALS, NO_INTERNET
    }

    private LoginResult validateCredentials(String email, String password) {
        try {
            // Connect to the SMTP server
            String SMTP_SERVER = "smtp.gmail.com";
            int SMTP_PORT = 587;

            // Create a socket connection
            Socket socket = new Socket(SMTP_SERVER, SMTP_PORT);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            // Read server greeting
            System.out.println("Server: " + reader.readLine());

            // HELO command
            sendCommand(writer, reader, "HELO localhost");

            // STARTTLS command
            sendCommand(writer, reader, "STARTTLS");

            // Upgrade to TLS
            SSLContext sc = SSLUtils.disableCertificateValidation(); // Disable certificate validation for testing
            SSLSocketFactory sslSocketFactory = sc.getSocketFactory();
            SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(socket, SMTP_SERVER, SMTP_PORT, true);
            sslSocket.startHandshake();

            // Replace reader and writer with encrypted streams
            reader = new BufferedReader(new InputStreamReader(sslSocket.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(sslSocket.getOutputStream()));

            // AUTH LOGIN command
            sendCommand(writer, reader, "AUTH LOGIN");
            sendCommand(writer, reader, Base64.getEncoder().encodeToString(email.getBytes())); // Send encoded username
            String authResponse = sendCommand(writer, reader, Base64.getEncoder().encodeToString(password.getBytes())); // Send encoded password

            // Check for authentication errors
            if (authResponse.startsWith("535") || authResponse.startsWith("530")) {
                System.out.println("Invalid credentials: " + authResponse);
                return LoginResult.INVALID_CREDENTIALS;
            }

            // Close the connection
            sslSocket.close();
            socket.close();

            return LoginResult.SUCCESS;
        } catch (java.io.IOException e) {
            System.out.println("Network error: " + e.getMessage());
            return LoginResult.NO_INTERNET;
        } catch (Exception e) {
            System.out.println("Error during credential validation: " + e.getMessage());
            return LoginResult.INVALID_CREDENTIALS;
        }
    }

    private String sendCommand(BufferedWriter writer, BufferedReader reader, String command) throws Exception {
        writer.write(command + "\r\n");
        writer.flush();
        System.out.println("Client: " + command);
        String response = reader.readLine();
        System.out.println("Server: " + response);
        return response;
    }

    private Stage createLoggingInPopup() {
        Stage popupStage = new Stage();
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.setTitle("Logging In");

        // Create a Label for the "Logging in..." message
        javafx.scene.control.Label loggingInLabel = new javafx.scene.control.Label("Logging in... Please wait.");
        loggingInLabel.setStyle("-fx-font-size: 14; -fx-text-alignment: center;");

        // Arrange the components in a VBox
        VBox layout = new VBox(10);
        layout.setStyle("-fx-padding: 20; -fx-alignment: center;");
        layout.getChildren().add(loggingInLabel);

        // Set the scene and return the popup stage
        popupStage.setScene(new Scene(layout, 250, 100));
        return popupStage;
    }

    private void showErrorPopup(String title, String message) {
        Stage popupStage = new Stage();
        popupStage.setTitle(title);

        // Create a Label for the error message
        javafx.scene.control.Label errorLabel = new javafx.scene.control.Label(message);

        // Create the "Retry" button
        javafx.scene.control.Button retryButton = new javafx.scene.control.Button("Retry");
        retryButton.setOnAction(event -> {
            // Clear the input fields
            emailField.clear();
            passwordField.clear();

            // Close the popup
            popupStage.close();
        });

        // Create the "Quit" button
        javafx.scene.control.Button quitButton = new javafx.scene.control.Button("Quit");
        quitButton.setOnAction(event -> {
            // Close the popup and exit the application
            popupStage.close();
            javafx.application.Platform.exit();
        });

        // Arrange the components in a VBox
        VBox layout = new VBox(10);
        layout.setStyle("-fx-padding: 20; -fx-alignment: center;");
        layout.getChildren().addAll(errorLabel, retryButton, quitButton);

        // Set the scene and show the popup
        popupStage.setScene(new Scene(layout, 300, 150));
        popupStage.showAndWait();
    }
}