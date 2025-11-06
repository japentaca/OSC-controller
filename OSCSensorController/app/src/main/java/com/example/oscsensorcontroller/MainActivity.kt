package com.example.oscsensorcontroller

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.widget.*
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import java.net.InetAddress

class MainActivity : AppCompatActivity(), SensorEventListener {

    companion object {
        init {
            // Initialization
        }
    }

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
    private lateinit var sendSwitch: Switch
    private var oscThread: HandlerThread? = null
    private var oscHandler: Handler? = null

    private var oscClient: SimpleOSCClient? = null
    private var isSending = false
    private var samplingRate = 200L // milliseconds
    private var lastSendTime = 0L
    private var messageCount = 0L
    private var lastLogTime = 0L

    // Sensor data storage
    private var accelerometerData = floatArrayOf(0f, 0f, 0f)
    private var gyroscopeData = floatArrayOf(0f, 0f, 0f)
    private var magnetometerData = floatArrayOf(0f, 0f, 0f)
    private var lightData = 0f
    private var proximityData = 0f
    private var pressureData = 0f
    private var temperatureData = 0f
    private var humidityData = 0f

    // Previous sensor data for change detection
    private var lastAccelerometerData = floatArrayOf(0f, 0f, 0f)
    private var lastGyroscopeData = floatArrayOf(0f, 0f, 0f)
    private var lastMagnetometerData = floatArrayOf(0f, 0f, 0f)
    private var lastLightData = 0f
    private var lastProximityData = 0f
    private var lastPressureData = 0f
    private var lastTemperatureData = 0f
    private var lastHumidityData = 0f

    // Threshold for change detection (epsilon value for float comparison)
    private var sensorThreshold = 0.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set a global exception handler to catch AWT ClassNotFoundError
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            if (exception is NoClassDefFoundError && exception.message?.contains("java.awt.Color") == true) {
                android.util.Log.e("MainActivity", "Caught AWT Color error, attempting recovery", exception)
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "JavaOSC library requires compatible Android environment",
                        Toast.LENGTH_LONG
                    ).show()
                    statusTextView.text = "Status: Stopped (Library Error)"
                    sendSwitch.isChecked = false
                }
            } else {
                defaultHandler?.uncaughtException(thread, exception)
            }
        }
        
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
        sendSwitch = findViewById(R.id.sendSwitch)

        // Valores por defecto solicitados
        ipEditText.setText("192.168.0.5")
        portEditText.setText("9000")
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
        // Switch de enviar/no enviar
        sendSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startSending()
                startStopButton.text = "Stop Sending"
            } else {
                stopSending()
                startStopButton.text = "Start Sending"
            }
        }

        // El botón alterna el estado del switch
        startStopButton.setOnClickListener {
            sendSwitch.isChecked = !sendSwitch.isChecked
        }
    }

    // Helper function to detect if a 3-value sensor has changed
    private fun hasFloatArrayChanged(current: FloatArray, previous: FloatArray): Boolean {
        return current[0] != previous[0] || current[1] != previous[1] || current[2] != previous[2]
    }

    // Helper function to detect if a single-value sensor has changed
    private fun hasFloatChanged(current: Float, previous: Float): Boolean {
        return current != previous
    }

    private fun startSending() {
        val ip = ipEditText.text.toString()
        val port = portEditText.text.toString().toIntOrNull() ?: 9000
        samplingRate = samplingRateEditText.text.toString().toLongOrNull() ?: 200L

        android.util.Log.i("MainActivity", "Starting to send OSC to $ip:$port with sampling rate ${samplingRate}ms")

        // Hilo en segundo plano para operaciones de red
        oscThread = HandlerThread("OSCThread").apply { start() }
        oscHandler = Handler(oscThread!!.looper)

        statusTextView.text = "Status: Connecting to $ip:$port"

        oscHandler?.post {
            android.util.Log.d("MainActivity", "OSC thread started: ${Thread.currentThread().name}")
            try {
                val address = InetAddress.getByName(ip)
                android.util.Log.i("MainActivity", "Resolved IP address: ${address.hostAddress}")
                oscClient = SimpleOSCClient(address, port)
                oscClient?.connect()
                
                runOnUiThread {
                    isSending = true
                    startStopButton.text = "Stop Sending"
                    statusTextView.text = "Status: Connected to $ip:$port"
                    android.util.Log.i("MainActivity", "isSending set to true, registering sensor listeners")
                    registerSensorListeners()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error connecting to OSC server: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error connecting: ${e.message}", Toast.LENGTH_SHORT).show()
                    sendSwitch.isChecked = false
                    statusTextView.text = "Status: Error connecting"
                }
            }
        }
    }

    private fun stopSending() {
        isSending = false
        startStopButton.text = "Start Sending"
        statusTextView.text = "Status: Stopped"
        unregisterSensorListeners()
        oscClient?.close()
        oscClient = null
        oscHandler?.removeCallbacksAndMessages(null)
        oscThread?.quitSafely()
        oscHandler = null
        oscThread = null
    }

    private fun registerSensorListeners() {
        android.util.Log.d("MainActivity", "registerSensorListeners called")
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
            android.util.Log.d("MainActivity", "Registered accelerometer listener")
        }
        if (gyroscopeCheckBox.isChecked && gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL)
            android.util.Log.d("MainActivity", "Registered gyroscope listener")
        }
        if (magnetometerCheckBox.isChecked && magnetometer != null) {
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL)
            android.util.Log.d("MainActivity", "Registered magnetometer listener")
        }
        if (lightSensorCheckBox.isChecked && lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
            android.util.Log.d("MainActivity", "Registered light sensor listener")
        }
        if (proximityCheckBox.isChecked && proximitySensor != null) {
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
            android.util.Log.d("MainActivity", "Registered proximity listener")
        }
        if (pressureCheckBox.isChecked && pressureSensor != null) {
            sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL)
            android.util.Log.d("MainActivity", "Registered pressure listener")
        }
        if (temperatureCheckBox.isChecked && temperatureSensor != null) {
            sensorManager.registerListener(this, temperatureSensor, SensorManager.SENSOR_DELAY_NORMAL)
            android.util.Log.d("MainActivity", "Registered temperature listener")
        }
        if (humidityCheckBox.isChecked && humiditySensor != null) {
            sensorManager.registerListener(this, humiditySensor, SensorManager.SENSOR_DELAY_NORMAL)
            android.util.Log.d("MainActivity", "Registered humidity listener")
        }
    }

    private fun unregisterSensorListeners() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelerometerData = event.values.clone()
                android.util.Log.v("MainActivity", "Accelerometer event: ${accelerometerData.toList()}")
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroscopeData = event.values.clone()
                android.util.Log.v("MainActivity", "Gyroscope event: ${gyroscopeData.toList()}")
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magnetometerData = event.values.clone()
                android.util.Log.v("MainActivity", "Magnetometer event: ${magnetometerData.toList()}")
            }
            Sensor.TYPE_LIGHT -> {
                lightData = event.values[0]
                android.util.Log.v("MainActivity", "Light event: $lightData")
            }
            Sensor.TYPE_PROXIMITY -> {
                proximityData = event.values[0]
                android.util.Log.v("MainActivity", "Proximity event: $proximityData")
            }
            Sensor.TYPE_PRESSURE -> {
                pressureData = event.values[0]
                android.util.Log.v("MainActivity", "Pressure event: $pressureData")
            }
            Sensor.TYPE_AMBIENT_TEMPERATURE -> {
                temperatureData = event.values[0]
                android.util.Log.v("MainActivity", "Temperature event: $temperatureData")
            }
            Sensor.TYPE_RELATIVE_HUMIDITY -> {
                humidityData = event.values[0]
                android.util.Log.v("MainActivity", "Humidity event: $humidityData")
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

        if (!isSending || oscClient == null) {
            android.util.Log.w("MainActivity", "OSC send skipped: isSending=$isSending, oscClient=$oscClient")
            return
        }

        android.util.Log.d("MainActivity", "sendOSCMessages called on thread: ${Thread.currentThread().name}")

        // Enviar mensajes desde el hilo de OSC para evitar bloquear el hilo principal
        oscHandler?.post {
            try {
                val accelerometerCheckBox = findViewById<CheckBox>(R.id.accelerometerCheckBox)
                val gyroscopeCheckBox = findViewById<CheckBox>(R.id.gyroscopeCheckBox)
                val magnetometerCheckBox = findViewById<CheckBox>(R.id.magnetometerCheckBox)
                val lightSensorCheckBox = findViewById<CheckBox>(R.id.lightSensorCheckBox)
                val proximityCheckBox = findViewById<CheckBox>(R.id.proximityCheckBox)
                val pressureCheckBox = findViewById<CheckBox>(R.id.pressureCheckBox)
                val temperatureCheckBox = findViewById<CheckBox>(R.id.temperatureCheckBox)
                val humidityCheckBox = findViewById<CheckBox>(R.id.humidityCheckBox)

                android.util.Log.v("MainActivity", "CheckBoxes: accel=${accelerometerCheckBox.isChecked}, gyro=${gyroscopeCheckBox.isChecked}, mag=${magnetometerCheckBox.isChecked}")

                // Send only if value has changed since last read
                if (accelerometerCheckBox.isChecked && hasFloatArrayChanged(accelerometerData, lastAccelerometerData)) {
                    android.util.Log.d("MainActivity", "Sending accelerometer: ${accelerometerData.toList()}")
                    oscClient?.send("/sensors/accelerometer", listOf(accelerometerData[0], accelerometerData[1], accelerometerData[2]))
                    lastAccelerometerData = accelerometerData.clone()
                }

                if (gyroscopeCheckBox.isChecked && hasFloatArrayChanged(gyroscopeData, lastGyroscopeData)) {
                    android.util.Log.d("MainActivity", "Sending gyroscope: ${gyroscopeData.toList()}")
                    oscClient?.send("/sensors/gyroscope", listOf(gyroscopeData[0], gyroscopeData[1], gyroscopeData[2]))
                    lastGyroscopeData = gyroscopeData.clone()
                }

                if (magnetometerCheckBox.isChecked && hasFloatArrayChanged(magnetometerData, lastMagnetometerData)) {
                    android.util.Log.d("MainActivity", "Sending magnetometer: ${magnetometerData.toList()}")
                    oscClient?.send("/sensors/magnetometer", listOf(magnetometerData[0], magnetometerData[1], magnetometerData[2]))
                    lastMagnetometerData = magnetometerData.clone()
                }

                if (lightSensorCheckBox.isChecked && hasFloatChanged(lightData, lastLightData)) {
                    android.util.Log.d("MainActivity", "Sending light: $lightData")
                    oscClient?.send("/sensors/light", listOf(lightData))
                    lastLightData = lightData
                }

                if (proximityCheckBox.isChecked && hasFloatChanged(proximityData, lastProximityData)) {
                    android.util.Log.d("MainActivity", "Sending proximity: $proximityData")
                    oscClient?.send("/sensors/proximity", listOf(proximityData))
                    lastProximityData = proximityData
                }

                if (pressureCheckBox.isChecked && hasFloatChanged(pressureData, lastPressureData)) {
                    android.util.Log.d("MainActivity", "Sending pressure: $pressureData")
                    oscClient?.send("/sensors/pressure", listOf(pressureData))
                    lastPressureData = pressureData
                }

                if (temperatureCheckBox.isChecked && hasFloatChanged(temperatureData, lastTemperatureData)) {
                    android.util.Log.d("MainActivity", "Sending temperature: $temperatureData")
                    oscClient?.send("/sensors/temperature", listOf(temperatureData))
                    lastTemperatureData = temperatureData
                }

                if (humidityCheckBox.isChecked && hasFloatChanged(humidityData, lastHumidityData)) {
                    android.util.Log.d("MainActivity", "Sending humidity: $humidityData")
                    oscClient?.send("/sensors/humidity", listOf(humidityData))
                    lastHumidityData = humidityData
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error in sendOSCMessages: ${e.message}", e)
            }
        }
    }

    private fun sendSafeOSCMessage(address: String, values: List<Float>) {
        // Deprecated - using oscClient.send() directly now
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Not used in this implementation
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSending()
    }
}
