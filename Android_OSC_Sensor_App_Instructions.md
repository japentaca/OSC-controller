# Android OSC Sensor App Development Instructions

## Project Overview
Create an Android application that reads mobile device sensors (excluding GPS) and sends the data as OSC (Open Sound Control) messages to a specified IP address and port. The app will have configurable sampling rate with a default of 200ms.

## Prerequisites
- Android Studio (latest version)
- Android SDK (minimum API level 21 - Android 5.0)
- Kotlin programming knowledge
- Basic understanding of OSC protocol

## Step-by-Step Development Instructions

### 1. Project Setup
```
1. Open Android Studio
2. Create New Project → "Empty Activity"
3. Name: "OSCSensorController"
4. Package: com.example.oscsensorcontroller
5. Language: Kotlin
6. Minimum SDK: API 21
```

### 2. Add Dependencies (build.gradle - Module level)
```gradle
dependencies {
    implementation 'com.illposed.osc:javaosc-core:0.8'
    implementation 'androidx.core:core-ktx:1.9.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.8.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
}
```

### 3. Required Permissions (AndroidManifest.xml)
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.VIBRATE" />
```

### 4. Main Activity Layout (activity_main.xml)
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="OSC Sensor Controller"
        android:textSize="24sp"
        android:textStyle="bold"
        android:layout_gravity="center_horizontal"
        android:layout_marginBottom="24dp" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="IP Address:"
        android:textSize="16sp" />

    <EditText
        android:id="@+id/ipEditText"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="192.168.1.100"
        android:inputType="textUri" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Port:"
        android:textSize="16sp"
        android:layout_marginTop="16dp" />

    <EditText
        android:id="@+id/portEditText"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="8000"
        android:inputType="number" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Sampling Rate (ms):"
        android:textSize="16sp"
        android:layout_marginTop="16dp" />

    <EditText
        android:id="@+id/samplingRateEditText"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="200"
        android:inputType="number"
        android:text="200" />

    <CheckBox
        android:id="@+id/accelerometerCheckBox"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Accelerometer"
        android:checked="true"
        android:layout_marginTop="16dp" />

    <CheckBox
        android:id="@+id/gyroscopeCheckBox"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Gyroscope"
        android:checked="true" />

    <CheckBox
        android:id="@+id/magnetometerCheckBox"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Magnetometer"
        android:checked="true" />

    <CheckBox
        android:id="@+id/lightSensorCheckBox"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Light Sensor"
        android:checked="true" />

    <CheckBox
        android:id="@+id/proximityCheckBox"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Proximity Sensor"
        android:checked="true" />

    <CheckBox
        android:id="@+id/pressureCheckBox"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Pressure Sensor"
        android:checked="true" />

    <CheckBox
        android:id="@+id/temperatureCheckBox"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Temperature Sensor"
        android:checked="true" />

    <CheckBox
        android:id="@+id/humidityCheckBox"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Humidity Sensor"
        android:checked="true" />

    <Button
        android:id="@+id/startStopButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Start Sending"
        android:layout_marginTop="24dp" />

    <TextView
        android:id="@+id/statusTextView"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Status: Stopped"
        android:textSize="14sp"
        android:layout_marginTop="16dp" />

</LinearLayout>
```

### 5. Main Activity (MainActivity.kt)
```kotlin
package com.example.oscsensorcontroller

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.illposed.osc.OSCPortOut
import com.illposed.osc.OSCMessage
import java.net.InetAddress
import java.util.*

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var magnetometer: Sensor? = null
    private var lightSensor: Sensor? = null
    private var proximitySensor: Sensor? = null
    private var pressureSensor: Sensor? = null
    private var temperatureSensor: Sensor? = null
    private var humiditySensor: Sensor? = null

    private lateinit var ipEditText: EditText
    private lateinit var portEditText: EditText
    private lateinit var samplingRateEditText: EditText
    private lateinit var startStopButton: Button
    private lateinit var statusTextView: TextView

    private var oscPortOut: OSCPortOut? = null
    private var isSending = false
    private var samplingRate = 200L // milliseconds
    private var lastSendTime = 0L

    // Sensor data storage
    private var accelerometerData = floatArrayOf(0f, 0f, 0f)
    private var gyroscopeData = floatArrayOf(0f, 0f, 0f)
    private var magnetometerData = floatArrayOf(0f, 0f, 0f)
    private var lightData = 0f
    private var proximityData = 0f
    private var pressureData = 0f
    private var temperatureData = 0f
    private var humidityData = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        initializeSensors()
        setupButtonListeners()
    }

    private fun initializeViews() {
        ipEditText = findViewById(R.id.ipEditText)
        portEditText = findViewById(R.id.portEditText)
        samplingRateEditText = findViewById(R.id.samplingRateEditText)
        startStopButton = findViewById(R.id.startStopButton)
        statusTextView = findViewById(R.id.statusTextView)
    }

    private fun initializeSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        temperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        humiditySensor = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY)
    }

    private fun setupButtonListeners() {
        startStopButton.setOnClickListener {
            if (isSending) {
                stopSending()
            } else {
                startSending()
            }
        }
    }

    private fun startSending() {
        try {
            val ip = ipEditText.text.toString()
            val port = portEditText.text.toString().toInt()
            samplingRate = samplingRateEditText.text.toString().toLong()

            oscPortOut = OSCPortOut(InetAddress.getByName(ip), port)
            isSending = true
            startStopButton.text = "Stop Sending"
            statusTextView.text = "Status: Connected to $ip:$port"

            registerSensorListeners()
        } catch (e: Exception) {
            Toast.makeText(this, "Error connecting: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopSending() {
        isSending = false
        startStopButton.text = "Start Sending"
        statusTextView.text = "Status: Stopped"
        unregisterSensorListeners()
        oscPortOut?.close()
        oscPortOut = null
    }

    private fun registerSensorListeners() {
        val accelerometerCheckBox = findViewById<CheckBox>(R.id.accelerometerCheckBox)
        val gyroscopeCheckBox = findViewById<CheckBox>(R.id.gyroscopeCheckBox)
        val magnetometerCheckBox = findViewById<CheckBox>(R.id.magnetometerCheckBox)
        val lightSensorCheckBox = findViewById<CheckBox>(R.id.lightSensorCheckBox)
        val proximityCheckBox = findViewById<CheckBox>(R.id.proximityCheckBox)
        val pressureCheckBox = findViewById<CheckBox>(R.id.pressureCheckBox)
        val temperatureCheckBox = findViewById<CheckBox>(R.id.temperatureCheckBox)
        val humidityCheckBox = findViewById<CheckBox>(R.id.humidityCheckBox)

        if (accelerometerCheckBox.isChecked && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (gyroscopeCheckBox.isChecked && gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (magnetometerCheckBox.isChecked && magnetometer != null) {
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (lightSensorCheckBox.isChecked && lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (proximityCheckBox.isChecked && proximitySensor != null) {
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (pressureCheckBox.isChecked && pressureSensor != null) {
            sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (temperatureCheckBox.isChecked && temperatureSensor != null) {
            sensorManager.registerListener(this, temperatureSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (humidityCheckBox.isChecked && humiditySensor != null) {
            sensorManager.registerListener(this, humiditySensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun unregisterSensorListeners() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelerometerData = event.values.clone()
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroscopeData = event.values.clone()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magnetometerData = event.values.clone()
            }
            Sensor.TYPE_LIGHT -> {
                lightData = event.values[0]
            }
            Sensor.TYPE_PROXIMITY -> {
                proximityData = event.values[0]
            }
            Sensor.TYPE_PRESSURE -> {
                pressureData = event.values[0]
            }
            Sensor.TYPE_AMBIENT_TEMPERATURE -> {
                temperatureData = event.values[0]
            }
            Sensor.TYPE_RELATIVE_HUMIDITY -> {
                humidityData = event.values[0]
            }
        }

        sendOSCMessages()
    }

    private fun sendOSCMessages() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSendTime < samplingRate) {
            return
        }
        lastSendTime = currentTime

        try {
            oscPortOut?.let { osc ->
                val accelerometerCheckBox = findViewById<CheckBox>(R.id.accelerometerCheckBox)
                val gyroscopeCheckBox = findViewById<CheckBox>(R.id.gyroscopeCheckBox)
                val magnetometerCheckBox = findViewById<CheckBox>(R.id.magnetometerCheckBox)
                val lightSensorCheckBox = findViewById<CheckBox>(R.id.lightSensorCheckBox)
                val proximityCheckBox = findViewById<CheckBox>(R.id.proximityCheckBox)
                val pressureCheckBox = findViewById<CheckBox>(R.id.pressureCheckBox)
                val temperatureCheckBox = findViewById<CheckBox>(R.id.temperatureCheckBox)
                val humidityCheckBox = findViewById<CheckBox>(R.id.humidityCheckBox)

                if (accelerometerCheckBox.isChecked) {
                    val accelMessage = OSCMessage("/sensors/accelerometer", listOf(accelerometerData[0], accelerometerData[1], accelerometerData[2]))
                    osc.send(accelMessage)
                }

                if (gyroscopeCheckBox.isChecked) {
                    val gyroMessage = OSCMessage("/sensors/gyroscope", listOf(gyroscopeData[0], gyroscopeData[1], gyroscopeData[2]))
                    osc.send(gyroMessage)
                }

                if (magnetometerCheckBox.isChecked) {
                    val magMessage = OSCMessage("/sensors/magnetometer", listOf(magnetometerData[0], magnetometerData[1], magnetometerData[2]))
                    osc.send(magMessage)
                }

                if (lightSensorCheckBox.isChecked) {
                    val lightMessage = OSCMessage("/sensors/light", listOf(lightData))
                    osc.send(lightMessage)
                }

                if (proximityCheckBox.isChecked) {
                    val proximityMessage = OSCMessage("/sensors/proximity", listOf(proximityData))
                    osc.send(proximityMessage)
                }

                if (pressureCheckBox.isChecked) {
                    val pressureMessage = OSCMessage("/sensors/pressure", listOf(pressureData))
                    osc.send(pressureMessage)
                }

                if (temperatureCheckBox.isChecked) {
                    val tempMessage = OSCMessage("/sensors/temperature", listOf(temperatureData))
                    osc.send(tempMessage)
                }

                if (humidityCheckBox.isChecked) {
                    val humidityMessage = OSCMessage("/sensors/humidity", listOf(humidityData))
                    osc.send(humidityMessage)
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                statusTextView.text = "Status: Error sending data"
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Not used in this implementation
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSending()
    }
}
```

### 6. OSC Message Format
The app will send OSC messages with the following address patterns:
- `/sensors/accelerometer` - X, Y, Z values (float)
- `/sensors/gyroscope` - X, Y, Z values (float)
- `/sensors/magnetometer` - X, Y, Z values (float)
- `/sensors/light` - Light level (float)
- `/sensors/proximity` - Proximity distance (float)
- `/sensors/pressure` - Atmospheric pressure (float)
- `/sensors/temperature` - Temperature (float)
- `/sensors/humidity` - Relative humidity (float)

### 7. Testing Instructions
```
1. Install the app on an Android device
2. Open the app and enter the target IP address and port
3. Select which sensors to monitor using the checkboxes
4. Set the desired sampling rate (default: 200ms)
5. Click "Start Sending" to begin transmission
6. Use an OSC receiver (like TouchOSC, Max/MSP, Pure Data, etc.) to verify incoming data
```

### 8. Troubleshooting
```
Common Issues:
- Connection refused: Check IP address and port, ensure receiver is listening
- No sensor data: Verify device has the requested sensors
- High battery usage: Increase sampling rate or reduce number of active sensors
- App crashes on start: Check permissions in AndroidManifest.xml
```

### 9. Optional Enhancements
```
- Add data logging to file
- Implement sensor calibration
- Add graph visualization of sensor data
- Implement background service for continuous monitoring
- Add UDP broadcast option
- Implement sensor fusion algorithms
- Add data filtering/smoothing options
```

### 10. Security Considerations
```
- Validate IP address input
- Implement connection timeout
- Add option to limit data transmission to WiFi only
- Consider adding authentication for OSC messages
- Implement rate limiting to prevent network flooding
```

## Build and Deployment
1. Build the APK: Build → Build Bundle(s) / APK(s) → Build APK(s)
2. Install on device: Connect device via USB and run, or transfer APK file
3. Test with your OSC receiver application

## Notes
- The app only works when actively running (foreground)
- GPS sensor is excluded as requested
- All other available sensors are included with individual enable/disable options
- Sampling rate is configurable with 200ms default
- OSC messages are sent via UDP protocol
