package gui;

import javafx.animation.RotateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.fxml.FXMLLoader;
import javafx.stage.Modality;
import javafx.util.Duration;

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

        // Add listener to clear credentials when "Remember Me" is unchecked
        rememberMeCheckBox.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
            if (!isNowSelected) {
                clearSavedCredentials();
            }
        });
    }

    @FXML
    private void handleHowToAppPassword() {
        try {
            // Open the Google App Password creation page
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI("https://myaccount.google.com/apppasswords"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            showInfoPopup("Error", "Could not open browser. Please visit the app password page manually.");
        }
        // Show instructions popup
        showAppPasswordInstructions();
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
                showResetAppPasswordInstructions();
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
    private Button loginButton;

    @FXML
    private void handleLogin() {
        String email = emailField.getText();
        String password = passwordField.getText();

        // Check for internet connection before proceeding
        if (!isInternetAvailable()) {
            showErrorPopup("No Internet Connection", "Please check your internet connection and try again.");
            return;
        }

        // Check for internet connection before proceeding
        if (!isInternetAvailable()) {
            showErrorPopup("No Internet Connection", "Please check your internet connection and try again.");
            return;
        }

        // Create a ProgressIndicator to replace the button text
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setProgress(-1.0); // Indeterminate mode
        spinner.setMaxSize(20, 20); // Set the size of the spinner
        spinner.setStyle("-fx-progress-color:rgb(255, 255, 255);");

        // Show the spinner and disable the login button
        Platform.runLater(() -> {
            loginButton.setGraphic(spinner); // Add the spinner as the button's graphic
            loginButton.setText(""); // Clear the button text
            loginButton.setDisable(true); // Disable the button
        });

        // Run the login process in a background thread
        new Thread(() -> {
            boolean isValid = validateCredentials(email, password);

            // Restore the button text and re-enable the button on the JavaFX Application Thread
            Platform.runLater(() -> {
                loginButton.setGraphic(null); // Remove the spinner
                loginButton.setText("Login"); // Restore the button text
                loginButton.setDisable(false); // Re-enable the button
            });

            if (isValid) {
                // Save credentials if "Remember Me" is selected
                if (rememberMeCheckBox.isSelected()) {
                    saveCredentials(email, password, true);
                } else {
                    clearSavedCredentials();
                }

                Platform.runLater(() -> {
                    try {
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/resources/SMTPView.fxml"));
                        Scene smtpScene = new Scene(loader.load());
                        SMTPController smtpController = loader.getController();
                        smtpController.setUserCredentials(email, password); // Pass credentials here

                        Stage stage = (Stage) emailField.getScene().getWindow();
                        stage.setScene(smtpScene);
                        stage.setTitle("SMTP Email Sender");
                        stage.show();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            } else {
                Platform.runLater(() -> showErrorPopup("Invalid Credentials", "The provided email or app password is incorrect."));
            }
        }).start();
    }

    private RotateTransition startButtonSpin() {
        RotateTransition rotateTransition = new RotateTransition(Duration.seconds(1), loginButton);
        rotateTransition.setByAngle(360); // Rotate 360 degrees
        rotateTransition.setCycleCount(RotateTransition.INDEFINITE); // Keep spinning
        rotateTransition.play();
        return rotateTransition;
    }

    // Add this method to check for internet connectivity
    private boolean isInternetAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress("8.8.8.8", 53), 1500);
            return true;
        } catch (IOException e) {
            return false;
        }
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
        popupStage.getIcons().add(new Image(getClass().getResourceAsStream("/gui/resources/logo.png")));

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
        popupStage.getIcons().add(new Image(getClass().getResourceAsStream("/gui/resources/logo.png")));

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
        popupStage.getIcons().add(new Image(getClass().getResourceAsStream("/gui/resources/logo.png")));

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
        popupStage.setTitle("Create App Password Instructions");
        popupStage.getIcons().add(new Image(getClass().getResourceAsStream("/gui/resources/logo.png")));

        // Create a Label for the instructions
        javafx.scene.control.Label instructionsLabel = new javafx.scene.control.Label(
            "1. First, make sure that you have 2-Factor Authentication enabled\n" +
            "2. Direct to your browser, a new tab has just appeared\n" +
            "3. Sign in to your Google Account\n" +
            "4. Enter a name for your new App Password\n" +
            "5. Click 'Create'\n" +
            "6. Save this App Password for future usage\n" +
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
    
    private void showResetAppPasswordInstructions() {
        Stage popupStage = new Stage();
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.setTitle("Reset App Password Instructions");
        popupStage.getIcons().add(new Image(getClass().getResourceAsStream("/gui/resources/logo.png")));

        // Create a Label for the instructions
        javafx.scene.control.Label instructionsLabel = new javafx.scene.control.Label(
            "To reset your App Password:\n\n" +
            "1. Direct to your browser, a new tab has just appeared\n" +
            "2. Sign in to your Google Account\n" +
            "3. Delete your previous App Password\n" +
            "4. Enter a name for your new App Password\n" +
            "5. Click 'Create'\n" +
            "6. Save this App Password for future usage\n" +
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