package com.neallink.client

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var statusDot: TextView
    private lateinit var accessibilityStatus: TextView
    private lateinit var accessibilityButton: MaterialButton
    private lateinit var connectButton: MaterialButton
    private lateinit var serverUrl: EditText

    private val prefs by lazy { getSharedPreferences("neallink", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        statusDot = findViewById(R.id.statusDot)
        accessibilityStatus = findViewById(R.id.accessibilityStatus)
        accessibilityButton = findViewById(R.id.accessibility)
        serverUrl = findViewById(R.id.serverUrl)
        connectButton = findViewById(R.id.connect)

        serverUrl.setText(prefs.getString("server_url", "ws://10.84.241.248:24891"))
        updateStatus(getString(R.string.status_ready), connected = false)
        updateAccessibilityState()

        connectButton.setOnClickListener {
            val url = serverUrl.text.toString().trim()
            if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
                updateStatus("Invalid WebSocket URL", connected = false, error = true)
                return@setOnClickListener
            }

            prefs.edit().putString("server_url", url).apply()
            connectButton.isEnabled = false
            connectButton.text = "Connecting…"
            updateStatus("Connecting…", connected = false)

            TabletSocket.connect(url) { text ->
                runOnUiThread {
                    val connected = text == "Connected"
                    val failed = text.startsWith("Disconnected") || text.startsWith("Closed")
                    updateStatus(text, connected, error = failed)
                    connectButton.isEnabled = true
                    connectButton.text = getString(R.string.connect)
                }
            }
        }

        accessibilityButton.setOnClickListener {
            if (isAccessibilityEnabled()) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::accessibilityStatus.isInitialized) {
            updateAccessibilityState()
        }
    }

    private fun updateStatus(text: String, connected: Boolean, error: Boolean = false) {
        status.text = text
        val color = when {
            connected -> Color.parseColor("#4ADE80")
            error -> Color.parseColor("#FB7185")
            else -> Color.parseColor("#FBBF24")
        }
        statusDot.setTextColor(color)
        status.text = text
        status.setTextColor(if (connected) Color.parseColor("#4ADE80") else Color.parseColor("#F4F1FF"))
    }

    private fun isAccessibilityEnabled(): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: ""
            enabledServices.contains(packageName, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun updateAccessibilityState() {
        if (isAccessibilityEnabled()) {
            accessibilityStatus.text = getString(R.string.accessibility_enabled)
            accessibilityStatus.setTextColor(Color.parseColor("#4ADE80"))
            accessibilityButton.text = "Manage Accessibility"
        } else {
            accessibilityStatus.text = getString(R.string.accessibility_disabled)
            accessibilityStatus.setTextColor(Color.parseColor("#FBBF24"))
            accessibilityButton.text = "Open App Settings"
        }
    }
}
