# SMTP Client Implementation

A JavaFX desktop application for sending emails via Gmail's SMTP server

## Core Features

- **Login and Authentication**
- **Send Email Process**
- **QUIT Command**
- **GUI Implementation**

## Extra Features

- **Multiple Recipients**
- **File Attachments (with MIME)**
- **Time Scheduling**
- **Retry Mechanism (in case network fails, etc.)**
- **Enhanced Security with SSL/TLS**

## Requirements

- Java SDK 23.0.1 or later
- javafx-sdk-23.0.1 or later
- Internet connection
- Gmail account with [App Passwords enabled](https://myaccount.google.com/apppasswords)

## Run the application

- Prepare a new empty directory
- Open Visual Studio Code
- Go to `File` -> `Open Folder` to open that directory
- Open your terminal and enter: 
```
git clone https://github.com/KhoiNguyenVGU/smtp.git
```

### 1. Run with Visual Studio Code
 
- Go to `File` -> `Open Folder` and open the recently-cloned `smtp` folder
- Open `src\gui\MainApp.java`
- In the bottom left corner, you should now see `Java Projects`, click it
- Scroll down to the bottom and find `Referenced Libraries`, click on the `+` sign next to it
- Add javafx libraries located at `C:\...\openjfx-23.0.1_windows-x64_bin-sdk\javafx-sdk-23.0.1\lib`
- Go to `Run` -> `Add Configuration` and open `.vscode\launch.json`
- Locate this section:
```
{
    "type": "java",
    "name": "MainApp",
    "request": "launch",
    "mainClass": "gui.MainApp",
    "projectName": <your_project_name>
},
```
- Add this line:
`"vmArgs": "--module-path \"C:/.../openjfx-23.0.1_windows-x64_bin-sdk/javafx-sdk-23.0.1/lib\" --add-modules javafx.controls,javafx.fxml"`
- Remember to update your path
- Finally it should look a bit similar to this:
```
{
    "type": "java",
    "name": "MainApp",
    "request": "launch",
    "mainClass": "gui.MainApp",
    "projectName": <your_project_name>,
    "vmArgs": "--module-path \"C:/.../openjfx-23.0.1_windows-x64_bin-sdk/javafx-sdk-23.0.1/lib\" --add-modules javafx.controls,javafx.fxml"
},
```
- Save your `launch.json`
- Run MainApp.java

### 2. Run with .exe file
- Find your jdk-23 folder: `C:\Program Files\Java\jdk-23`
- Find your javafx-sdk folder: `C:\...\openjfx-23.0.1_windows-x64_bin-sdk\javafx-sdk-23.0.1`
- Copy your `javafx-sdk-23.0.1` folder into your `jdk-23` folder
- Copy your new `jdk-23` folder into the recently-cloned `smtp` folder
- Click on the `SMTP.exe` file to run the app


## Usage

- **Login:**  
  Enter your Gmail and App Password. If you need help, click "Forgot Password?" for instructions. There is an option to remember your login credentials
- **Email Content:**  
  Fill in recipients (comma separated), subject, and content. Attach files if needed.
- **Schedule Email:**  
  Check "Schedule Email" and enter the date/time to schedule Email.
- **Send:**  
  Click "Send" to send your Email.
- **Logout:**  
  Click "Logout" to return to the login screen.

## Notes

- **App Passwords:**  
  Gmail requires App Passwords for third-party SMTP access.
- **Internet Connection:**  
  The app checks for internet connectivity before sending emails and will notify you if offline.

## Folder Structure

- `bin/` - Compiled output
- `src/core/` - Terminal-based code
- `src/gui/` - GUI-based code
- `src/gui/resources` - Related resources for GUI

## License

This project is for educational purposes.

---

*Created for Computer Networks 2 Project at VGU.*
