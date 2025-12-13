package com.example.oscsensorcontroller

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Simple OSC sender implementation without external library dependencies
 * This avoids the AWT class loading issue in JavaOSC library
 */
class SimpleOSCClient(private val address: InetAddress, private val port: Int) {
    
    private var socket: DatagramSocket? = null
    private var isConnected = false
    
    companion object {
        private const val TAG = "SimpleOSCClient"
    }
    
    fun connect() {
        try {
            socket = DatagramSocket()
            socket?.broadcast = true
            isConnected = true
            android.util.Log.i(TAG, "✓ Socket created successfully")
            android.util.Log.i(TAG, "✓ Connected to ${address.hostAddress}:$port")
            android.util.Log.d(TAG, "Socket details: isClosed=${socket?.isClosed}, port=${socket?.localPort}")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "✗ Failed to create socket: ${e.message}", e)
            isConnected = false
        }
    }
    
    fun send(path: String, values: List<Any>) {
        if (!isConnected || socket == null || socket?.isClosed == true) {
            android.util.Log.w(TAG, "✗ Cannot send: isConnected=$isConnected, socket=$socket, isClosed=${socket?.isClosed}")
            return
        }
        
        try {
            android.util.Log.d(TAG, "Encoding message for path=$path with ${values.size} values")
            val buffer = encodeOSCMessage(path, values)
            android.util.Log.d(TAG, "Buffer encoded successfully, size=${buffer.size} bytes")
            
            val targetHost = address.hostAddress ?: address.hostName ?: "unknown"
            android.util.Log.d(TAG, "Creating packet for $targetHost:$port")
            val packet = DatagramPacket(buffer, buffer.size, address, port)
            android.util.Log.d(TAG, "Packet created, sending...")
            socket?.send(packet)
            android.util.Log.i(TAG, "✓ OSC SENT: path=$path, values=${values.size}, bytes=${buffer.size}, target=$targetHost:$port")
        } catch (e: NullPointerException) {
            android.util.Log.e(TAG, "✗ NullPointerException in send: ${e.message}", e)
            android.util.Log.e(TAG, "Details: address=$address, socket=$socket")
            e.printStackTrace()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "✗ Exception in send: ${e.javaClass.simpleName}: ${e.message}", e)
            e.printStackTrace()
        }
    }
    
    fun close() {
        try {
            socket?.close()
            isConnected = false
            android.util.Log.i(TAG, "✓ Socket closed successfully")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "✗ Error closing socket: ${e.message}")
        }
        socket = null
    }
    
    private fun encodeOSCMessage(path: String, values: List<Any>): ByteArray {
        val output = ByteArrayOutputStream()
        
        try {
            // Encode OSC address pattern
            val pathBytes = path.toByteArray(Charsets.UTF_8)
            val pathLength = pathBytes.size + 1 // +1 for null terminator
            val pathPadded = padToMultipleOf4(pathLength)
            
            output.write(pathBytes)
            output.write(0) // null terminator
            // Add padding
            repeat(pathPadded - pathLength) {
                output.write(0)
            }
            
            android.util.Log.v(TAG, "Encoded path: '$path' (${pathLength} bytes + ${pathPadded - pathLength} padding)")
            
            // Encode type tag string
            val typeTagBuilder = StringBuilder(",")
            for (value in values) {
                when (value) {
                    is Float -> typeTagBuilder.append("f")
                    is Int -> typeTagBuilder.append("i")
                    else -> android.util.Log.w(TAG, "Unsupported type: ${value.javaClass.simpleName}")
                }
            }
            val typeTag = typeTagBuilder.toString()
            val typeTagBytes = typeTag.toByteArray(Charsets.UTF_8)
            val typeTagLength = typeTagBytes.size + 1 // +1 for null terminator
            val typeTagPadded = padToMultipleOf4(typeTagLength)
            
            output.write(typeTagBytes)
            output.write(0) // null terminator
            // Add padding
            repeat(typeTagPadded - typeTagLength) {
                output.write(0)
            }
            
            android.util.Log.v(TAG, "Encoded type tag: '$typeTag' (${typeTagLength} bytes + ${typeTagPadded - typeTagLength} padding)")
            
            // Encode values in big-endian (OSC standard)
            for (value in values) {
                when (value) {
                    is Float -> {
                        val floatBytes = ByteBuffer.allocate(4).putFloat(value).array()
                        output.write(floatBytes)
                        android.util.Log.v(TAG, "Encoded float: $value")
                    }
                    is Int -> {
                        val intBytes = ByteBuffer.allocate(4).putInt(value).array()
                        output.write(intBytes)
                        android.util.Log.v(TAG, "Encoded int: $value")
                    }
                    // TODO: Support other types if needed
                }
            }
            
            android.util.Log.i(TAG, "✓ OSC message encoded: path=$path, values=${values.size}, totalBytes=${output.size()}")
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "✗ Error encoding OSC message: ${e.message}", e)
        }
        
        return output.toByteArray()
    }
    
    private fun padToMultipleOf4(size: Int): Int {
        val remainder = size % 4
        return if (remainder == 0) size else size + (4 - remainder)
    }
}

