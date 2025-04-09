import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;

import javax.net.ssl.*;

public class EmailSender {
    private static final String SMTP_SERVER = "smtp.gmail.com"; // Replace with your SMTP server
    private static final int SMTP_PORT = 587; // Common SMTP port

    private static BufferedWriter tlsWriter;
    private static BufferedReader tlsReader;
    private static SSLSocket sslSocket;
    private static Socket socket;

    public static void sendEmail(String username, String password, List<String> recipients, String subject, String content, List<File> files) throws Exception {
        // Disable certificate validation (for testing purposes only)
        SSLContext sc = SSLUtils.disableCertificateValidation();

        // Create a socket connection to the SMTP server
        socket = new Socket(SMTP_SERVER, SMTP_PORT);
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
        sslSocket = (SSLSocket) sslSocketFactory.createSocket(socket, SMTP_SERVER, SMTP_PORT, true);
        sslSocket.startHandshake();

        // Replace reader and writer with encrypted streams
        tlsReader = new BufferedReader(new InputStreamReader(sslSocket.getInputStream()));
        tlsWriter = new BufferedWriter(new OutputStreamWriter(sslSocket.getOutputStream()));

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

        // Do not send the QUIT command or close the socket here
        // Keep the connection open for further commands
    }

    public static void sendQuitCommand() throws IOException {
        // Send the QUIT command
        sendCommand(tlsWriter, tlsReader, "QUIT");

        // Close the socket
        sslSocket.close();
        socket.close();
        System.out.println("SMTP session closed.");
    }

    private static void sendCommand(BufferedWriter writer, BufferedReader reader, String command) throws IOException {
        writer.write(command + "\r\n");
        writer.flush();
        System.out.println("Client: " + command);
        System.out.println("Server: " + reader.readLine());
    }
}