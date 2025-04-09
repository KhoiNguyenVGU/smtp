import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class App {
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
                EmailSender.sendEmail(username, password, recipients, subject, content.toString(), files);
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

        // Wait for the user to type "QUIT" before exiting
        System.out.println("Type 'QUIT' to exit the program:");
        while (true) {
            String input = scanner.nextLine();
            if (input.equalsIgnoreCase("quit")) {
                System.out.println("Closing the SMTP session...");
                try {
                    EmailSender.sendQuitCommand();
                } catch (Exception e) {
                    System.out.println("Error while closing the SMTP session: " + e.getMessage());
                }
                System.out.println("Exiting the program. Goodbye!");
                break;
            } else {
                System.out.println("Invalid input. Please type 'quit' to exit.");
            }
        }

        scanner.close();
    }
}