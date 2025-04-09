import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;

public class App {
    private static final String SMTP_SERVER = "smtp.gmail.com"; // Replace with your SMTP server
    private static final int SMTP_PORT = 587; // Common SMTP port
    private static final int MAX_RETRIES = 3; // Maximum number of retries
    private static final long RETRY_DELAY = 60000; // Delay between retries in milliseconds (1 minute)

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Enter the sender's email address:");
        String username = scanner.nextLine();

        System.out.println("Enter the app password:");
        String password = scanner.nextLine();

        System.out.println("Enter the number of recipients:");
        int recipientCount = Integer.parseInt(scanner.nextLine());
        List<String> recipients = new ArrayList<>();

        for (int i = 0; i < recipientCount; i++) {
            System.out.println("Enter the email address of recipient " + (i + 1) + ":");
            recipients.add(scanner.nextLine());
        }

        System.out.println("Enter the subject of the email:");
        String subject = scanner.nextLine();

        System.out.println("Enter the content of the email:");
        StringBuilder content = new StringBuilder();
        String line;
        System.out.println("Type your email content below. Type 'END' on a new line to finish:");
        while (!(line = scanner.nextLine()).equalsIgnoreCase("END")) {
            content.append(line).append("\r\n");
        }

        System.out.println("Do you want to attach files? (yes/no):");
        String attachFiles = scanner.nextLine();
        List<File> files = new ArrayList<>();
        if (attachFiles.equalsIgnoreCase("yes")) {
            System.out.println("Enter the number of files to attach:");
            int fileCount = Integer.parseInt(scanner.nextLine());
            for (int i = 0; i < fileCount; i++) {
                System.out.println("Enter the file path for file " + (i + 1) + ":");
                String filePath = scanner.nextLine();
                File file = new File(filePath);
                if (file.exists()) {
                    files.add(file);
                } else {
                    System.out.println("File not found: " + filePath);
                }
            }
        }

        System.out.println("Do you want to send the email immediately or schedule it? (immediate/schedule):");
        String scheduleOption = scanner.nextLine();

        if (scheduleOption.equalsIgnoreCase("schedule")) {
            System.out.println("Enter the desired time to send the email (format: yyyy-MM-dd HH:mm:ss):");
            String scheduledTime = scanner.nextLine();
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date scheduledDate = dateFormat.parse(scheduledTime);
                long delay = scheduledDate.getTime() - System.currentTimeMillis();

                if (delay > 0) {
                    System.out.println("Email scheduled. Waiting to send...");
                    TimeUnit.MILLISECONDS.sleep(delay);
                } else {
                    System.out.println("The scheduled time is in the past. Sending the email immediately.");
                }
            } catch (Exception e) {
                System.out.println("Invalid date format. Sending the email immediately.");
            }
        }

        int attempt = 0;
        boolean success = false;

        while (attempt < MAX_RETRIES && !success) {
            try {
                attempt++;
                System.out.println("Attempt " + attempt + " to send the email...");
                sendEmail(username, password, recipients, subject, content.toString(), files, scanner);
                success = true;
                System.out.println("Email sent successfully!");
            } catch (Exception e) {
                System.out.println("Failed to send email. Error: " + e.getMessage());
                if (attempt < MAX_RETRIES) {
                    System.out.println("Retrying in 1 minute...");
                    try {
                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY);
                    } catch (InterruptedException ie) {
                        System.out.println("Retry interrupted.");
                        break;
                    }
                } else {
                    System.out.println("All retry attempts failed. Email not sent.");
                }
            }
        }

        scanner.close();
    }

    private static void sendEmail(String username, String password, List<String> recipients, String subject, String content, List<File> files, Scanner scanner) throws Exception {
        // Disable certificate validation (for testing purposes only)
        SSLContext sc = disableCertificateValidation();

        // Create a socket connection to the SMTP server
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

        // Wait for the user to enter the QUIT command
        System.out.println("Type 'QUIT' to terminate the SMTP session:");
        while (!scanner.nextLine().equalsIgnoreCase("QUIT")) {
            System.out.println("Invalid command. Please type 'QUIT' to terminate the session.");
        }

        // QUIT command
        sendCommand(tlsWriter, tlsReader, "QUIT");

        // Close the socket
        sslSocket.close();
        socket.close();
    }

    private static void sendCommand(BufferedWriter writer, BufferedReader reader, String command) throws IOException {
        writer.write(command + "\r\n");
        writer.flush();
        System.out.println("Client: " + command);
        System.out.println("Server: " + reader.readLine());
    }

    private static SSLContext disableCertificateValidation() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }
        };

        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        return sc;
    }
}