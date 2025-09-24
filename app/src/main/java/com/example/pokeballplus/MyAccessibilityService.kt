package com.example.pokeballplus

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.accessibilityservice.GestureDescription
import android.graphics.Path

class MyAccessibilityService : AccessibilityService() {


    fun simulateTap(x: Float, y: Float) {
        Log.d("MyAccessibilityService", "Simulating tap at: ($x, $y)")

        val path = Path().apply {
            moveTo(x, y)
        }

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(
            GestureDescription.StrokeDescription(path, 0, 100)
        )

        val gesture = gestureBuilder.build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d("MyAccessibilityService", "Tap completed at ($x, $y)")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.d("MyAccessibilityService", "Tap cancelled at ($x, $y)")
            }
        }, null)
    }



    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No lo usamos, pero hay que implementarlo
    }

    override fun onInterrupt() {
        // No lo usamos
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }



    fun simulateKeyPress(keyCode: Int) {
        Log.d("MyAccessibilityService", "simulateKeyPress called with keyCode: $keyCode")
        Thread {
            try {
                val inst = android.app.Instrumentation()
                Log.d("MyAccessibilityService", "Sending key down/up for code: $keyCode")
                inst.sendKeyDownUpSync(keyCode)
                Log.d("MyAccessibilityService", "Key event sent")
            } catch (e: Exception) {
                Log.e("MyAccessibilityService", "Error sending key event", e)
            }
        }.start()
    }



    companion object {
        var instance: MyAccessibilityService? = null
    }
}
