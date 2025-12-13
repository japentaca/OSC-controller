# OSC Controller Developer Guide

## Build and Deployment

A convenience script is provided to build the debug APK and deploy it to a connected Android device.

### Prerequisites
1.  **Android SDK**: Ensure standard Android SDK tools (`adb`, etc.) are installed.
2.  **Device**: Connect your Android device via USB and enable USB Debugging.

### Quick Start
Run the following execution script from the root directory:

```bat
.\build_and_deploy.bat
```

This script will:
1.  Initialize the environment (via `setup_environment.bat`).
2.  Build the project using Gradle (`assembleDebug`).
3.  Check for a connected ADB device.
4.  Install the generated APK (`app-debug.apk`).
5.  Launch the application automatically.

Gradle is located in C:\Users\jntac\.gradle\wrapper\dists\gradle-8.0-bin\ca5e32bp14vu59qr306oxotwh\gradle-8.0\bin\gradle.bat

### Troubleshooting
If the build fails, the script is configured to run with `--stacktrace --info` to provide detailed error logs. Check the console output for specific Gradle errors.
