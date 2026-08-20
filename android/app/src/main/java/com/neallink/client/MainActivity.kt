package com.neallink.client

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var statusDot: View
    private lateinit var accessibilityStatus: TextView
    private lateinit var connectButton: MaterialButton
    private lateinit var serverUrl: android.widget.EditText

    private val prefs by lazy { getSharedPreferences("neallink", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        statusDot = findViewById(R.id.statusDot)
        accessibilityStatus = findViewById(R.id.accessibilityStatus)
        serverUrl = findViewById(R.id.serverUrl)
        connectButton = findViewById(R.id.connect)
        val accessibility = findViewById<MaterialButton>(R.id.accessibility)

        serverUrl.setText(prefs.getString("server_url", "ws://192.168.31.170:24891"))

        connectButton.setOnClickListener {
            val url = serverUrl.text.toString().trim()
            if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
                updateStatus("Invalid WebSocket URL", false, error = true)
                return@setOnClickListener
            }

            prefs.edit().putString("server_url", url).apply()
            connectButton.isEnabled = false
            connectButton.text = "Connecting…"
            updateStatus("Connecting…", false)

            TabletSocket.connect(url) { text ->
                runOnUiThread {
                    val connected = text == "Connected"
                    updateStatus(text, connected, error = text.startsWith("Disconnected") || text.startsWith("Closed"))
                    connectButton.isEnabled = true
                    connectButton.text = getString(if (connected) R.string.connect else R.string.connect)
                }
            }
        }

        accessibility.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        updateAccessibilityState()
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
        statusDot.background.setTint(color)
        status.setTextColor(if (connected) Color.parseColor("#4ADE80") else Color.parseColor("#F4F1FF"))
    }

    private fun updateAccessibilityState() {
        val enabled = try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            enabledServices.contains(packageName, ignoreCase = true)
        } catch (_: Exception) {
            false
        }

        if (enabled) {
            accessibilityStatus.text = getString(R.string.accessibility_enabled)
            accessibilityStatus.setTextColor(Color.parseColor("#4ADE80"))
        } else {
            accessibilityStatus.text = getString(R.string.accessibility_disabled)
            accessibilityStatus.setTextColor(Color.parseColor("#FBBF24"))
        }
    }
}
