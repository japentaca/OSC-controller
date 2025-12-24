package com.example.oscsensorcontroller

/**
 * Interface for sending sensor data via different protocols (OSC, Bluetooth, etc.)
 */
interface DataSender {
    /**
     * Send data values to a specific path/address
     * @param path The address path (e.g., "/sensors/accelerometer")
     * @param values List of values to send (typically Floats or Ints)
     */
    fun send(path: String, values: List<Any>)
    
    /**
     * Connect or initialize the sender
     */
    fun connect()
    
    /**
     * Close or release resources
     */
    fun close()
    
    /**
     * Check if currently connected/ready to send
     */
    fun isConnected(): Boolean
}
