package com.smartclock.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.smartclock.util.TimeFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmAlertOverlay @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var overlayView: View? = null

    fun canShow(): Boolean = Settings.canDrawOverlays(context)

    fun show(
        title: String,
        onDismiss: () -> Unit,
        onSnooze: (Int) -> Unit
    ) {
        hide()
        if (!canShow()) return

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#CC0B1220"))
            isClickable = true
            isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(28f)
                setColor(Color.parseColor("#F4F7FB"))
            }
            setPadding(dpInt(24f), dpInt(28f), dpInt(24f), dpInt(24f))
        }

        val timeView = TextView(context).apply {
            text = TimeFormat.hhmm(System.currentTimeMillis())
            setTextColor(Color.parseColor("#0F172A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        val titleView = TextView(context).apply {
            text = title
            setTextColor(Color.parseColor("#1E293B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dpInt(12f), 0, 0)
        }

        val subtitleView = TextView(context).apply {
            text = "\u95f9\u949f\u65f6\u95f4\u5230"
            setTextColor(Color.parseColor("#475569"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setPadding(0, dpInt(8f), 0, 0)
        }

        val closeButton = Button(context).apply {
            text = "\u5173\u95ed"
            setOnClickListener { onDismiss() }
        }

        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dpInt(16f), 0, 0)
        }

        listOf(5, 10, 30).forEach { minutes ->
            actionRow.addView(
                Button(context).apply {
                    text = "${minutes} \u5206\u949f"
                    setOnClickListener { onSnooze(minutes) }
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    if (minutes != 5) marginStart = dpInt(8f)
                }
            )
        }

        card.addView(
            timeView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        card.addView(
            titleView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        card.addView(
            subtitleView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        card.addView(
            closeButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpInt(24f)
            }
        )
        card.addView(
            actionRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply {
                marginStart = dpInt(20f)
                marginEnd = dpInt(20f)
            }
        )

        runCatching {
            windowManager.addView(root, buildLayoutParams())
            overlayView = root
        }
    }

    fun hide() {
        overlayView?.let { attachedView ->
            runCatching { windowManager.removeView(attachedView) }
        }
        overlayView = null
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics
        )

    private fun dpInt(value: Float): Int = dp(value).toInt()
}
