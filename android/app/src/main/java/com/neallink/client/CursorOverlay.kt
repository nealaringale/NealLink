package com.neallink.client

import android.content.Context
import android.graphics.Color
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
    }
    private var shown = false

    fun show(x: Float, y: Float) {
        if (!shown) {
            val params = WindowManager.LayoutParams(
                26, 26,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START }
            wm.addView(cursor, params)
            shown = true
        }
        val lp = cursor.layoutParams as WindowManager.LayoutParams
        lp.x = x.toInt() - 13
        lp.y = y.toInt() - 13
        wm.updateViewLayout(cursor, lp)
    }

    fun hide() {
        if (shown) {
            wm.removeView(cursor)
            shown = false
        }
    }
}
