package com.example.oscsensorcontroller

import android.content.Context
import android.content.SharedPreferences

/**
 * Manager para persistir y recuperar configuración de la app (IP, puerto, umbrales, etc.)
 * Utiliza SharedPreferences para almacenar datos localmente en el dispositivo Android
 */
class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "osc_controller_config",
        Context.MODE_PRIVATE
    )

    companion object {
        // Keys para configuración general
        const val KEY_SERVER_IP = "server_ip"
        const val KEY_SERVER_PORT = "server_port"
        const val KEY_SAMPLING_RATE = "sampling_rate"

        // Keys para estados de sensores (habilitado/deshabilitado)
        const val KEY_ACCELEROMETER_ENABLED = "accelerometer_enabled"
        const val KEY_GYROSCOPE_ENABLED = "gyroscope_enabled"
        const val KEY_MAGNETOMETER_ENABLED = "magnetometer_enabled"
        const val KEY_LIGHT_ENABLED = "light_enabled"
        const val KEY_PROXIMITY_ENABLED = "proximity_enabled"
        const val KEY_PRESSURE_ENABLED = "pressure_enabled"
        const val KEY_TEMPERATURE_ENABLED = "temperature_enabled"
        const val KEY_HUMIDITY_ENABLED = "humidity_enabled"

        // Keys para umbrales de cada sensor
        const val KEY_ACCELEROMETER_THRESHOLD = "accelerometer_threshold"
        const val KEY_GYROSCOPE_THRESHOLD = "gyroscope_threshold"
        const val KEY_MAGNETOMETER_THRESHOLD = "magnetometer_threshold"
        const val KEY_LIGHT_THRESHOLD = "light_threshold"
        const val KEY_PROXIMITY_THRESHOLD = "proximity_threshold"
        const val KEY_PRESSURE_THRESHOLD = "pressure_threshold"
        const val KEY_TEMPERATURE_THRESHOLD = "temperature_threshold"
        const val KEY_HUMIDITY_THRESHOLD = "humidity_threshold"

        // Keys para normalización
        const val KEY_NORMALIZE_ENABLED = "normalize_enabled"

        // Valores por defecto
        const val DEFAULT_SERVER_IP = "192.168.0.5"
        const val DEFAULT_SERVER_PORT = 9000
        const val DEFAULT_SAMPLING_RATE = 200L
        const val DEFAULT_THRESHOLD = 0.05f
        const val DEFAULT_SENSOR_ENABLED = true
        const val DEFAULT_NORMALIZE_ENABLED = false
    }

    // ==================== Configuración General ====================

    fun getServerIP(): String = prefs.getString(KEY_SERVER_IP, DEFAULT_SERVER_IP) ?: DEFAULT_SERVER_IP
    fun saveServerIP(ip: String) = prefs.edit().putString(KEY_SERVER_IP, ip).apply()

    fun getServerPort(): Int = prefs.getInt(KEY_SERVER_PORT, DEFAULT_SERVER_PORT)
    fun saveServerPort(port: Int) = prefs.edit().putInt(KEY_SERVER_PORT, port).apply()

    fun getSamplingRate(): Long = prefs.getLong(KEY_SAMPLING_RATE, DEFAULT_SAMPLING_RATE)
    fun saveSamplingRate(rate: Long) = prefs.edit().putLong(KEY_SAMPLING_RATE, rate).apply()

    fun getNormalizeEnabled(): Boolean = prefs.getBoolean(KEY_NORMALIZE_ENABLED, DEFAULT_NORMALIZE_ENABLED)
    fun saveNormalizeEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_NORMALIZE_ENABLED, enabled).apply()

    // ==================== Estados de Sensores ====================

    fun getSensorEnabled(sensorName: String): Boolean = when (sensorName) {
        "accelerometer" -> prefs.getBoolean(KEY_ACCELEROMETER_ENABLED, DEFAULT_SENSOR_ENABLED)
        "gyroscope" -> prefs.getBoolean(KEY_GYROSCOPE_ENABLED, DEFAULT_SENSOR_ENABLED)
        "magnetometer" -> prefs.getBoolean(KEY_MAGNETOMETER_ENABLED, DEFAULT_SENSOR_ENABLED)
        "light" -> prefs.getBoolean(KEY_LIGHT_ENABLED, DEFAULT_SENSOR_ENABLED)
        "proximity" -> prefs.getBoolean(KEY_PROXIMITY_ENABLED, DEFAULT_SENSOR_ENABLED)
        "pressure" -> prefs.getBoolean(KEY_PRESSURE_ENABLED, DEFAULT_SENSOR_ENABLED)
        "temperature" -> prefs.getBoolean(KEY_TEMPERATURE_ENABLED, DEFAULT_SENSOR_ENABLED)
        "humidity" -> prefs.getBoolean(KEY_HUMIDITY_ENABLED, DEFAULT_SENSOR_ENABLED)
        else -> DEFAULT_SENSOR_ENABLED
    }

    fun saveSensorEnabled(sensorName: String, enabled: Boolean) {
        val key = when (sensorName) {
            "accelerometer" -> KEY_ACCELEROMETER_ENABLED
            "gyroscope" -> KEY_GYROSCOPE_ENABLED
            "magnetometer" -> KEY_MAGNETOMETER_ENABLED
            "light" -> KEY_LIGHT_ENABLED
            "proximity" -> KEY_PROXIMITY_ENABLED
            "pressure" -> KEY_PRESSURE_ENABLED
            "temperature" -> KEY_TEMPERATURE_ENABLED
            "humidity" -> KEY_HUMIDITY_ENABLED
            else -> return
        }
        prefs.edit().putBoolean(key, enabled).apply()
    }

    // ==================== Umbrales de Sensores ====================

    fun getSensorThreshold(sensorName: String): Float = when (sensorName) {
        "accelerometer" -> prefs.getFloat(KEY_ACCELEROMETER_THRESHOLD, DEFAULT_THRESHOLD)
        "gyroscope" -> prefs.getFloat(KEY_GYROSCOPE_THRESHOLD, DEFAULT_THRESHOLD)
        "magnetometer" -> prefs.getFloat(KEY_MAGNETOMETER_THRESHOLD, DEFAULT_THRESHOLD)
        "light" -> prefs.getFloat(KEY_LIGHT_THRESHOLD, DEFAULT_THRESHOLD)
        "proximity" -> prefs.getFloat(KEY_PROXIMITY_THRESHOLD, DEFAULT_THRESHOLD)
        "pressure" -> prefs.getFloat(KEY_PRESSURE_THRESHOLD, DEFAULT_THRESHOLD)
        "temperature" -> prefs.getFloat(KEY_TEMPERATURE_THRESHOLD, DEFAULT_THRESHOLD)
        "humidity" -> prefs.getFloat(KEY_HUMIDITY_THRESHOLD, DEFAULT_THRESHOLD)
        else -> DEFAULT_THRESHOLD
    }

    fun saveSensorThreshold(sensorName: String, threshold: Float) {
        val key = when (sensorName) {
            "accelerometer" -> KEY_ACCELEROMETER_THRESHOLD
            "gyroscope" -> KEY_GYROSCOPE_THRESHOLD
            "magnetometer" -> KEY_MAGNETOMETER_THRESHOLD
            "light" -> KEY_LIGHT_THRESHOLD
            "proximity" -> KEY_PROXIMITY_THRESHOLD
            "pressure" -> KEY_PRESSURE_THRESHOLD
            "temperature" -> KEY_TEMPERATURE_THRESHOLD
            "humidity" -> KEY_HUMIDITY_THRESHOLD
            else -> return
        }
        prefs.edit().putFloat(key, threshold).apply()
    }

    // ==================== Métodos Utilitarios ====================

    /**
     * Obtiene todos los umbrales de todos los sensores como mapa
     */
    fun getAllThresholds(): Map<String, Float> = mapOf(
        "accelerometer" to getSensorThreshold("accelerometer"),
        "gyroscope" to getSensorThreshold("gyroscope"),
        "magnetometer" to getSensorThreshold("magnetometer"),
        "light" to getSensorThreshold("light"),
        "proximity" to getSensorThreshold("proximity"),
        "pressure" to getSensorThreshold("pressure"),
        "temperature" to getSensorThreshold("temperature"),
        "humidity" to getSensorThreshold("humidity")
    )

    /**
     * Limpia todas las preferencias (usa con cuidado)
     */
    fun clearAll() = prefs.edit().clear().apply()

    /**
     * Restablece todos los valores a sus defaults
     */
    fun resetToDefaults() {
        clearAll()
        // Los valores se cargarán con los defaults en la próxima llamada
    }
}
