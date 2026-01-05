package com.example.oscsensorcontroller

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import java.util.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Implementation of DataSender that acts as a BLE Peripheral
 */
@SuppressLint("MissingPermission") // Permissions should be checked before calling these methods
class BlePeripheralSender(private val context: Context) : DataSender {

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    
    private val registeredDevices = mutableSetOf<BluetoothDevice>()
    private var isAdvertising = false
    private var isServiceAdded = false
    
    // UUIDs
    private val SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    
    // Map path to Characteristic UUID
    private val characteristicMap = mapOf(
        "/sensors/accelerometer" to UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"),
        "/sensors/gyroscope" to UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb"),
        "/sensors/magnetometer" to UUID.fromString("0000ffe3-0000-1000-8000-00805f9b34fb"),
        "/sensors/light" to UUID.fromString("0000ffe4-0000-1000-8000-00805f9b34fb"),
        "/sensors/proximity" to UUID.fromString("0000ffe5-0000-1000-8000-00805f9b34fb"),
        "/sensors/pressure" to UUID.fromString("0000ffe6-0000-1000-8000-00805f9b34fb"),
        "/sensors/temperature" to UUID.fromString("0000ffe7-0000-1000-8000-00805f9b34fb"),
        "/sensors/humidity" to UUID.fromString("0000ffe8-0000-1000-8000-00805f9b34fb"),
        "/note_on" to UUID.fromString("0000ffe9-0000-1000-8000-00805f9b34fb"),
        "/note_off" to UUID.fromString("0000ffea-0000-1000-8000-00805f9b34fb"),
        "/pitch_bend" to UUID.fromString("0000ffeb-0000-1000-8000-00805f9b34fb"),
        "/cc/1" to UUID.fromString("0000ffec-0000-1000-8000-00805f9b34fb")
    )

    companion object {
        private const val TAG = "BlePeripheralSender"
    }

    override fun connect() {
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.e(TAG, "Bluetooth not enabled")
            return
        }

        // Setup GATT Server
        openGattServer()
        
        // Advertising will start after service is added (see onServiceAdded callback)
    }
    
    private fun openGattServer() {
        if (gattServer != null) return
        
        gattServer = bluetoothManager?.openGattServer(context, gattServerCallback) ?: return
        
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        
        // Add characteristics
        for ((_, uuid) in characteristicMap) {
            val characteristic = BluetoothGattCharacteristic(
                uuid,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
            // Add Client Characteristic Configuration Descriptor (CCCD) for notifications
            val clientConfig = BluetoothGattDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
            characteristic.addDescriptor(clientConfig)
            
            service.addCharacteristic(characteristic)
        }
        
        gattServer?.addService(service)
        Log.i(TAG, "Adding GATT service with ${service.characteristics.size} characteristics...")
        // Service will be ready when onServiceAdded callback fires
    }

    private fun startAdvertising() {
        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (bluetoothLeAdvertiser == null) {
            Log.e(TAG, "BLE Advertising not supported")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true) // Show friendly name in device picker
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
            
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true) // Also in scan response
            .build()

        bluetoothLeAdvertiser?.startAdvertising(settings, data, scanResponse, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i(TAG, "BLE Advertising started")
            isAdvertising = true
             android.os.Handler(android.os.Looper.getMainLooper()).post {
                 android.widget.Toast.makeText(context, "BLE Adv Started", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE Advertising failed: $errorCode")
            isAdvertising = false
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "BLE Advertising Failed: Error $errorCode", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Service added successfully: ${service.uuid}")
                isServiceAdded = true
                // Delay to ensure service is fully ready before advertising
                // Testing showed console.log delays made it work - using 500ms to be safe
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    startAdvertising()
                }, 500) // 500ms delay
            } else {
                Log.e(TAG, "Failed to add service: $status")
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, "Failed to start BLE: Error $status", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
        
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Device connected: ${device.address}")
                registeredDevices.add(device)
                 android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, "Client Connected: ${device.address}", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Device disconnected: ${device.address}")
                registeredDevices.remove(device)
                 android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, "Client Disconnected", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.value)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            if (descriptor.uuid == UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")) {
                if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
                    Log.i(TAG, "Notifications enabled for ${descriptor.characteristic.uuid}")
                    registeredDevices.add(device)
                } else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value)) {
                    Log.i(TAG, "Notifications disabled for ${descriptor.characteristic.uuid}")
                    // Don't remove device immediately as it might be listening to other characteristics
                    // Simplified logic: we just track connected devices globally 
                }
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }
    }

    override fun send(path: String, values: List<Any>) {
        if (registeredDevices.isEmpty()) return
        
        val characteristicUUID = characteristicMap[path]
        if (characteristicUUID == null) {
            Log.w(TAG, "No characteristic mapping for path: $path")
            return
        }

        val service = gattServer?.getService(SERVICE_UUID)
        val characteristic = service?.getCharacteristic(characteristicUUID)
        
        if (characteristic != null) {
            val bytes = encodeValues(values)
            characteristic.value = bytes
            
            for (device in registeredDevices) {
                gattServer?.notifyCharacteristicChanged(device, characteristic, false)
            }
        }
    }
    
    private fun encodeValues(values: List<Any>): ByteArray {
        // Assume all floats or mix (OSC style usually float for sensors)
        // We pack 3 floats for Accel/Gyro/Mag, 1 float for others
        // Allocating enough space: values.size * 4 bytes
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN) // Web Bluetooth is often Little Endian friendly
        
        for (value in values) {
            when (value) {
                is Float -> buffer.putFloat(value)
                is Int -> buffer.putFloat(value.toFloat()) // Convert int to float for consistency
                else -> buffer.putFloat(0f)
            }
        }
        return buffer.array()
    }

    override fun isConnected(): Boolean {
        return isAdvertising // Simplified status
    }

    override fun close() {
        if (isAdvertising) {
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            isAdvertising = false
        }
        gattServer?.close()
        gattServer = null
        registeredDevices.clear()
        Log.i(TAG, "BLE Peripheral closed")
    }
}
