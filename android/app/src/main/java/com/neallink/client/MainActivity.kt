package com.neallink.client

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val status = findViewById<TextView>(R.id.status)
        val url = findViewById<EditText>(R.id.serverUrl)
        val connect = findViewById<Button>(R.id.connect)
        val accessibility = findViewById<Button>(R.id.accessibility)

        url.setText("ws://192.168.31.170:24891")
        connect.setOnClickListener {
            TabletSocket.connect(url.text.toString()) { text ->
                runOnUiThread { status.text = "NealLink Tablet\n$text" }
            }
        }
        accessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}
