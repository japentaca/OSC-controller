# OSC Sensor Controller - AI Agent Instructions

## 🚀 QUICK START - Compilar y Desplegar

**Para compilar y desplegar la app en un dispositivo Android, ejecuta SIEMPRE:**

```batch
.\build_and_deploy.bat
```

Desde: `c:\Users\jntac\Documents\prj\jape\IAs varias\OSC controller`

El script automatiza completamente el proceso: configuración de entorno → compilación → instalación → lanzamiento.

---

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

### ⚠️ **RECOMMENDED: Automated Build & Deploy Script**

**INVOCAR SIEMPRE:** `build_and_deploy.bat` para compilar y desplegar la app en el dispositivo Android.

```batch
# Desde el directorio raíz del workspace:
cd c:\Users\jntac\Documents\prj\jape\IAs varias\OSC controller
.\build_and_deploy.bat
```

**¿Qué hace `build_and_deploy.bat`?**
1. Ejecuta `setup_environment.bat` (configura variables de entorno y rutas)
2. Compila la app: `gradle assembleDebug` (genera `app/build/outputs/apk/debug/app-debug.apk`)
3. Instala en el dispositivo: `adb install -r` (instalación con remplazo de versión anterior)
4. Lanza automáticamente la app en el dispositivo

**Prerequisites**: 
- `ANDROID_HOME` debe apuntar al Android SDK (auto-detectado: `C:\Users\jntac\AppData\Local\Android\Sdk`)
- ADB en PATH (desde `%ANDROID_HOME%\platform-tools`)
- Dispositivo Android conectado con USB debugging habilitado
- Archivo `gradle_path.txt` en el directorio raíz con la ruta correcta a `gradlew.bat`

---

### Manual Build Commands (Alternativa - No Recomendado)

Si necesitas compilar manualmente **sin usar el script automatizado**:

```bash
# Desde OSCSensorController/ directory:
./gradlew assembleDebug        # Crea: app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # Crea versión release (Minify habilitado en build.gradle)

# Para instalar manualmente en dispositivo:
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.oscsensorcontroller/.MainActivity
```

⚠️ **Nota**: Este método requiere configuración manual de variables de entorno y es propenso a errores. Se recomienda usar **`build_and_deploy.bat`** siempre.

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

## Persistencia de Datos (SharedPreferences)

La app guarda automáticamente toda la configuración en el dispositivo Android usando **SharedPreferences**:

### Datos Persistidos
- **Configuración general**: IP del servidor, puerto OSC, sampling rate
- **Estado de sensores**: Qué sensores están habilitados/deshabilitados (checkboxes)
- **Umbrales**: Valor umbral (threshold) personalizado para cada sensor (0-1 rango)
- **Normalización**: Estado de la checkbox de normalización

### Cómo Funciona
- Los valores se **guardan automáticamente** cuando el usuario los modifica en la UI
- Al reiniciar la app, todos los valores se **cargan automáticamente** desde SharedPreferences
- Si no hay datos previos, se usan los **valores por defecto**

### Archivo de Implementación
- **`PreferencesManager.kt`**: Clase helper que maneja todo el almacenamiento y recuperación de datos
  - Métodos: `getServerIP()`, `saveServerIP()`, `getSensorThreshold()`, `saveSensorThreshold()`, etc.
  - Usa `context.getSharedPreferences()` con permisos privados

### Valores por Defecto
```kotlin
SERVER_IP: "192.168.0.5"
SERVER_PORT: 9000
SAMPLING_RATE: 200ms
SENSOR_THRESHOLD: 0.05f (para todos los sensores)
SENSORS_ENABLED: true (todos habilitados por defecto)
NORMALIZE: false
```

### Modificar Valores Persistidos (Desarrollo)
```kotlin
// Guardar
preferencesManager.saveServerIP("192.168.1.100")
preferencesManager.saveSensorThreshold("accelerometer", 0.1f)
preferencesManager.saveSensorEnabled("gyroscope", false)

// Cargar
val ip = preferencesManager.getServerIP()
val threshold = preferencesManager.getSensorThreshold("accelerometer")
val enabled = preferencesManager.getSensorEnabled("gyroscope")

// Obtener todos los umbrales
val allThresholds = preferencesManager.getAllThresholds()

// Limpiar todo (use con cuidado)
preferencesManager.resetToDefaults()
```

## Common Developer Tasks

### Adding a New Sensor
1. Add field: `private var mySensorData = floatArrayOf(...)`
2. Get sensor in `initializeSensors()`: `sensorManager.getDefaultSensor(Sensor.TYPE_MY_SENSOR)`
3. Add CheckBox to `activity_main.xml` with id `mySensorCheckBox`
4. Register in `registerSensorListeners()`: `if (mySensorCheckBox.isChecked && mySensor != null) sensorManager.registerListener(this, mySensor, ...)`
5. Handle in `onSensorChanged()`: `Sensor.TYPE_MY_SENSOR -> mySensorData = ...`
6. Transmit in `sendOSCMessages()`: `if (mySensorCheckBox.isChecked) oscClient?.send("/sensors/mysensor", ...)`
7. **Agregar persistencia en `PreferencesManager.kt`**:
   - Agregar Keys: `const val KEY_MYSENSOR_ENABLED = "mysensor_enabled"` y `const val KEY_MYSENSOR_THRESHOLD = "mysensor_threshold"`
   - Agregar métodos `getSensorEnabled()` y `getSensorThreshold()` con case para "mysensor"
   - Agregar métodos `saveSensorEnabled()` y `saveSensorThreshold()` con case para "mysensor"
8. Cargar en `initializeSensors()`: `mySensorCheckBox.isChecked = preferencesManager.getSensorEnabled("mysensor")`
9. Agregar listener en `initializeSensors()`: `mySensorCheckBox.setOnCheckedChangeListener { _, isChecked -> preferencesManager.saveSensorEnabled("mysensor", isChecked) }`
10. Cargar umbral en `startSending()`: `myThreshold = preferencesManager.getSensorThreshold("mysensor")`

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
│   │   │   ├── SimpleOSCClient.kt       # Custom OSC/UDP encoder & sender
│   │   │   └── PreferencesManager.kt    # Persistencia de configuración (SharedPreferences)
│   │   └── res/layout/
│   │       └── activity_main.xml        # 8 sensor CheckBoxes, IP/port/sampling-rate EditTexts, start/stop switch
│   └── proguard-rules.pro
├── build.gradle                         # Root: Kotlin 1.9.10, Android plugin 8.2.2
└── settings.gradle                      # Single-module project: ':app'
```

## Debugging Tips

**⚠️ ANTES DE DEBUGGEAR**: Asegúrate de que la app está compilada e instalada ejecutando `build_and_deploy.bat`.

1. **Sensor not registering?** Check CheckBox state and verify `sensorManager.getDefaultSensor(TYPE)` returns non-null
2. **OSC not sending?** Check IP/port validity; review `SimpleOSCClient` logs for socket creation failures
3. **ANR or freezing?** Likely blocking network call on main thread; verify all `InetAddress.getByName()` and `socket.send()` happen in `oscHandler`
4. **High CPU/battery drain?** Reduce sampling rate or uncheck unnecessary sensors; default 200ms is reasonable
5. **Gradle build failures?** Ensure JDK 17 active; check `gradle.properties` for `jvmargs` heap size
