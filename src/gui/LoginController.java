package gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
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
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.URI;
import java.util.Base64;
import java.awt.Desktop;
import java.util.Properties;


public class LoginController {

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private CheckBox rememberMeCheckBox;
    
    @FXML
    private Hyperlink forgotPasswordLink;
    
    @FXML
    private void initialize() {
        // Load saved credentials if available
        loadSavedCredentials();
    }
    
    @FXML
    private void handleForgotPassword() {
        // Check if the email field has content
        String email = emailField.getText().trim();
        
        if (email.isEmpty()) {
            showInfoPopup("Email Required", "Please enter your email address first.");
            return;
        }
        
        try {
            // Open the Google App Password page
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI("https://myaccount.google.com/apppasswords"));
                
                // Show instructions popup
                showAppPasswordInstructions();
            } else {
                showInfoPopup("Browser Unavailable", 
                    "Could not open browser automatically. Please visit https://myaccount.google.com/apppasswords");
            }
        } catch (Exception e) {
            e.printStackTrace();
            showInfoPopup("Error", 
                "Could not open browser. Please visit https://myaccount.google.com/apppasswords manually.");
        }
    }

    @FXML
    private void handleLogin() {
        String email = emailField.getText();
        String password = passwordField.getText();

        // Create and show the "Logging in..." popup
        Stage loggingInPopup = createLoggingInPopup();
        loggingInPopup.show();

        // Run the login process in a background thread
        new Thread(() -> {
            boolean isValid = validateCredentials(email, password);

            // Close the "Logging in..." popup on the JavaFX Application Thread
            Platform.runLater(loggingInPopup::close);

            if (isValid) {
                // Save credentials if "Remember Me" is selected
                if (rememberMeCheckBox.isSelected()) {
                    saveCredentials(email, password, true);
                } else {
                    clearSavedCredentials();
                }

                Platform.runLater(() -> {
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
                });
            } else {
                // Show an error popup for invalid credentials
                Platform.runLater(() -> showErrorPopup("Invalid Credentials", "The provided email or app password is incorrect."));
            }
        }).start();
    }

    private boolean validateCredentials(String email, String password) {
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
                return false; // Invalid credentials
            }

            // Close the connection
            sendCommand(writer, reader, "QUIT");
            sslSocket.close();
            socket.close();

            return true; // Valid credentials
        } catch (Exception e) {
            System.out.println("Error during credential validation: " + e.getMessage());
            return false; // Invalid credentials
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
    
    private void showInfoPopup(String title, String message) {
        Stage popupStage = new Stage();
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.setTitle(title);

        // Create a Label for the message
        javafx.scene.control.Label messageLabel = new javafx.scene.control.Label(message);
        messageLabel.setWrapText(true);

        // Create the "OK" button
        javafx.scene.control.Button okButton = new javafx.scene.control.Button("OK");
        okButton.setOnAction(event -> popupStage.close());

        // Arrange the components in a VBox
        VBox layout = new VBox(10);
        layout.setStyle("-fx-padding: 20; -fx-alignment: center;");
        layout.getChildren().addAll(messageLabel, okButton);

        // Set the scene and show the popup
        popupStage.setScene(new Scene(layout, 300, 150));
        popupStage.showAndWait();
    }
    
    private void showAppPasswordInstructions() {
        Stage popupStage = new Stage();
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.setTitle("App Password Instructions");

        // Create a Label for the instructions
        javafx.scene.control.Label instructionsLabel = new javafx.scene.control.Label(
            "To generate an App Password for Gmail:\n\n" +
            "1. Sign in to your Google Account\n" +
            "2. Select 'App passwords' (you may need to enable 2-Step Verification first)\n" +
            "3. At the bottom, choose 'Select app' and pick 'Mail'\n" +
            "4. Choose 'Select device' and pick 'Other'\n" +
            "5. Enter 'SMTP Email Sender' and click 'Generate'\n" +
            "6. Use the 16-character password Google provides in the App Password field\n" +
            "7. Click 'Done'"
        );
        instructionsLabel.setWrapText(true);

        // Create the "OK" button
        javafx.scene.control.Button okButton = new javafx.scene.control.Button("OK");
        okButton.setOnAction(event -> popupStage.close());

        // Arrange the components in a VBox
        VBox layout = new VBox(10);
        layout.setStyle("-fx-padding: 20; -fx-alignment: center;");
        layout.getChildren().addAll(instructionsLabel, okButton);

        // Set the scene and show the popup
        popupStage.setScene(new Scene(layout, 400, 300));
        popupStage.showAndWait();
    }

    private final String CONFIG_PATH = System.getProperty("user.home") + "/.smtp_login.env";

    private void saveCredentials(String email, String password, boolean remember) {
        Properties props = new Properties();
        props.setProperty("EMAIL", email);
        props.setProperty("PASSWORD", password); // You can Base64-encode if needed
        props.setProperty("REMEMBER", String.valueOf(remember));

        try (FileWriter writer = new FileWriter(CONFIG_PATH)) {
            props.store(writer, "SMTP Login Credentials");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void loadSavedCredentials() {
        File file = new File(CONFIG_PATH);
        if (file.exists()) {
            Properties props = new Properties();
            try (FileReader reader = new FileReader(CONFIG_PATH)) {
                props.load(reader);
                
                String email = props.getProperty("EMAIL", "");
                String password = props.getProperty("PASSWORD", "");
                boolean remember = Boolean.parseBoolean(props.getProperty("REMEMBER", "false"));
                
                emailField.setText(email);
                passwordField.setText(password);
                rememberMeCheckBox.setSelected(remember);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    private void clearSavedCredentials() {
        File file = new File(CONFIG_PATH);
        if (file.exists()) {
            file.delete();
        }
    }
}