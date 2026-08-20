package com.neallink.client

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class NealAccessibilityService : AccessibilityService() {
    companion object {
        private var instance: NealAccessibilityService? = null
        private var x = 200f
        private var y = 200f

        fun moveBy(dx: Int, dy: Int) {
            val svc = instance ?: return
            x += dx
            y += dy
            x = x.coerceIn(0f, svc.resources.displayMetrics.widthPixels.toFloat())
            y = y.coerceIn(0f, svc.resources.displayMetrics.heightPixels.toFloat())
            svc.overlay?.show(x, y)
        }

        fun setCursor(nx: Int, ny: Int) {
            val svc = instance ?: return
            x = nx.toFloat().coerceIn(0f, svc.resources.displayMetrics.widthPixels.toFloat())
            y = ny.toFloat().coerceIn(0f, svc.resources.displayMetrics.heightPixels.toFloat())
            svc.overlay?.show(x, y)
        }

        fun click(button: String, pressed: Boolean) {
            if (!pressed) return
            instance?.tap(x, y)
        }

        fun scroll(dx: Int, dy: Int) {
            val svc = instance ?: return
            val distanceX = dx * 40f
            val distanceY = -dy * 80f
            svc.swipe(x, y, x + distanceX, y + distanceY, 140L)
        }
    }

    internal var overlay: CursorOverlay? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlay = CursorOverlay(this)
        overlay?.show(x, y)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        overlay?.hide()
        overlay = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun tap(px: Float, py: Float) {
        val path = Path().apply { moveTo(px, py) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 1L)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }
}
