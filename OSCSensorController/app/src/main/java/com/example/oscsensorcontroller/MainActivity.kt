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
import android.text.Editable
import android.text.TextWatcher

class MainActivity : AppCompatActivity(), SensorEventListener {

    companion object {
        init {
            // Initialization
        }
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var preferencesManager: PreferencesManager
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
    private lateinit var sendSwitch: Switch
    private lateinit var normalizeCheckBox: CheckBox
    
    // Threshold EditTexts for each sensor
    private lateinit var accelerometerThresholdEditText: EditText
    private lateinit var gyroscopeThresholdEditText: EditText
    private lateinit var magnetometerThresholdEditText: EditText
    private lateinit var lightSensorThresholdEditText: EditText
    private lateinit var proximityThresholdEditText: EditText
    private lateinit var pressureThresholdEditText: EditText
    private lateinit var temperatureThresholdEditText: EditText
    private lateinit var humidityThresholdEditText: EditText
    
    // Threshold values per sensor (0-1 range)
    private var accelerometerThreshold = 0.05f
    private var gyroscopeThreshold = 0.05f
    private var magnetometerThreshold = 0.05f
    private var lightThreshold = 0.05f
    private var proximityThreshold = 0.05f
    private var pressureThreshold = 0.05f
    private var temperatureThreshold = 0.05f
    private var humidityThreshold = 0.05f
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializar PreferencesManager
        preferencesManager = PreferencesManager(this)
        
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
                    sendSwitch.isChecked = false
                }
            } else {
                defaultHandler?.uncaughtException(thread, exception)
            }
        }
        
        setContentView(R.layout.activity_main)

        initializeViews()
        initializeSensors()
    }

    private fun initializeViews() {
        // Setup accordion toggle
        val configurationHeader = findViewById<LinearLayout>(R.id.configurationHeader)
        val configurationContent = findViewById<LinearLayout>(R.id.configurationContent)
        val configExpandIcon = findViewById<TextView>(R.id.configExpandIcon)
        
        var isConfigExpanded = true
        
        configurationHeader.setOnClickListener {
            isConfigExpanded = !isConfigExpanded
            if (isConfigExpanded) {
                configurationContent.visibility = LinearLayout.VISIBLE
                configExpandIcon.text = "▼"
            } else {
                configurationContent.visibility = LinearLayout.GONE
                configExpandIcon.text = "▶"
            }
        }
        
        ipEditText = findViewById(R.id.ipEditText)
        portEditText = findViewById(R.id.portEditText)
        samplingRateEditText = findViewById(R.id.samplingRateEditText)
        sendSwitch = findViewById(R.id.sendSwitch)
        normalizeCheckBox = findViewById(R.id.normalizeCheckBox)
        
        // Initialize threshold EditTexts
        accelerometerThresholdEditText = findViewById(R.id.accelerometerThresholdEditText)
        gyroscopeThresholdEditText = findViewById(R.id.gyroscopeThresholdEditText)
        magnetometerThresholdEditText = findViewById(R.id.magnetometerThresholdEditText)
        lightSensorThresholdEditText = findViewById(R.id.lightSensorThresholdEditText)
        proximityThresholdEditText = findViewById(R.id.proximityThresholdEditText)
        pressureThresholdEditText = findViewById(R.id.pressureThresholdEditText)
        temperatureThresholdEditText = findViewById(R.id.temperatureThresholdEditText)
        humidityThresholdEditText = findViewById(R.id.humidityThresholdEditText)

        // Cargar valores persistidos desde SharedPreferences
        ipEditText.setText(preferencesManager.getServerIP())
        portEditText.setText(preferencesManager.getServerPort().toString())
        samplingRateEditText.setText(preferencesManager.getSamplingRate().toString())
        normalizeCheckBox.isChecked = preferencesManager.getNormalizeEnabled()
        
        // Cargar umbrales persistidos
        accelerometerThresholdEditText.setText(preferencesManager.getSensorThreshold("accelerometer").toString())
        gyroscopeThresholdEditText.setText(preferencesManager.getSensorThreshold("gyroscope").toString())
        magnetometerThresholdEditText.setText(preferencesManager.getSensorThreshold("magnetometer").toString())
        lightSensorThresholdEditText.setText(preferencesManager.getSensorThreshold("light").toString())
        proximityThresholdEditText.setText(preferencesManager.getSensorThreshold("proximity").toString())
        pressureThresholdEditText.setText(preferencesManager.getSensorThreshold("pressure").toString())
        temperatureThresholdEditText.setText(preferencesManager.getSensorThreshold("temperature").toString())
        humidityThresholdEditText.setText(preferencesManager.getSensorThreshold("humidity").toString())

        // Agregar TextWatchers para guardar automáticamente cambios en configuración general
        ipEditText.addTextChangedListener(createTextWatcher { preferencesManager.saveServerIP(it) })
        portEditText.addTextChangedListener(createTextWatcher { 
            it.toIntOrNull()?.let { port -> preferencesManager.saveServerPort(port) }
        })
        samplingRateEditText.addTextChangedListener(createTextWatcher {
            it.toLongOrNull()?.let { rate -> preferencesManager.saveSamplingRate(rate) }
        })
        
        // Agregar TextWatchers para guardar automáticamente cambios en umbrales
        accelerometerThresholdEditText.addTextChangedListener(createTextWatcher {
            it.toFloatOrNull()?.let { threshold -> preferencesManager.saveSensorThreshold("accelerometer", threshold) }
        })
        gyroscopeThresholdEditText.addTextChangedListener(createTextWatcher {
            it.toFloatOrNull()?.let { threshold -> preferencesManager.saveSensorThreshold("gyroscope", threshold) }
        })
        magnetometerThresholdEditText.addTextChangedListener(createTextWatcher {
            it.toFloatOrNull()?.let { threshold -> preferencesManager.saveSensorThreshold("magnetometer", threshold) }
        })
        lightSensorThresholdEditText.addTextChangedListener(createTextWatcher {
            it.toFloatOrNull()?.let { threshold -> preferencesManager.saveSensorThreshold("light", threshold) }
        })
        proximityThresholdEditText.addTextChangedListener(createTextWatcher {
            it.toFloatOrNull()?.let { threshold -> preferencesManager.saveSensorThreshold("proximity", threshold) }
        })
        pressureThresholdEditText.addTextChangedListener(createTextWatcher {
            it.toFloatOrNull()?.let { threshold -> preferencesManager.saveSensorThreshold("pressure", threshold) }
        })
        temperatureThresholdEditText.addTextChangedListener(createTextWatcher {
            it.toFloatOrNull()?.let { threshold -> preferencesManager.saveSensorThreshold("temperature", threshold) }
        })
        humidityThresholdEditText.addTextChangedListener(createTextWatcher {
            it.toFloatOrNull()?.let { threshold -> preferencesManager.saveSensorThreshold("humidity", threshold) }
        })

        // Setup Switch listener
        sendSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startSending()
            } else {
                stopSending()
            }
        }
        
        // Setup normalize checkbox listener
        normalizeCheckBox.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.saveNormalizeEnabled(isChecked)
        }
    }

    /**
     * Helper para crear un TextWatcher que guarda valores automáticamente
     */
    private fun createTextWatcher(onTextChanged: (String) -> Unit): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onTextChanged(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        }
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
        
        // Cargar estados persistidos de los checkboxes de sensores
        val accelerometerCheckBox = findViewById<CheckBox>(R.id.accelerometerCheckBox)
        val gyroscopeCheckBox = findViewById<CheckBox>(R.id.gyroscopeCheckBox)
        val magnetometerCheckBox = findViewById<CheckBox>(R.id.magnetometerCheckBox)
        val lightSensorCheckBox = findViewById<CheckBox>(R.id.lightSensorCheckBox)
        val proximityCheckBox = findViewById<CheckBox>(R.id.proximityCheckBox)
        val pressureCheckBox = findViewById<CheckBox>(R.id.pressureCheckBox)
        val temperatureCheckBox = findViewById<CheckBox>(R.id.temperatureCheckBox)
        val humidityCheckBox = findViewById<CheckBox>(R.id.humidityCheckBox)
        
        accelerometerCheckBox.isChecked = preferencesManager.getSensorEnabled("accelerometer")
        gyroscopeCheckBox.isChecked = preferencesManager.getSensorEnabled("gyroscope")
        magnetometerCheckBox.isChecked = preferencesManager.getSensorEnabled("magnetometer")
        lightSensorCheckBox.isChecked = preferencesManager.getSensorEnabled("light")
        proximityCheckBox.isChecked = preferencesManager.getSensorEnabled("proximity")
        pressureCheckBox.isChecked = preferencesManager.getSensorEnabled("pressure")
        temperatureCheckBox.isChecked = preferencesManager.getSensorEnabled("temperature")
        humidityCheckBox.isChecked = preferencesManager.getSensorEnabled("humidity")
        
        // Agregar listeners para guardar cambios en estado de sensores
        accelerometerCheckBox.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.saveSensorEnabled("accelerometer", isChecked)
        }
        gyroscopeCheckBox.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.saveSensorEnabled("gyroscope", isChecked)
        }
        magnetometerCheckBox.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.saveSensorEnabled("magnetometer", isChecked)
        }
        lightSensorCheckBox.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.saveSensorEnabled("light", isChecked)
        }
        proximityCheckBox.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.saveSensorEnabled("proximity", isChecked)
        }
        pressureCheckBox.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.saveSensorEnabled("pressure", isChecked)
        }
        temperatureCheckBox.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.saveSensorEnabled("temperature", isChecked)
        }
        humidityCheckBox.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.saveSensorEnabled("humidity", isChecked)
        }
    }

    // Normalization ranges for different sensors (min, max)
    private val normalizationRanges = mapOf(
        "accelerometer" to Pair(-10f, 10f),
        "gyroscope" to Pair(-34.9f, 34.9f),
        "magnetometer" to Pair(-200f, 200f),
        "light" to Pair(0f, 1f),  // Special: uses logarithmic scale, not linear
        "proximity" to Pair(0f, 10f),
        "pressure" to Pair(300f, 1100f),
        "temperature" to Pair(-40f, 85f),
        "humidity" to Pair(0f, 100f)
    )

    // Normalize a single value to 0-1 range
    private fun normalizeValue(sensorType: String, value: Float): Float {
        if (sensorType == "light") {
            // Light uses logarithmic scale: 0-1 lux → 0, 1-10 lux → 0.2, 10-100 lux → 0.4, 
            // 100-1000 lux → 0.6, 1000-10000 lux → 0.8, 10000+ lux → 1
            return if (value <= 0f) {
                0f
            } else {
                val logValue = kotlin.math.log10(value.coerceAtLeast(1f))
                (logValue + 1f) / 6f  // Range: log10(1)=-0, log10(100000)=5, normalized to 0-1
            }.coerceIn(0f, 1f)
        }
        
        val range = normalizationRanges[sensorType] ?: return value
        val min = range.first
        val max = range.second
        return ((value - min) / (max - min)).coerceIn(0f, 1f)
    }

    // Normalize an array of values (for 3-axis sensors)
    private fun normalizeArray(sensorType: String, values: FloatArray): FloatArray {
        return values.map { normalizeValue(sensorType, it) }.toFloatArray()
    }

    // Check if normalized 3-value sensor has changed beyond threshold
    private fun hasNormalizedArrayChanged(current: FloatArray, previous: FloatArray, sensorType: String, threshold: Float): Boolean {
        val normalizedCurrent = normalizeArray(sensorType, current)
        val normalizedPrevious = normalizeArray(sensorType, previous)
        
        // Calculate max difference across all 3 axes
        val maxDiff = maxOf(
            kotlin.math.abs(normalizedCurrent[0] - normalizedPrevious[0]),
            kotlin.math.abs(normalizedCurrent[1] - normalizedPrevious[1]),
            kotlin.math.abs(normalizedCurrent[2] - normalizedPrevious[2])
        )
        return maxDiff > threshold
    }

    // Check if normalized single-value sensor has changed beyond threshold
    private fun hasNormalizedFloatChanged(current: Float, previous: Float, sensorType: String, threshold: Float): Boolean {
        val normalizedCurrent = normalizeValue(sensorType, current)
        val normalizedPrevious = normalizeValue(sensorType, previous)
        val diff = kotlin.math.abs(normalizedCurrent - normalizedPrevious)
        return diff > threshold
    }

    // Helper function to detect if a 3-value sensor has changed (without threshold)
    private fun hasFloatArrayChanged(current: FloatArray, previous: FloatArray): Boolean {
        return current[0] != previous[0] || current[1] != previous[1] || current[2] != previous[2]
    }

    // Helper function to detect if a single-value sensor has changed (without threshold)
    private fun hasFloatChanged(current: Float, previous: Float): Boolean {
        return current != previous
    }

    private fun startSending() {
        val ip = ipEditText.text.toString()
        val port = portEditText.text.toString().toIntOrNull() ?: 9000
        samplingRate = samplingRateEditText.text.toString().toLongOrNull() ?: 200L
        
        // Load thresholds from EditTexts
        accelerometerThreshold = accelerometerThresholdEditText.text.toString().toFloatOrNull() ?: 0.05f
        gyroscopeThreshold = gyroscopeThresholdEditText.text.toString().toFloatOrNull() ?: 0.05f
        magnetometerThreshold = magnetometerThresholdEditText.text.toString().toFloatOrNull() ?: 0.05f
        lightThreshold = lightSensorThresholdEditText.text.toString().toFloatOrNull() ?: 0.05f
        proximityThreshold = proximityThresholdEditText.text.toString().toFloatOrNull() ?: 0.05f
        pressureThreshold = pressureThresholdEditText.text.toString().toFloatOrNull() ?: 0.05f
        temperatureThreshold = temperatureThresholdEditText.text.toString().toFloatOrNull() ?: 0.05f
        humidityThreshold = humidityThresholdEditText.text.toString().toFloatOrNull() ?: 0.05f

        android.util.Log.i("MainActivity", "Starting to send OSC to $ip:$port with sampling rate ${samplingRate}ms")

        // Hilo en segundo plano para operaciones de red
        oscThread = HandlerThread("OSCThread").apply { start() }
        oscHandler = Handler(oscThread!!.looper)

        oscHandler?.post {
            android.util.Log.d("MainActivity", "OSC thread started: ${Thread.currentThread().name}")
            try {
                val address = InetAddress.getByName(ip)
                android.util.Log.i("MainActivity", "Resolved IP address: ${address.hostAddress}")
                oscClient = SimpleOSCClient(address, port)
                oscClient?.connect()
                
                runOnUiThread {
                    isSending = true
                    android.util.Log.i("MainActivity", "isSending set to true, registering sensor listeners")
                    registerSensorListeners()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error connecting to OSC server: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error connecting: ${e.message}", Toast.LENGTH_SHORT).show()
                    sendSwitch.isChecked = false
                }
            }
        }
    }

    private fun stopSending() {
        isSending = false
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
                val normalize = normalizeCheckBox.isChecked

                android.util.Log.v("MainActivity", "CheckBoxes: accel=${accelerometerCheckBox.isChecked}, gyro=${gyroscopeCheckBox.isChecked}, mag=${magnetometerCheckBox.isChecked}, normalize=$normalize")

                // Send only if value has changed since last read (with threshold for normalized values)
                if (accelerometerCheckBox.isChecked) {
                    val hasChanged = if (normalize) {
                        hasNormalizedArrayChanged(accelerometerData, lastAccelerometerData, "accelerometer", accelerometerThreshold)
                    } else {
                        hasFloatArrayChanged(accelerometerData, lastAccelerometerData)
                    }
                    if (hasChanged) {
                        val valuestoSend = if (normalize) normalizeArray("accelerometer", accelerometerData) else accelerometerData
                        android.util.Log.d("MainActivity", "Sending accelerometer: ${valuestoSend.toList()}")
                        oscClient?.send("/sensors/accelerometer", listOf(valuestoSend[0], valuestoSend[1], valuestoSend[2]))
                        lastAccelerometerData = accelerometerData.clone()
                    }
                }

                if (gyroscopeCheckBox.isChecked) {
                    val hasChanged = if (normalize) {
                        hasNormalizedArrayChanged(gyroscopeData, lastGyroscopeData, "gyroscope", gyroscopeThreshold)
                    } else {
                        hasFloatArrayChanged(gyroscopeData, lastGyroscopeData)
                    }
                    if (hasChanged) {
                        val valuestoSend = if (normalize) normalizeArray("gyroscope", gyroscopeData) else gyroscopeData
                        android.util.Log.d("MainActivity", "Sending gyroscope: ${valuestoSend.toList()}")
                        oscClient?.send("/sensors/gyroscope", listOf(valuestoSend[0], valuestoSend[1], valuestoSend[2]))
                        lastGyroscopeData = gyroscopeData.clone()
                    }
                }

                if (magnetometerCheckBox.isChecked) {
                    val hasChanged = if (normalize) {
                        hasNormalizedArrayChanged(magnetometerData, lastMagnetometerData, "magnetometer", magnetometerThreshold)
                    } else {
                        hasFloatArrayChanged(magnetometerData, lastMagnetometerData)
                    }
                    if (hasChanged) {
                        val valuestoSend = if (normalize) normalizeArray("magnetometer", magnetometerData) else magnetometerData
                        android.util.Log.d("MainActivity", "Sending magnetometer: ${valuestoSend.toList()}")
                        oscClient?.send("/sensors/magnetometer", listOf(valuestoSend[0], valuestoSend[1], valuestoSend[2]))
                        lastMagnetometerData = magnetometerData.clone()
                    }
                }

                if (lightSensorCheckBox.isChecked) {
                    val hasChanged = if (normalize) {
                        hasNormalizedFloatChanged(lightData, lastLightData, "light", lightThreshold)
                    } else {
                        hasFloatChanged(lightData, lastLightData)
                    }
                    if (hasChanged) {
                        val valuetoSend = if (normalize) normalizeValue("light", lightData) else lightData
                        android.util.Log.d("MainActivity", "Sending light: $valuetoSend")
                        oscClient?.send("/sensors/light", listOf(valuetoSend))
                        lastLightData = lightData
                    }
                }

                if (proximityCheckBox.isChecked) {
                    val hasChanged = if (normalize) {
                        hasNormalizedFloatChanged(proximityData, lastProximityData, "proximity", proximityThreshold)
                    } else {
                        hasFloatChanged(proximityData, lastProximityData)
                    }
                    if (hasChanged) {
                        val valuetoSend = if (normalize) normalizeValue("proximity", proximityData) else proximityData
                        android.util.Log.d("MainActivity", "Sending proximity: $valuetoSend")
                        oscClient?.send("/sensors/proximity", listOf(valuetoSend))
                        lastProximityData = proximityData
                    }
                }

                if (pressureCheckBox.isChecked) {
                    val hasChanged = if (normalize) {
                        hasNormalizedFloatChanged(pressureData, lastPressureData, "pressure", pressureThreshold)
                    } else {
                        hasFloatChanged(pressureData, lastPressureData)
                    }
                    if (hasChanged) {
                        val valuetoSend = if (normalize) normalizeValue("pressure", pressureData) else pressureData
                        android.util.Log.d("MainActivity", "Sending pressure: $valuetoSend")
                        oscClient?.send("/sensors/pressure", listOf(valuetoSend))
                        lastPressureData = pressureData
                    }
                }

                if (temperatureCheckBox.isChecked) {
                    val hasChanged = if (normalize) {
                        hasNormalizedFloatChanged(temperatureData, lastTemperatureData, "temperature", temperatureThreshold)
                    } else {
                        hasFloatChanged(temperatureData, lastTemperatureData)
                    }
                    if (hasChanged) {
                        val valuetoSend = if (normalize) normalizeValue("temperature", temperatureData) else temperatureData
                        android.util.Log.d("MainActivity", "Sending temperature: $valuetoSend")
                        oscClient?.send("/sensors/temperature", listOf(valuetoSend))
                        lastTemperatureData = temperatureData
                    }
                }

                if (humidityCheckBox.isChecked) {
                    val hasChanged = if (normalize) {
                        hasNormalizedFloatChanged(humidityData, lastHumidityData, "humidity", humidityThreshold)
                    } else {
                        hasFloatChanged(humidityData, lastHumidityData)
                    }
                    if (hasChanged) {
                        val valuetoSend = if (normalize) normalizeValue("humidity", humidityData) else humidityData
                        android.util.Log.d("MainActivity", "Sending humidity: $valuetoSend")
                        oscClient?.send("/sensors/humidity", listOf(valuetoSend))
                        lastHumidityData = humidityData
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error in sendOSCMessages: ${e.message}", e)
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
