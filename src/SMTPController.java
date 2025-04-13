import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea; // Changed from TextField to TextArea
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

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
    private TextArea contentArea; // Updated to match the FXML file

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
            System.out.println("Files attached: " + selectedFiles);
        }
    }

    @FXML
    private void handleSendEmail() {
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
            boolean success = false;

            while (attempt < MAX_RETRIES && !success) {
                try {
                    attempt++;
                    System.out.println("Attempt " + attempt + " to send the email...");
                    sendEmail(username, password, recipients, subject, content, attachedFiles);
                    success = true;
                    System.out.println("Email sent successfully!");
                } catch (Exception e) {
                    System.out.println("Failed to send email. Error: " + e.getMessage());
                    if (attempt < MAX_RETRIES) {
                        System.out.println("Retrying in 1 minute...");
                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY);
                    } else {
                        System.out.println("All retry attempts failed. Email not sent.");
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Failed to send email. Error: " + e.getMessage());
        }
    }

    private void sendEmail(String username, String password, List<String> recipients, String subject, String content, List<File> files) throws Exception {
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

        // QUIT command
        sendCommand(tlsWriter, tlsReader, "QUIT");

        // Close the socket
        sslSocket.close();
        socket.close();
        System.out.println("SMTP session closed.");
    }

    private void sendCommand(BufferedWriter writer, BufferedReader reader, String command) throws Exception {
        writer.write(command + "\r\n");
        writer.flush();
        System.out.println("Client: " + command);
        System.out.println("Server: " + reader.readLine());
    }
}
