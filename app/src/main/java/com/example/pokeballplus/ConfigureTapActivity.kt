package com.example.pokeballplus

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ConfigureTapActivity : AppCompatActivity() {

    private var selectedButton: String? = null
    private lateinit var frame: FrameLayout
    private lateinit var buttonContainer: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        frame = FrameLayout(this)
        frame.setBackgroundColor(Color.parseColor("#AA000000"))

        setupButtonSelector()

        setContentView(frame)
    }

    private fun setupButtonSelector() {
        // Remove previous container if exists
        if (::buttonContainer.isInitialized) {
            frame.removeView(buttonContainer)
        }

        buttonContainer = FrameLayout(this)

        val btnTop = Button(this).apply {
            text = "Configure TOP button"
            setOnClickListener {
                selectedButton = "TOP"
                showTouchSelection()
            }
        }

        val btnJoystick = Button(this).apply {
            text = "Configure JOYSTICK button"
            setOnClickListener {
                selectedButton = "JOYSTICK"
                showTouchSelection()
            }
        }

        val btnCenterJoystick = Button(this).apply {
            text = "Configure JOYSTICK center"
            setOnClickListener {
                selectedButton = "JOYSTICK_CENTER"
                showTouchSelection()
            }
        }

        val btnShake = Button(this).apply {
            text = "Configure SHAKE gesture"
            setOnClickListener {
                selectedButton = "SHAKE"
                showTouchSelection()
            }
        }

        // Add buttons to container
        buttonContainer.addView(btnTop, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 200
        })

        buttonContainer.addView(btnJoystick, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 400
        })

        buttonContainer.addView(btnCenterJoystick, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 600
        })

        buttonContainer.addView(btnShake, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 800
        })

        frame.addView(buttonContainer)
    }

    private fun showTouchSelection() {
        frame.removeView(buttonContainer)
        Toast.makeText(this, "Tap on the screen to save position for $selectedButton button", Toast.LENGTH_SHORT).show()

        frame.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && selectedButton != null) {
                val rawX = event.rawX
                val rawY = event.rawY
                val localX = event.x
                val localY = event.y

                val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)
                val editor = prefs.edit()

                when (selectedButton) {
                    "TOP" -> {
                        editor.putFloat("tap_x", rawX)
                        editor.putFloat("tap_y", rawY)
                        Toast.makeText(this, "✅ Saved point for TOP button", Toast.LENGTH_SHORT).show()
                    }
                    "JOYSTICK" -> {
                        editor.putFloat("joystick_x", rawX)
                        editor.putFloat("joystick_y", rawY)
                        Toast.makeText(this, "✅ Saved point for JOYSTICK button", Toast.LENGTH_SHORT).show()
                    }
                    "JOYSTICK_CENTER" -> {
                        editor.putFloat("joystick_center_x", rawX)
                        editor.putFloat("joystick_center_y", rawY)
                        Toast.makeText(this, "✅ Joystick center saved", Toast.LENGTH_SHORT).show()
                    }
                    "SHAKE" -> {
                        editor.putFloat("shake_x", rawX)
                        editor.putFloat("shake_y", rawY)
                        Toast.makeText(this, "✅ Saved point for SHAKE gesture", Toast.LENGTH_SHORT).show()
                    }
                }

                editor.apply()
                selectedButton = null

                val marker = View(this).apply {
                    setBackgroundColor(Color.RED)
                    layoutParams = FrameLayout.LayoutParams(50, 50).apply {
                        leftMargin = (localX - 25).toInt()
                        topMargin = (localY - 25).toInt()
                    }
                }
                frame.addView(marker)

                // Return to selector after short delay
                frame.setOnTouchListener(null)
                frame.postDelayed({
                    setupButtonSelector()
                }, 800)
            }
            true
        }
    }
}
