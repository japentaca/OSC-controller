package com.example.oscsensorcontroller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class PianoTouchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface PianoTouchListener {
        fun onNoteOn(pointerId: Int, relativeX: Float, relativeY: Float, note: Int)
        fun onNoteOff(pointerId: Int)
        fun onTouchMove(pointerId: Int, relativeX: Float, relativeY: Float, note: Int)
    }

    var listener: PianoTouchListener? = null
    var isPitchBendEnabled = false
    
    // Properties to be set by Activity
    var firstNote = 48 // C3 by default
        set(value) {
            field = value
            invalidate()
        }

    var visibleOctaveCount = 2
        set(value) {
            field = value.coerceIn(1, 3)
            if (width > 0) {
                calculateLayout(width, height)
                invalidate()
            }
        }

    // Geometry
    private var whiteKeyWidth = 0f
    private var blackKeyWidth = 0f
    private var blackKeyHeight = 0f
    private val blackKeyHeightRatio = 0.60f
    private val blackKeyWidthRatio = 0.6f

    // Standard piano pattern (staring from C)
    // 0=White, 1=Black. 
    // C, C#, D, D#, E, F, F#, G, G#, A, A#, B
    private val notePattern = intArrayOf(0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 1, 0)
    
    // Calculated based on screen size
    private var numWhiteKeys = 14 // Default 2 octaves
    private val rect = android.graphics.RectF()


    // Paints
    private val whiteKeyPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val whiteKeyBorderPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val blackKeyPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val activeKeyPaint = Paint().apply {
        color = Color.argb(100, 255, 0, 0) // Red highlight
        style = Paint.Style.FILL
    }
    private val feedbackCirclePaint = Paint().apply {
        color = Color.argb(150, 255, 0, 0) 
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    // Touch State
    // Map pointer ID to current Note
    private val activePointers = mutableMapOf<Int, Int>() 
    private val activePointerCoords = mutableMapOf<Int, PointF>() 

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0) {
            calculateLayout(w, h)
        }
    }

    private fun calculateLayout(w: Int, h: Int) {
        numWhiteKeys = visibleOctaveCount * 7 + 1
        whiteKeyWidth = w.toFloat() / numWhiteKeys
        
        blackKeyWidth = whiteKeyWidth * blackKeyWidthRatio
        blackKeyHeight = h * blackKeyHeightRatio
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 1. Draw White Keys
        for (i in 0 until numWhiteKeys) {
            val left = i * whiteKeyWidth
            val right = left + whiteKeyWidth
            rect.set(left, 0f, right, height.toFloat())
            
            // 1a. Draw Background (White)
            canvas.drawRect(rect, whiteKeyPaint)

            // 1b. Highlight if active
            val note = getNoteForWhiteKeyIndex(i)
            if (isNoteActive(note)) {
                canvas.drawRect(rect, activeKeyPaint)
            }
            
            // 1c. Draw Border
            canvas.drawRect(rect, whiteKeyBorderPaint)
        }

        // 2. Draw Black Keys
        // We iterate through white keys and draw black keys to their right if needed
        for (i in 0 until numWhiteKeys - 1) { // Don't draw after last key if it's cut off, usually check pattern
            // Current white key note relative to C
            val currentWhiteNoteWithinOctave = (i % 7) // 0..6 (C, D, E, F, G, A, B)
            
            // Check if there is a black key AFTER this white key
            // C(0)->C#(yes), D(1)->D#(yes), E(2)->F(no), F(3)->F#(yes), G(4)->G#(yes), A(5)->A#(yes), B(6)->C(no)
            if (hasBlackKeyAfter(currentWhiteNoteWithinOctave)) {
                val center = (i + 1) * whiteKeyWidth
                val left = center - (blackKeyWidth / 2)
                val right = center + (blackKeyWidth / 2)
                
                rect.set(left, 0f, right, blackKeyHeight)
                
                val note = getNoteForBlackKeyAfterWhiteIndex(i)
                canvas.drawRect(rect, blackKeyPaint)
                if (isNoteActive(note)) {
                    canvas.drawRect(rect, activeKeyPaint)
                }
            }
        }
        
        // 3. Draw Touch Feedback
        for ((_, point) in activePointerCoords) {
            canvas.drawCircle(point.x, point.y, 40f, activeKeyPaint) // Fill
            canvas.drawCircle(point.x, point.y, 40f, feedbackCirclePaint) // Border
        }
    }
    
    // Helpers for Note Mapping
    // Assumption: firstNote maps to the first drawn key which we assume is a 'C' type for simplicity of drawing loop
    // BUT 'firstNote' might not be C.
    // To properly support "any" start note, we need to know the offset.
    // For now, let's assuming drawing starts at a 'C' (index 0) visually, and we map 'firstNote' to that.
    // If the user scrolls octaves, we just change 'firstNote'.
    // Since we usually jump by octaves, firstNote will be C.
    
    private fun getNoteForWhiteKeyIndex(index: Int): Int {
        // Map visual white key index to MIDI note
        // 7 white keys per octave.
        val octaveOffset = index / 7
        val noteInOctaveIndex = index % 7
        
        // Major scale intervals: 0, 2, 4, 5, 7, 9, 11 (C D E F G A B) relative to C
        val whiteKeyOffsets = intArrayOf(0, 2, 4, 5, 7, 9, 11)
        
        val relativeNote = whiteKeyOffsets[noteInOctaveIndex]
        return firstNote + (octaveOffset * 12) + relativeNote
    }
    
    private fun getNoteForBlackKeyAfterWhiteIndex(whiteIndex: Int): Int {
        // The black key is immediately after the white key note
        return getNoteForWhiteKeyIndex(whiteIndex) + 1
    }

    private fun hasBlackKeyAfter(whiteIndexInOctave: Int): Boolean {
        // Indices in octave: 0=C, 1=D, 2=E, 3=F, 4=G, 5=A, 6=B
        // Black keys after: C(yes), D(yes), E(no), F(yes), G(yes), A(yes), B(no)
        return !(whiteIndexInOctave == 2 || whiteIndexInOctave == 6)
    }

    private fun isNoteActive(note: Int): Boolean {
        return activePointers.containsValue(note)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                handleTouch(id, event.getX(index), event.getY(index), isDown = true)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    handleTouch(event.getPointerId(i), event.getX(i), event.getY(i), isDown = true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                handleRelease(id)
            }
            MotionEvent.ACTION_CANCEL -> {
                // Release all
                for (id in activePointers.keys.toList()) {
                    handleRelease(id)
                }
            }
        }
        invalidate()
        return true
    }

    private fun handleTouch(pointerId: Int, x: Float, y: Float, isDown: Boolean) {
        // Update visual feedback position
        if (isDown) {
            activePointerCoords[pointerId] = PointF(x, y)
        } else {
            activePointerCoords.remove(pointerId)
        }
    
        val note = getNoteAtXY(x, y)
        
        // If the note changed for this pointer, send Note Off for old, Note On for new
        val previousNote = activePointers[pointerId]
        
        // Logic Branch: Pitch Bend Mode vs Standard Mode
        if (isPitchBendEnabled && previousNote != null) {
             // LOCKED MODE: Do NOT change the note.
             // Just update move parameters for the ORIGINAL note.
             listener?.onTouchMove(pointerId, x / width, y / height, previousNote)
        } else {
            // STANDARD MODE: Glissando allowed.
            if (previousNote != note) {
                if (previousNote != null) {
                    listener?.onNoteOff(pointerId) 
                }
                
                if (note != -1) {
                    activePointers[pointerId] = note
                    listener?.onNoteOn(pointerId, x / width, y / height, note) 
                } else {
                    activePointers.remove(pointerId)
                }
            } else {
                // Same note, just update generic move parameters (pitch bend etc)
                if (note != -1) {
                    listener?.onTouchMove(pointerId, x / width, y / height, note)
                }
            }
        }
    }
    
    private fun handleRelease(pointerId: Int) {
        if (activePointers.containsKey(pointerId)) {
            listener?.onNoteOff(pointerId)
            activePointers.remove(pointerId)
        }
        activePointerCoords.remove(pointerId)
    }

    private fun getNoteAtXY(x: Float, y: Float): Int {
        if (x < 0 || x > width || y < 0 || y > height) return -1
        
        // 1. Check Black Keys First (they are on top)
        if (y <= blackKeyHeight) {
            // Black keys are centered on boundary of white keys.
            // visual center = (i + 1) * whiteKeyWidth
            // Check ranges
             for (i in 0 until numWhiteKeys - 1) {
                val whiteNoteInOctave = i % 7
                if (hasBlackKeyAfter(whiteNoteInOctave)) {
                    val center = (i + 1) * whiteKeyWidth
                    val halfBlack = blackKeyWidth / 2
                    if (x >= center - halfBlack && x <= center + halfBlack) {
                         return getNoteForBlackKeyAfterWhiteIndex(i)
                    }
                }
            }
        }
        
        // 2. Check White Keys
        val whiteIndex = (x / whiteKeyWidth).toInt()
        if (whiteIndex in 0 until numWhiteKeys) {
            return getNoteForWhiteKeyIndex(whiteIndex)
        }
        
        return -1
    }
}
