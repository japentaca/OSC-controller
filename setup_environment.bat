@echo off
echo ============================================
echo Configurando entorno Android
echo ============================================

REM Establecer ANDROID_HOME si no está definido
if not defined ANDROID_HOME (
  set "ANDROID_HOME=C:\Users\jntac\AppData\Local\Android\Sdk"
  echo [INFO] ANDROID_HOME no estaba definido, usando: %ANDROID_HOME%
) else (
  echo [INFO] ANDROID_HOME=%ANDROID_HOME%
)

REM Añadir herramientas de Android al PATH
set "PATH=%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\tools;%ANDROID_HOME%\tools\bin;%PATH%"
echo [OK] Platform-Tools añadidos al PATH

REM Verificar ADB
where adb >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  for /f "delims=" %%i in ('where adb') do echo [INFO] ADB encontrado en: %%i
) else (
  echo [WARN] ADB no encontrado en PATH. Instala Platform-Tools y añádelos al PATH.
  echo        https://developer.android.com/studio/releases/platform-tools
)

echo [INFO] Requisitos: JDK 17, Android SDK (API 33+), Platform-Tools.
echo [INFO] Entorno listo.
REM Intentar añadir Gradle al PATH si no está disponible
where gradle >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
  echo [WARN] Gradle no encontrado en PATH. Buscando instalación local...
  for /f "delims=" %%G in ('dir /b /s "%USERPROFILE%\.gradle\wrapper\dists\*\bin\gradle.bat" 2^>nul') do (
    set "GRADLE_BIN=%%G"
  )
  if defined GRADLE_BIN (
    for %%P in ("%GRADLE_BIN%") do set "GRADLE_DIR=%%~dpP.."
    set "PATH=%GRADLE_DIR%;%PATH%"
    echo [OK] Gradle añadido al PATH desde: %GRADLE_DIR%
    echo %%G > "gradle_path.txt"
  ) else (
    echo [WARN] No se encontró Gradle instalado. Usa Android Studio para generar gradlew o instala Gradle.
  )
)

REM Preferir JDK 17 de Android Studio (jbr) si está disponible
set "AS_JBR=C:\Program Files\Android\Android Studio\jbr"
if exist "%AS_JBR%\bin\java.exe" (
  REM Verificar si la versión actual de java es 17; si no, usar jbr
  set "JAVA_17_DETECTADO="
  for /f "tokens=* delims=" %%V in ('java -version 2^>^&1 ^| findstr /R /C:"version \"17\.""') do set "JAVA_17_DETECTADO=1"
  if not defined JAVA_17_DETECTADO (
    set "JAVA_HOME=%AS_JBR%"
    set "PATH=%JAVA_HOME%\bin;%PATH%"
    echo [OK] JAVA_HOME establecido a JDK 17 de Android Studio: %JAVA_HOME%
  ) else (
    echo [INFO] JDK 17 ya detectado en el entorno actual.
  )
)
