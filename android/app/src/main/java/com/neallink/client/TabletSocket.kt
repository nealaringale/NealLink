package com.neallink.client

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object TabletSocket {
    private val client = OkHttpClient.Builder()
        .pingInterval(5, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null

    fun connect(url: String, status: (String) -> Unit) {
        socket?.cancel()
        status("Connecting…")
        val normalized = url.trim().removeSuffix("/")
        val request = Request.Builder().url(normalized).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                status("Connected")
                val hello = JSONObject()
                    .put("v", 1)
                    .put("type", "hello")
                    .put("name", "ANDROID-TABLET")
                    .put("role", "receiver")
                    .put("width", 1080)
                    .put("height", 2400)
                webSocket.send(hello.toString())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                status("Disconnected: ${t.message ?: "connection failed"}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                status("Closed: $reason")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = JSONObject(text)
                    when (msg.optString("type")) {
                        "move" -> NealAccessibilityService.moveBy(msg.optInt("dx"), msg.optInt("dy"))
                        "click" -> NealAccessibilityService.click(
                            msg.optString("button", "left"),
                            msg.optBoolean("pressed")
                        )
                        "scroll" -> NealAccessibilityService.scroll(msg.optInt("dx"), msg.optInt("dy"))
                        "cursor" -> NealAccessibilityService.setCursor(msg.optInt("x"), msg.optInt("y"))
                    }
                } catch (_: Exception) {
                    status("Invalid message received")
                }
            }
        })
    }
}
