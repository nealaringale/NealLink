package com.neallink.client

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var statusDot: View
    private lateinit var accessibilityStatus: TextView
    private lateinit var accessibilityButton: MaterialButton
    private lateinit var connectButton: MaterialButton
    private lateinit var serverUrl: EditText
    private lateinit var cursorView: View
    private lateinit var rootFrame: FrameLayout

    private val prefs by lazy { getSharedPreferences("neallink", Context.MODE_PRIVATE) }

    companion object {
        @Volatile
        private var instance: MainActivity? = null

        fun updateNetworkCursor(dx: Int, dy: Int) {
            instance?.runOnUiThread {
                it.moveCursor(dx, dy)
            }
        }

        fun setNetworkCursor(x: Int, y: Int) {
            instance?.runOnUiThread {
                it.placeCursor(x, y)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        setContentView(R.layout.activity_main)

        rootFrame = findViewById(R.id.rootFrame)
        cursorView = findViewById(R.id.cursorView)
        status = findViewById(R.id.status)
        statusDot = findViewById(R.id.statusDot)
        accessibilityStatus = findViewById(R.id.accessibilityStatus)
        accessibilityButton = findViewById(R.id.accessibility)
        serverUrl = findViewById(R.id.serverUrl)
        connectButton = findViewById(R.id.connect)

        serverUrl.setText(prefs.getString("server_url", "ws://192.168.31.170:24891"))
        updateStatus(getString(R.string.status_ready), false)
        cursorView.visibility = View.GONE

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

            TabletSocket.connect(
                url = url,
                status = { text ->
                    runOnUiThread {
                        val connected = text == "Connected"
                        val failed = text.startsWith("Disconnected") || text.startsWith("Closed")
                        updateStatus(text, connected, error = failed)
                        connectButton.isEnabled = true
                        connectButton.text = getString(R.string.connect)
                        if (!connected) cursorView.visibility = View.GONE
                    }
                }
            )
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

        updateAccessibilityState()
    }

    override fun onResume() {
        super.onResume()
        instance = this
        if (::accessibilityStatus.isInitialized) {
            updateAccessibilityState()
        }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun moveCursor(dx: Int, dy: Int) {
        val lp = cursorView.layoutParams as FrameLayout.LayoutParams
        val maxX = (rootFrame.width - cursorView.width).coerceAtLeast(0)
        val maxY = (rootFrame.height - cursorView.height).coerceAtLeast(0)
        lp.leftMargin = (lp.leftMargin + dx).coerceIn(0, maxX)
        lp.topMargin = (lp.topMargin + dy).coerceIn(0, maxY)
        cursorView.layoutParams = lp
        cursorView.visibility = View.VISIBLE
        cursorView.bringToFront()
    }

    private fun placeCursor(x: Int, y: Int) {
        val lp = cursorView.layoutParams as FrameLayout.LayoutParams
        val maxX = (rootFrame.width - cursorView.width).coerceAtLeast(0)
        val maxY = (rootFrame.height - cursorView.height).coerceAtLeast(0)
        lp.leftMargin = x.coerceIn(0, maxX)
        lp.topMargin = y.coerceIn(0, maxY)
        cursorView.layoutParams = lp
        cursorView.visibility = View.VISIBLE
        cursorView.bringToFront()
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

    private fun isAccessibilityEnabled(): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
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
