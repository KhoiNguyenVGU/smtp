package gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

public class SMTPController {

    @FXML
    private CheckBox scheduleCheckBox;

    @FXML
    private Label scheduleTimeLabel;

    @FXML
    private TextField scheduleTimeField;

    @FXML
    private TextField emailField;

    @FXML
    private TextField passwordField;

    @FXML
    private TextField recipientsField;

    @FXML
    private TextField subjectField;

    @FXML
    private TextArea contentArea;

    @FXML
    private ListView<String> attachedFilesListView; // ListView to display attached files

    private String userEmail;
    private String userPassword;

    private List<File> attachedFiles = new ArrayList<>();
    private static final int MAX_RETRIES = 3; // Maximum number of retries
    private static final long RETRY_DELAY = 60000; // Delay between retries in milliseconds (1 minute)

    private Stage scheduledPopupStage; // Reference to the scheduled popup stage
    private boolean isScheduled = true; // Flag to track if the email is scheduled

    @FXML
    public void initialize() {
        // Add a listener to the CheckBox to toggle visibility of schedule time fields
        scheduleCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            scheduleTimeLabel.setVisible(newValue);
            scheduleTimeField.setVisible(newValue);
        });
    }

    public void setUserCredentials(String email, String password) {
        this.userEmail = email;
        this.userPassword = password;
    }

    @FXML
    private void handleAttachFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Files to Attach");
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(new Stage());
        if (selectedFiles != null) {
            attachedFiles.addAll(selectedFiles);

            // Update the ListView with the names of the attached files
            for (File file : selectedFiles) {
                attachedFilesListView.getItems().add(file.getName());
            }

            System.out.println("Files attached: " + selectedFiles);
        }
    }

    @FXML
    private void handleSendEmail() {
        if (recipientsField.getText().trim().isEmpty()) {
            showInvalidRecipientPopup("Recipient field cannot be empty.");
            return;
        }

        if (subjectField.getText().trim().isEmpty()) {
            // Show confirmation popup if the subject is empty
            showEmptySubjectConfirmationPopup();
            return;
        }

        if (scheduleCheckBox.isSelected()) {
            try {
                String scheduledTime = scheduleTimeField.getText();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date scheduledDate = dateFormat.parse(scheduledTime);

                if (scheduledDate.before(new Date())) {
                    Platform.runLater(this::showInvalidScheduledTimePopup);
                    return;
                }

                isScheduled = true; // Mark as scheduled
                Platform.runLater(() -> showScheduledPopup(scheduledTime));

                long delay = scheduledDate.getTime() - System.currentTimeMillis();
                new Thread(() -> {
                    try {
                        Thread.sleep(delay);
                        Platform.runLater(() -> {
                            sendEmailProcess();
                            isScheduled = false; // Reset the scheduled state after sending

                            // Close the scheduled popup if it is still open
                            if (scheduledPopupStage != null && scheduledPopupStage.isShowing()) {
                                scheduledPopupStage.close();
                            }
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> showErrorPopup("Error", "Failed to send the scheduled email."));
                    }
                }).start();
            } catch (Exception e) {
                Platform.runLater(() -> showErrorPopup("Invalid Date Format", "Please enter a valid date and time in the format: yyyy-MM-dd HH:mm:ss"));
            }
        } else {
            sendEmailProcess();
            isScheduled = false; // Reset the scheduled state for immediate sending
        }
    }

    private void sendEmailProcess() {
        // Create and show the "Sending in process..." popup
        Stage sendingPopup = createSendingPopup();
        Label sendingLabel = new Label("Attempt 1: Sending in process... Please wait.");
        VBox layout = (VBox) sendingPopup.getScene().getRoot();
        layout.getChildren().add(0, sendingLabel); // Add the label at the top
        sendingPopup.show();

        // Run the email sending process in a background thread
        new Thread(() -> {
            try {
                // Retrieve input values
                String username = userEmail; // Use userEmail instead of emailField
                String password = userPassword; // Use userPassword instead of passwordField
                String recipientsInput = recipientsField.getText();
                String subject = subjectField.getText();
                String content = contentArea.getText();

                // Split recipients by commas
                List<String> recipients = List.of(recipientsInput.split(","));

                // Retry logic
                int attempt = 0;
                final boolean[] success = {false}; // Use a single-element array to hold the success value
                Map<String, Object> smtpResources = new HashMap<>();

                while (attempt < MAX_RETRIES && !success[0]) {
                    try {
                        attempt++;
                        int currentAttempt = attempt;
                        javafx.application.Platform.runLater(() -> sendingLabel.setText("Attempt " + currentAttempt + ": Sending in process... Please wait."));
                        System.out.println("Attempt " + attempt + " to send the email...");
                        // Call sendEmail and retrieve the SMTP session resources
                        smtpResources = sendEmailAndGetResources(username, password, recipients, subject, content, attachedFiles);
                        success[0] = true; // Update the success value
                        System.out.println("Email sent successfully!");
                    } catch (Exception e) {
                        if (e.getMessage().contains("Invalid credentials")) {
                            System.out.println("Invalid credentials detected.");
                            javafx.application.Platform.runLater(() -> {
                                sendingPopup.close();
                                showInvalidCredentialsPopup(); // Show popup for invalid credentials
                            });
                            return; // Exit the retry loop immediately
                        }
                        if (e.getMessage().contains("Invalid recipient")) {
                            System.out.println("Invalid recipient detected.");
                            javafx.application.Platform.runLater(() -> {
                                sendingPopup.close();
                                showInvalidRecipientPopup(e.getMessage()); // Show popup for invalid recipient
                            });
                            return; // Exit the retry loop immediately
                        }
                        System.out.println("Failed to send email. Error: " + e.getMessage());
                        if (attempt < MAX_RETRIES) {
                            int currentAttempt = attempt;
                            javafx.application.Platform.runLater(() -> sendingLabel.setText("Attempt " + currentAttempt + " failed. Retrying in 1 minute..."));
                            System.out.println("Retrying in 1 minute...");
                            TimeUnit.MILLISECONDS.sleep(RETRY_DELAY);
                        } else {
                            System.out.println("All retry attempts failed. Email not sent.");
                            javafx.application.Platform.runLater(() -> sendingLabel.setText("All attempts failed. Email not sent."));
                        }
                    }
                }

                // Close the "Sending in process..." popup
                Map<String, Object> finalSmtpResources = smtpResources;
                javafx.application.Platform.runLater(() -> {
                    sendingPopup.close();
                    if (success[0]) {
                        showSuccessPopup(
                            (BufferedWriter) finalSmtpResources.get("tlsWriter"),
                            (BufferedReader) finalSmtpResources.get("tlsReader"),
                            (SSLSocket) finalSmtpResources.get("sslSocket"),
                            (Socket) finalSmtpResources.get("socket")
                        );
                    } else {
                        showFailurePopup(); // Show failure popup if all retries fail
                    }
                });
            } catch (Exception e) {
                System.out.println("Failed to send email. Error: " + e.getMessage());
                javafx.application.Platform.runLater(() -> {
                    sendingPopup.close();
                    showFailurePopup(); // Show failure popup if an exception occurs
                });
            }
        }).start();
    }

    private Stage createSendingPopup() {
        // Create a new Stage for the "Sending in process..." popup
        Stage popupStage = new Stage();
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.setTitle("Sending Email");

        // Create a VBox layout for dynamic updates
        VBox layout = new VBox(10);
        layout.setStyle("-fx-padding: 20; -fx-alignment: center;");

        // Create a Label for the "Sending in process..." message
        //Label sendingLabel = new Label("Sending in process... Please wait.");
        //sendingLabel.setStyle("-fx-font-size: 14; -fx-text-alignment: center;");

        // Add the label to the layout
        //layout.getChildren().add(sendingLabel);

        // Set the scene and return the popup stage
        Scene scene = new Scene(layout, 300, 150);
        popupStage.setScene(scene);
        return popupStage;
    }

    private void showSuccessPopup(BufferedWriter tlsWriter, BufferedReader tlsReader, SSLSocket sslSocket, Socket socket) {
        // Create a new Stage for the popup
        Stage popupStage = new Stage();
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.setTitle("Email Sent Successfully");

        // Create a Label for the success message
        Label successLabel = new Label("The email was sent successfully!");

        // Create the "Send New Email" button
        Button sendNewEmailButton = new Button("Send New Email");
        sendNewEmailButton.setOnAction(event -> {
            popupStage.close(); // Close the popup
            resetForm(); // Reset the form for a new email
        });

        // Create the "Quit" button
        Button quitButton = new Button("Quit");
        quitButton.setOnAction(event -> {
            try {
                // Send the QUIT command and close the SMTP session
                sendCommand(tlsWriter, tlsReader, "QUIT");
                sslSocket.close();
                socket.close();
                System.out.println("SMTP session closed.");
            } catch (Exception e) {
                System.out.println("Failed to close SMTP session. Error: " + e.getMessage());
            }
            popupStage.close(); // Close the popup
            javafx.application.Platform.exit(); // Gracefully exit the application
        });

        // Arrange the components in a VBox
        VBox layout = new VBox(10);
        layout.getChildren().addAll(successLabel, sendNewEmailButton, quitButton);
        layout.setStyle("-fx-padding: 20; -fx-alignment: center;");

        // Set the scene and show the popup
        Scene scene = new Scene(layout, 300, 150);
        popupStage.setScene(scene);
        popupStage.showAndWait();
    }

    private void showFailurePopup() {
        // Create a new Stage for the popup
        Stage popupStage = new Stage();
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.setTitle("Email Sending Failed");

        // Create a Label for the failure message
        Label failureLabel = new Label("All attempts to send the email have failed.");

        // Create the "Send New Email" button
        Button sendNewEmailButton = new Button("Send New Email");
        sendNewEmailButton.setOnAction(event -> {
            popupStage.close(); // Close the popup
            resetForm(); // Clear all previous input
        });

        // Create the "Quit" button
        Button quitButton = new Button("Quit");
        quitButton.setOnAction(event -> {
            popupStage.close(); // Close the popup
            javafx.application.Platform.exit(); // Gracefully exit the application
        });

        // Arrange the components in a VBox
        VBox layout = new VBox(10);
        layout.getChildren().addAll(failureLabel, sendNewEmailButton, quitButton);
        layout.setStyle("-fx-padding: 20; -fx-alignment: center;");

        // Set the scene and show the popup
        Scene scene = new Scene(layout, 300, 150);
        popupStage.setScene(scene);
        popupStage.showAndWait();
    }

    private void showInvalidCredentialsPopup() {
        // Create a new Stage for the popup
        Stage popupStage = new Stage();
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.setTitle("Invalid Credentials");

        // Create a Label for the error message
        Label errorLabel = new Label("The provided username or password is incorrect.");

        // Create the "Try Again" button
        Button tryAgainButton = new Button("Try Again");
        tryAgainButton.setOnAction(event -> {
            popupStage.close(); // Close the popup
            resetForm(); // Reset the form for a new attempt
        });

        // Create the "Quit" button
        Button quitButton = new Button("Quit");
        quitButton.setOnAction(event -> {
            popupStage.close(); // Close the popup
            javafx.application.Platform.exit(); // Gracefully exit the application
        });

        // Arrange the components in a VBox
        VBox layout = new VBox(10);
        layout.getChildren().addAll(errorLabel, tryAgainButton, quitButton);
        layout.setStyle("-fx-padding: 20; -fx-alignment: center;");

        // Set the scene and show the popup
        Scene scene = new Scene(layout, 300, 150);
        popupStage.setScene(scene);
        popupStage.showAndWait();
    }

    private void showInvalidRecipientPopup(String errorMessage) {
        // Create a new Stage for the popup
        Stage popupStage = new Stage();
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.setTitle("Invalid Recipient");

        // Create a Label for the error message
        Label errorLabel = new Label("The recipient address is invalid:\n" + errorMessage);

        // Create the "OK" button
        Button okButton = new Button("OK");
        okButton.setOnAction(event -> popupStage.close()); // Close the popup

        // Arrange the components in a VBox
        VBox layout = new VBox(10);
        layout.getChildren().addAll(errorLabel, okButton);
        layout.setStyle("-fx-padding: 20; -fx-alignment: center;");

        // Set the scene and show the popup
        Scene scene = new Scene(layout, 300, 150);
        popupStage.setScene(scene);
        popupStage.showAndWait();
    }

    private void showEmptySubjectConfirmationPopup() {
        // Create a new Stage for the popup
        Stage popupStage = new Stage();
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.setTitle("Empty Subject");

        // Create a Label for the confirmation message
        Label confirmationLabel = new Label("The subject field is empty. Do you want to send the email without a subject?");

        // Create the "OK" button
        Button okButton = new Button("OK");
        okButton.setOnAction(event -> {
            popupStage.close(); // Close the popup
            sendEmailProcess(); // Proceed with sending the email
        });

        // Create the "Cancel" button
        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(event -> {
            popupStage.close(); // Close the popup without clearing the form
        });

        // Arrange the components in a VBox
        VBox layout = new VBox(10);
        layout.getChildren().addAll(confirmationLabel, okButton, cancelButton);
        layout.setStyle("-fx-padding: 20; -fx-alignment: center;");

        // Set the scene and show the popup
        Scene scene = new Scene(layout, 400, 150);
        popupStage.setScene(scene);
        popupStage.showAndWait();
    }

    private Map<String, Object> sendEmailAndGetResources(String username, String password, List<String> recipients, String subject, String content, List<File> files) throws Exception {
        // Disable certificate validation (for testing purposes only)
        SSLContext sc = SSLUtils.disableCertificateValidation();

        // Create a socket connection to the SMTP server
        String SMTP_SERVER = "smtp.gmail.com";
        int SMTP_PORT = 587;
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
        SSLSocketFactory sslSocketFactory = sc.getSocketFactory();
        SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(socket, SMTP_SERVER, SMTP_PORT, true);
        sslSocket.startHandshake();

        // Replace reader and writer with encrypted streams
        BufferedReader tlsReader = new BufferedReader(new InputStreamReader(sslSocket.getInputStream()));
        BufferedWriter tlsWriter = new BufferedWriter(new OutputStreamWriter(sslSocket.getOutputStream()));

        // AUTH LOGIN command
        sendCommand(tlsWriter, tlsReader, "AUTH LOGIN");
        sendCommand(tlsWriter, tlsReader, Base64.getEncoder().encodeToString(username.getBytes())); // Send encoded username
        String authResponse = sendCommand(tlsWriter, tlsReader, Base64.getEncoder().encodeToString(password.getBytes())); // Send encoded password

        // Check for authentication errors
        if (authResponse.startsWith("535") || authResponse.startsWith("530")) {
            throw new Exception("Invalid credentials: " + authResponse);
        }

        // MAIL FROM command
        sendCommand(tlsWriter, tlsReader, "MAIL FROM:<" + username + ">");

        // RCPT TO commands for all recipients
        for (String recipient : recipients) {
            String rcptResponse = sendCommand(tlsWriter, tlsReader, "RCPT TO:<" + recipient + ">");
            if (rcptResponse.startsWith("553")) {
                throw new Exception("Invalid recipient: " + rcptResponse);
            }
        }

        // DATA command
        String dataResponse = sendCommand(tlsWriter, tlsReader, "DATA");
        if (!dataResponse.startsWith("354")) {
            throw new Exception("Error during DATA command: " + dataResponse);
        }

        // MIME headers
        String boundary = "----=_Part_" + System.currentTimeMillis();
        tlsWriter.write("Subject: " + subject + "\r\n");
        tlsWriter.write("From: " + username + "\r\n");
        tlsWriter.write("To: " + String.join(", ", recipients) + "\r\n");
        tlsWriter.write("MIME-Version: 1.0\r\n");
        tlsWriter.write("Content-Type: multipart/mixed; boundary=\"" + boundary + "\"\r\n");
        tlsWriter.write("\r\n");

        // Email content
        tlsWriter.write("--" + boundary + "\r\n");
        tlsWriter.write("Content-Type: text/plain; charset=\"UTF-8\"\r\n");
        tlsWriter.write("Content-Transfer-Encoding: 7bit\r\n");
        tlsWriter.write("\r\n");
        tlsWriter.write(content);
        tlsWriter.write("\r\n");

        // Attachments (if any)
        for (File file : files) {
            tlsWriter.write("--" + boundary + "\r\n");
            tlsWriter.write("Content-Type: application/octet-stream; name=\"" + file.getName() + "\"\r\n");
            tlsWriter.write("Content-Transfer-Encoding: base64\r\n");
            tlsWriter.write("Content-Disposition: attachment; filename=\"" + file.getName() + "\"\r\n");
            tlsWriter.write("\r\n");

            // Encode file in Base64
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            String encodedFile = Base64.getEncoder().encodeToString(fileBytes);
            tlsWriter.write(encodedFile);
            tlsWriter.write("\r\n");
        }

        // End of MIME message
        tlsWriter.write("--" + boundary + "--\r\n");
        tlsWriter.write(".\r\n"); // End of message
        tlsWriter.flush();
        String finalResponse = tlsReader.readLine();
        System.out.println("Server: " + finalResponse);

        // Check for errors in the final response
        if (!finalResponse.startsWith("250")) {
            throw new Exception("Error after sending email: " + finalResponse);
        }

        // Return the SMTP session resources
        Map<String, Object> smtpResources = new HashMap<>();
        smtpResources.put("tlsWriter", tlsWriter);
        smtpResources.put("tlsReader", tlsReader);
        smtpResources.put("sslSocket", sslSocket);
        smtpResources.put("socket", socket);
        return smtpResources;
    }

    private String sendCommand(BufferedWriter writer, BufferedReader reader, String command) throws Exception {
        writer.write(command + "\r\n");
        writer.flush();
        System.out.println("Client: " + command);
        String response = reader.readLine();
        System.out.println("Server: " + response);
        return response;
    }

    private void resetForm() {
        recipientsField.clear();
        subjectField.clear();
        contentArea.clear();
        attachedFiles.clear();
        attachedFilesListView.getItems().clear();
        scheduleCheckBox.setSelected(false);
        scheduleTimeLabel.setVisible(false);
        scheduleTimeField.clear();
        scheduleTimeField.setVisible(false);
        isScheduled = false; // Reset the scheduled state

        // Close the scheduled popup if it is still open
        if (scheduledPopupStage != null && scheduledPopupStage.isShowing()) {
            scheduledPopupStage.close();
        }
        scheduledPopupStage = null; // Reset the reference
    }

    private void showInvalidScheduledTimePopup() {
        // Create a new Stage for the popup
        Stage popupStage = new Stage();
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.setTitle("Invalid Scheduled Time");

        // Create a Label for the error message
        Label errorLabel = new Label("The scheduled time is in the past. Please select a valid future time.");

        // Create the "OK" button
        Button okButton = new Button("OK");
        okButton.setOnAction(event -> popupStage.close()); // Close the popup

        // Arrange the components in a VBox
        VBox layout = new VBox(10);
        layout.getChildren().addAll(errorLabel, okButton);
        layout.setStyle("-fx-padding: 20; -fx-alignment: center;");

        // Set the scene and show the popup
        Scene scene = new Scene(layout, 300, 150);
        popupStage.setScene(scene);
        popupStage.showAndWait();
    }

    private void showErrorPopup(String title, String message) {
        // Create a new Stage for the popup
        Stage popupStage = new Stage();
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.setTitle(title);

        // Create a Label for the error message
        Label errorLabel = new Label(message);

        // Create the "OK" button
        Button okButton = new Button("OK");
        okButton.setOnAction(event -> popupStage.close()); // Close the popup

        // Arrange the components in a VBox
        VBox layout = new VBox(10);
        layout.getChildren().addAll(errorLabel, okButton);
        layout.setStyle("-fx-padding: 20; -fx-alignment: center;");

        // Set the scene and show the popup
        Scene scene = new Scene(layout, 300, 150);
        popupStage.setScene(scene);
        popupStage.showAndWait();
    }

    private void showScheduledPopup(String scheduledTime) {
        if (!isScheduled) {
            return; // Do not show the popup if the email is no longer scheduled
        }

        scheduledPopupStage = new Stage(); // Store the popup stage reference
        scheduledPopupStage.initModality(Modality.APPLICATION_MODAL);
        scheduledPopupStage.setTitle("Email Scheduled");

        Label scheduledLabel = new Label("The email is scheduled to be sent at: " + scheduledTime);

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(event -> {
            scheduledPopupStage.close();
            isScheduled = false; // Cancel the scheduled email
            System.out.println("Scheduled email canceled.");
        });

        VBox layout = new VBox(10);
        layout.getChildren().addAll(scheduledLabel, cancelButton);
        layout.setStyle("-fx-padding: 20; -fx-alignment: center;");

        Scene scene = new Scene(layout, 300, 150);
        scheduledPopupStage.setScene(scene);
        scheduledPopupStage.showAndWait();
    }

    @FXML
    private void handleLogout() {
        try {
            // Load the login view
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/LoginView.fxml"));
            Stage stage = (Stage) recipientsField.getScene().getWindow();
            stage.setScene(new Scene(loader.load()));

            // Reset the stage title
            stage.setTitle("Login - SMTP Email Sender");
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}