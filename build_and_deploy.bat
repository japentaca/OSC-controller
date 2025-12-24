@echo off
setlocal ENABLEEXTENSIONS

REM Build and deploy debug APK for OSCSensorController
set PROJECT_DIR=%~dp0OSCSensorController

REM Initialize environment (ANDROID_HOME/adb/paths)
call "%~dp0setup_environment.bat"

if not exist "%PROJECT_DIR%" (
  echo [ERROR] Project directory not found: %PROJECT_DIR%
  echo Make sure the project has been created.
  exit /b 1
)

REM Prefer Gradle Wrapper if present, else fallback to gradle in PATH
set GRADLE_CMD=
if exist "%PROJECT_DIR%\gradlew.bat" (
  set GRADLE_CMD=call gradlew.bat
) else (
  where gradle >nul 2>nul
  if %ERRORLEVEL% EQU 0 (
    set GRADLE_CMD=gradle
  ) else (
    REM Fallback a Gradle 8.5 encontrado en el sistema (evitar delayed expansion)
    if exist "C:\Users\jntac\.gradle\wrapper\dists\gradle-8.5-bin\5t9huq95ubn472n8rpzujfbqh\gradle-8.5\bin\gradle.bat" (
      set GRADLE_CMD=call "C:\Users\jntac\.gradle\wrapper\dists\gradle-8.5-bin\5t9huq95ubn472n8rpzujfbqh\gradle-8.5\bin\gradle.bat"
      echo [INFO] Usando Gradle local: C:\Users\jntac\.gradle\wrapper\dists\gradle-8.5-bin\5t9huq95ubn472n8rpzujfbqh\gradle-8.5\bin\gradle.bat
    ) else (
      echo [ERROR] gradlew.bat no encontrado y gradle no en PATH.
      echo Instala Gradle o abre el proyecto en Android Studio para generar el wrapper.
      exit /b 1
    )
  )
)

pushd "%PROJECT_DIR%"
echo [INFO] Cleaning and Building (Clean Build)...
%GRADLE_CMD% clean assembleDebug --info --console=plain
if %ERRORLEVEL% NEQ 0 (
  echo [ERROR] Build failed.
  popd
  exit /b 1
)

REM Ensure ADB exists
where adb >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
  echo [ERROR] adb not found in PATH.
  echo Install Android SDK Platform-Tools and add to PATH.
  popd
  exit /b 1
)

echo [INFO] Checking connected devices...
set DEVICE_FOUND=
adb devices
for /f "tokens=1" %%i in ('adb devices ^| findstr /R /C:"device$"') do set DEVICE_FOUND=1

if not defined DEVICE_FOUND (
  echo [ERROR] No device connected. Connect a device and enable USB debugging.
  popd
  exit /b 1
)

set APK=app\build\outputs\apk\debug\app-debug.apk
if not exist "%APK%" (
  echo [ERROR] APK not found: %APK%
  popd
  exit /b 1
)

echo [INFO] Uninstalling previous version...
adb uninstall com.example.oscsensorcontroller

echo [INFO] Installing APK on device...
adb install -r "%APK%"
if %ERRORLEVEL% NEQ 0 (
  echo [ERROR] Install failed.
  popd
  exit /b 1
)

echo [INFO] Launching app...
adb shell am start -n com.example.oscsensorcontroller/.MainActivity

popd
echo [SUCCESS] Build and deploy completed.
endlocal
