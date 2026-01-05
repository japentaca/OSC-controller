package com.example.oscsensorcontroller

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.InetAddress
import kotlin.math.abs
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.content.Intent
import android.net.Uri
import android.provider.Settings

class PianoActivity : AppCompatActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private var oscClient: SimpleOSCClient? = null
    private var oscThread: HandlerThread? = null
    private var oscHandler: Handler? = null

    private var bleSender: BlePeripheralSender? = null


    private var currentOctave = 3 // Standard middle C octave
    private var baseNote = 48 // C3

    private var visibleOctaveCount = 2 // Default
    private var baseVelocity = 30 // Default base velocity
    
    // View reference
    private lateinit var touchView: PianoTouchView

    private var sendPitchBend = true
    private var sendCC1 = true

    private lateinit var octaveLabel: TextView

    private lateinit var octaveCountLabel: TextView
    private lateinit var velLabel: TextView

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_piano)

        preferencesManager = PreferencesManager(this)

        touchView = findViewById(R.id.touchView) // Initialize view reference
        octaveLabel = findViewById(R.id.octaveLabel)

        octaveCountLabel = findViewById(R.id.octaveCountLabel)
        velLabel = findViewById(R.id.velLabel)
        
        // Initial setup
        updateOctaveDisplay() // This will set baseNote and update view
        updateOctaveCountDisplay()
        updateVelocityDisplay()

        setupControls()
        setupThreading()
        
        // Initialize communication based on global preference
        if (preferencesManager.isBleMode()) {
            if (hasBluetoothPermissions()) {
                startBleSender()
            } else {
                requestBluetoothPermissions()
            }
        } else {
            setupOSC()
        }
        
        setupTouchListener()
        
        // Init visual state
        touchView.isPitchBendEnabled = true
        touchView.visibleOctaveCount = visibleOctaveCount
    }

    private fun setupControls() {
        // Base Octave Controls
        findViewById<Button>(R.id.octaveUpButton).setOnClickListener {
            if (currentOctave < 8) {
                currentOctave++
                updateOctaveDisplay()
            }
        }
        
        findViewById<Button>(R.id.octaveDownButton).setOnClickListener {
            if (currentOctave > 0) {
                currentOctave--
                updateOctaveDisplay()
            }
        }

        // Visible Octave Count Controls
        findViewById<Button>(R.id.octaveCountUpButton).setOnClickListener {
            if (visibleOctaveCount < 3) {
                visibleOctaveCount++
                updateOctaveCountDisplay()
            }
        }

        findViewById<Button>(R.id.octaveCountDownButton).setOnClickListener {
            if (visibleOctaveCount > 1) {
                visibleOctaveCount--
                updateOctaveCountDisplay()
            }
        }

        findViewById<CheckBox>(R.id.pitchCheckBox).setOnCheckedChangeListener { _, isChecked ->
            sendPitchBend = isChecked
            touchView.isPitchBendEnabled = isChecked
            // Reset if turned off
            if (!isChecked) sendPitchBend(0f)
        }

        findViewById<CheckBox>(R.id.cc1CheckBox).setOnCheckedChangeListener { _, isChecked ->
            sendCC1 = isChecked
        }

        // Base Velocity Controls
        findViewById<Button>(R.id.velUpButton).setOnClickListener {
            if (baseVelocity < 127) {
                baseVelocity++
                updateVelocityDisplay()
            }
        }

        findViewById<Button>(R.id.velDownButton).setOnClickListener {
            if (baseVelocity > 0) {
                baseVelocity--
                updateVelocityDisplay()
            }
        }

        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
        }
    }

    private fun updateOctaveDisplay() {
        octaveLabel.text = "C$currentOctave"
        // MIDI note for C$currentOctave. C0 is 12, C3 is 48.
        baseNote = (currentOctave + 1) * 12
        
        // Update view
        touchView.firstNote = baseNote
    }

    private fun updateOctaveCountDisplay() {
        octaveCountLabel.text = "${visibleOctaveCount} Oct"
        touchView.visibleOctaveCount = visibleOctaveCount
    }

    private fun updateVelocityDisplay() {
        velLabel.text = "Vel: $baseVelocity"
    }

    private fun setupThreading() {
        oscThread = HandlerThread("PianoWorkerThread").apply { start() }
        oscHandler = Handler(oscThread!!.looper)
    }

    private fun setupOSC() {
        val ip = preferencesManager.getServerIP()
        val port = preferencesManager.getServerPort()

        // Thread is now initialized in setupThreading()

        oscHandler?.post {
            try {
                val address = InetAddress.getByName(ip)
                oscClient = SimpleOSCClient(address, port)
                oscClient?.connect()
                runOnUiThread {
                    Toast.makeText(this, "Connected to $ip:$port", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Connection Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupTouchListener() {
        touchView.listener = object : PianoTouchView.PianoTouchListener {
            override fun onNoteOn(pointerId: Int, relativeX: Float, relativeY: Float, note: Int) {
                // Determine velocity based on Y position (0-127) if desired, or fixed
                // Logic: Top (0.0) -> 127, Bottom (1.0) -> 0 is common for expressive strips
                // But for pure keys, usually velocity is fixed or based on "how hard" (which we don't have without force touch)
                // Let's keep the Y-axis velocity expression as it adds playability
                
                // Calculate velocity based on Y position (0-127)
                // Matching CC1 logic: Top (relativeY=0) -> 127, Bottom (relativeY=1) -> 0
                
                var adjustedY = relativeY
                if (isBlackKey(note)) {
                     // Black keys are only ~60% of the screen height. 
                     // We need to map 0..0.6 to 0..1 to get full velocity range.
                     // 0.6 is the blackKeyHeightRatio from PianoTouchView
                     val blackKeyHeight = 0.6f 
                     adjustedY = (relativeY / blackKeyHeight).coerceIn(0f, 1f)
                }

                val touchVelocity = ((1f - adjustedY) * 127f).coerceIn(0f, 127f)
                val finalVelocity = (touchVelocity + baseVelocity).coerceIn(1f, 127f)

                sendNoteOn(note.toFloat(), finalVelocity)
                
                // Track active note for this pointer
                activePointerNotes[pointerId] = note
                pointerStartPositions[pointerId] = relativeX 
            }

            override fun onTouchMove(pointerId: Int, relativeX: Float, relativeY: Float, note: Int) {
                 // Pitch Bend (Horizontal Slide)
                 if (sendPitchBend) {
                     // Calculate bend based on distance from START position of this touch
                     val startX = pointerStartPositions[pointerId] ?: relativeX
                     val deltaX = relativeX - startX
                     
                     // visual bend range: +/- 0.5 key width? 
                     // relativeX is 0..1 globally.
                     // A key width approx 1/14th of screen (0.07).
                     // Let's say bending 1 key width is full bend.
                     val keyWidthRelative = 1.0f / 14.0f // Approx
                     val bendFullRange = keyWidthRelative // Shift of 1 key width = full bend
                     
                     val bendValue = (deltaX / bendFullRange).coerceIn(-1f, 1f)
                     
                     // Only send if significant change? 
                     // For now just stream it.
                     sendPitchBend(bendValue)
                 }

                 // CC1 (Vertical Slide)
                 if (sendCC1) {
                     val ccValue = (1f - relativeY).coerceIn(0f, 1f)
                     sendCC1(ccValue)
                 }
            }

            override fun onNoteOff(pointerId: Int) {
                val note = activePointerNotes[pointerId]
                if (note != null) {
                    sendNoteOff(note.toFloat())
                    activePointerNotes.remove(pointerId)
                    pointerStartPositions.remove(pointerId)
                }
                
                // If no pointers left, reset bend?
                if (activePointerNotes.isEmpty() && sendPitchBend) {
                    sendPitchBend(0f)
                }
            }
        }
    }
    
    private val activePointerNotes = mutableMapOf<Int, Int>() // pointerId -> Note
    private val pointerStartPositions = mutableMapOf<Int, Float>() // pointerId -> relativeX


    private fun sendNoteOn(note: Float, velocity: Float) {
        oscHandler?.post {
            oscClient?.send("/note_on", listOf(note, velocity))
            bleSender?.send("/note_on", listOf(note, velocity))
        }
    }

    private fun sendNoteOff(note: Float) {
        oscHandler?.post {
            oscClient?.send("/note_off", listOf(note, 0f))
            bleSender?.send("/note_off", listOf(note, 0f))
        }
    }

    private fun sendPitchBend(value: Float) {
        oscHandler?.post {
            oscClient?.send("/pitch_bend", listOf(value))
            bleSender?.send("/pitch_bend", listOf(value))
        }
    }

    private fun sendCC1(value: Float) {
        oscHandler?.post {
            oscClient?.send("/cc/1", listOf(value))
            bleSender?.send("/cc/1", listOf(value))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        oscClient?.close()
        oscThread?.quitSafely()
        stopBleSender()
    }

    private fun startBleSender() {
        if (bleSender == null) {
            bleSender = BlePeripheralSender(this)
            bleSender?.connect()
            Toast.makeText(this, "BLE Sender Started", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopBleSender() {
        bleSender?.close()
        bleSender = null
    }

    private fun hasBluetoothPermissions(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun requestBluetoothPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val permissionsToRequest = arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
            ActivityCompat.requestPermissions(this, permissionsToRequest, 102)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 102) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startBleSender()
            } else {
                Toast.makeText(this, "Bluetooth permissions required for BLE", Toast.LENGTH_SHORT).show()
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH_ADVERTISE)) {
                     // Permanently denied
                     // openAppSettings() // Optional: ask user to go to settings
                }
            }
        }
    }

    private fun isBlackKey(note: Int): Boolean {
        val noteInOctave = note % 12
        // Black keys: 1(C#), 3(D#), 6(F#), 8(G#), 10(A#)
        return when (noteInOctave) {
            1, 3, 6, 8, 10 -> true
            else -> false
        }
    }
}
