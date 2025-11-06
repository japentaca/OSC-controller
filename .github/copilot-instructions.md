# OSC Sensor Controller - AI Agent Instructions

## Project Overview
Android Kotlin application that reads device sensors (accelerometer, gyroscope, magnetometer, light, proximity, pressure, temperature, humidity) and transmits data as **OSC (Open Sound Control) messages** via UDP to a configurable server. Core architecture: sensor events → batched OSC messages → network transmission on background thread.

## Architecture & Key Components

### OSC Protocol Implementation
- **Custom `SimpleOSCClient`** (`app/src/main/java/com/example/oscsensorcontroller/SimpleOSCClient.kt`): Minimal UDP-based OSC implementation
  - **Why custom?** Avoids external `javaosc` library that imports `java.awt.*` classes, causing `NoClassDefFoundError` on Android (AWT not available in Android runtime)
  - **Message format**: Path (null-terminated, 4-byte padded) + Type tag (`,f...`, padded) + Float values (big-endian)
  - **OSC paths**: `/sensors/{accelerometer|gyroscope|magnetometer|light|proximity|pressure|temperature|humidity}`

### Sensor Management Flow
1. **MainActivity** registers listeners with `SensorManager` for 8 sensor types (none are GPS)
2. `onSensorChanged()` stores latest float values in persistent fields (e.g., `accelerometerData`)
3. **Sampling rate throttling**: `sendOSCMessages()` only executes if `currentTime - lastSendTime >= samplingRate` (default 200ms)
4. Messages sent only if corresponding **CheckBox is checked** and `isSending=true`

### Threading Model
- **Main thread**: UI, sensor registration/unregistration
- **OSC background thread**: `HandlerThread("OSCThread")` handles network operations (`connect()`, `send()`) via `oscHandler?.post()`
  - Prevents ANR (Application Not Responding) from network I/O
  - Started in `startSending()`, safely shut down via `quitSafely()` in `stopSending()`

### Critical Dependencies
- **Target API 33** (Android 13), **Min API 21** (Android 5.0)
- **Kotlin 1.9.10**, **JDK 17**
- **AndroidX** (core-ktx, appcompat, material, constraintlayout)
- **No external OSC library** - pure custom UDP implementation

## Build & Deployment

### Build Commands
```bash
# From OSCSensorController/ directory:
./gradlew assembleDebug        # Creates app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # Minify enabled in build.gradle
```

### Automated Deploy (Windows Batch)
```bash
cd <workspace>
build_and_deploy.bat  # Calls setup_environment.bat, then gradle assembleDebug, then adb install-r & launch
```

**Prerequisites**: 
- `ANDROID_HOME` must point to Android SDK (auto-detected: `C:\Users\jntac\AppData\Local\Android\Sdk`)
- ADB in PATH (from `%ANDROID_HOME%\platform-tools`)
- Connected Android device with USB debugging enabled

### Debug Workflow
```bash
adb logcat -c                          # Clear logs
<run app>
adb logcat | grep -E "(MainActivity|SimpleOSCClient)  # Monitor sensor/OSC logs
```

## Important Patterns & Constraints

### Sensor Data Storage
- 3-value sensors (accel, gyro, magnet): `floatArrayOf(x, y, z)` - **persisted across onSensorChanged calls**
- 1-value sensors (light, proximity, etc.): single `Float` field
- **No polling** - data pulled only when sending messages

### UI-to-Network Thread Safety
- `runOnUiThread {}` wraps all UI updates from `oscHandler` background operations
- `oscClient?.send()` always checks `isConnected && !socket.isClosed` before transmitting

### Exception Handling
- **Global `Thread.UncaughtExceptionHandler`** catches `NoClassDefFoundError` for java.awt (library compatibility)
- OSC send failures logged but don't crash app; sampling continues

### Manifest Permissions (Required)
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.VIBRATE" />
```

## Common Developer Tasks

### Adding a New Sensor
1. Add field: `private var mySensorData = floatArrayOf(...)`
2. Get sensor in `initializeSensors()`: `sensorManager.getDefaultSensor(Sensor.TYPE_MY_SENSOR)`
3. Add CheckBox to `activity_main.xml` with id `mySensorCheckBox`
4. Register in `registerSensorListeners()`: `if (mySensorCheckBox.isChecked && mySensor != null) sensorManager.registerListener(this, mySensor, ...)`
5. Handle in `onSensorChanged()`: `Sensor.TYPE_MY_SENSOR -> mySensorData = ...`
6. Transmit in `sendOSCMessages()`: `if (mySensorCheckBox.isChecked) oscClient?.send("/sensors/mysensor", ...)`

### Changing OSC Target
- Edit `ipEditText` (default: `192.168.0.5`) and `portEditText` (default: `9000`) in UI
- Or modify `initializeViews()` in `MainActivity.kt` for different defaults

### Adjusting Sampling Rate
- UI: `samplingRateEditText` (milliseconds, default 200ms)
- Parsed in `startSending()` → stored in `samplingRate` → checked in `sendOSCMessages()`

## File Structure Reference
```
OSCSensorController/
├── app/
│   ├── build.gradle                      # Module config; note: no external OSC lib
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # Permissions: INTERNET, ACCESS_NETWORK_STATE, VIBRATE
│   │   ├── java/com/example/oscsensorcontroller/
│   │   │   ├── MainActivity.kt          # UI, sensor orchestration, lifecycle
│   │   │   └── SimpleOSCClient.kt       # Custom OSC/UDP encoder & sender
│   │   └── res/layout/
│   │       └── activity_main.xml        # 8 sensor CheckBoxes, IP/port/sampling-rate EditTexts, start/stop switch
│   └── proguard-rules.pro
├── build.gradle                         # Root: Kotlin 1.9.10, Android plugin 8.2.2
└── settings.gradle                      # Single-module project: ':app'
```

## Debugging Tips

1. **Sensor not registering?** Check CheckBox state and verify `sensorManager.getDefaultSensor(TYPE)` returns non-null
2. **OSC not sending?** Check IP/port validity; review `SimpleOSCClient` logs for socket creation failures
3. **ANR or freezing?** Likely blocking network call on main thread; verify all `InetAddress.getByName()` and `socket.send()` happen in `oscHandler`
4. **High CPU/battery drain?** Reduce sampling rate or uncheck unnecessary sensors; default 200ms is reasonable
5. **Gradle build failures?** Ensure JDK 17 active; check `gradle.properties` for `jvmargs` heap size
