package com.neallink.client

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class CursorOverlay(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val cursor = View(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
            setStroke(3, Color.BLACK)
        }
        elevation = 100f
    }
    private var shown = false

    fun show(x: Float, y: Float) {
        if (!shown) {
            val params = WindowManager.LayoutParams(
                30,
                30,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }
            try {
                wm.addView(cursor, params)
                shown = true
            } catch (_: Exception) {
                return
            }
        }

        try {
            val lp = cursor.layoutParams as WindowManager.LayoutParams
            lp.x = x.toInt() - 15
            lp.y = y.toInt() - 15
            wm.updateViewLayout(cursor, lp)
        } catch (_: Exception) {
            shown = false
        }
    }

    fun hide() {
        if (!shown) return
        try {
            wm.removeView(cursor)
        } catch (_: Exception) {
            // Service teardown can race with overlay removal.
        } finally {
            shown = false
        }
    }
}
