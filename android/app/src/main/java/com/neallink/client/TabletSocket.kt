package com.neallink.client

import android.content.res.Resources
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

                val metrics = Resources.getSystem().displayMetrics
                val hello = JSONObject()
                    .put("v", 1)
                    .put("type", "hello")
                    .put("name", "ANDROID-TABLET")
                    .put("role", "receiver")
                    .put("width", metrics.widthPixels)
                    .put("height", metrics.heightPixels)

                webSocket.send(hello.toString())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                NealAccessibilityService.stopTabletMode()
                status("Disconnected: ${t.message ?: "connection failed"}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                NealAccessibilityService.stopTabletMode()
                status("Closed: $reason")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = JSONObject(text)
                    when (msg.optString("type")) {
                        "handoff" -> {
                            NealAccessibilityService.startTabletMode(
                                msg.optInt("x", 8),
                                msg.optInt("y", 1200),
                            )
                            status("Tablet control active — move to LEFT edge to return")
                        }

                        "move" -> NealAccessibilityService.moveBy(
                            msg.optInt("dx"),
                            msg.optInt("dy"),
                        )

                        "click" -> NealAccessibilityService.click(
                            msg.optString("button", "left"),
                            msg.optBoolean("pressed"),
                        )

                        "scroll" -> NealAccessibilityService.scroll(
                            msg.optInt("dx"),
                            msg.optInt("dy"),
                        )

                        "host_mode" -> {
                            NealAccessibilityService.stopTabletMode()
                            status("Connected")
                        }
                    }
                } catch (_: Exception) {
                    status("Invalid message received")
                }
            }
        })
    }

    fun returnToHost() {
        socket?.send(
            JSONObject()
                .put("v", 1)
                .put("type", "return_host")
                .toString()
        )
    }
}
