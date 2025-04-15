package gui;

import javafx.fxml.FXML;
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

    private List<File> attachedFiles = new ArrayList<>();
    private static final int MAX_RETRIES = 3; // Maximum number of retries
    private static final long RETRY_DELAY = 60000; // Delay between retries in milliseconds (1 minute)

    @FXML
    public void initialize() {
        // Add a listener to the CheckBox to toggle visibility of schedule time fields
        scheduleCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            scheduleTimeLabel.setVisible(newValue);
            scheduleTimeField.setVisible(newValue);
        });
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
                String username = emailField.getText();
                String password = passwordField.getText();
                String recipientsInput = recipientsField.getText();
                String subject = subjectField.getText();
                String content = contentArea.getText();

                // Split recipients by commas
                List<String> recipients = List.of(recipientsInput.split(","));

                // Handle scheduling
                if (scheduleCheckBox.isSelected()) {
                    String scheduledTime = scheduleTimeField.getText();
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    Date scheduledDate = dateFormat.parse(scheduledTime);
                    long delay = scheduledDate.getTime() - System.currentTimeMillis();

                    if (delay > 0) {
                        System.out.println("Email scheduled. Waiting to send...");
                        TimeUnit.MILLISECONDS.sleep(delay);
                    } else {
                        System.out.println("The scheduled time is in the past. Sending the email immediately.");
                    }
                }

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

        // Create the "Send New Email" button
        Button sendNewEmailButton = new Button("Send New Email");
        sendNewEmailButton.setOnAction(event -> {
            popupStage.close(); // Close the popup
            resetForm(); // Reset the form for a new email
        });

        // Create the "Quit" button
        Button quitButton = new Button("Quit");
        quitButton.setOnAction(event -> {
            popupStage.close(); // Close the popup
            javafx.application.Platform.exit(); // Gracefully exit the application
        });

        // Add buttons to the layout
        layout.getChildren().addAll(sendNewEmailButton, quitButton);

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
            resetForm(); // Reset the form for a new email
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
        sendCommand(tlsWriter, tlsReader, Base64.getEncoder().encodeToString(password.getBytes())); // Send encoded password

        // MAIL FROM command
        sendCommand(tlsWriter, tlsReader, "MAIL FROM:<" + username + ">");

        // RCPT TO commands for all recipients
        for (String recipient : recipients) {
            sendCommand(tlsWriter, tlsReader, "RCPT TO:<" + recipient + ">");
        }

        // DATA command
        sendCommand(tlsWriter, tlsReader, "DATA");

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
        System.out.println("Server: " + tlsReader.readLine());

        // Return the SMTP session resources
        Map<String, Object> smtpResources = new HashMap<>();
        smtpResources.put("tlsWriter", tlsWriter);
        smtpResources.put("tlsReader", tlsReader);
        smtpResources.put("sslSocket", sslSocket);
        smtpResources.put("socket", socket);
        return smtpResources;
    }

    private void sendCommand(BufferedWriter writer, BufferedReader reader, String command) throws Exception {
        writer.write(command + "\r\n");
        writer.flush();
        System.out.println("Client: " + command);
        System.out.println("Server: " + reader.readLine());
    }

    private void resetForm() {
        // Clear all input fields
        emailField.clear();
        passwordField.clear();
        recipientsField.clear();
        subjectField.clear();
        contentArea.clear();

        // Clear the attached files list
        attachedFiles.clear();
        attachedFilesListView.getItems().clear();

        // Reset the schedule checkbox and hide the schedule time fields
        scheduleCheckBox.setSelected(false);
        scheduleTimeLabel.setVisible(false);
        scheduleTimeField.clear();
        scheduleTimeField.setVisible(false);
    }
}
