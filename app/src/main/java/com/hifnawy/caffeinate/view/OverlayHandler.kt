package com.hifnawy.caffeinate.view

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.sp
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.controller.LocaleChangeReceiver
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toLocalizedFormattedTime
import timber.log.Timber as Log

/**
 * Handles the display and management of an overlay within the application.
 *
 * Rewritten with Jetpack Compose: the overlay content is now a [ComposeView]
 * instead of an inflated XML layout. The [WindowManager] mechanism is unchanged,
 * so behaviour parity with the previous implementation is preserved.
 *
 * @param context [Context] The context used for accessing system services and resources.
 *
 * @property overlayText [String] The text displayed in the overlay.
 */
class OverlayHandler(private val context: Context) {

    /**
     * The ComposeView used for the overlay content.
     *
     * This property is lazy because the overlay is not always visible. It is only
     * initialized when the overlay is first shown.
     */
    private val overlayView by lazy { ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    } }

    /**
     * A lazy delegate that provides a reference to the [LocaleChangeReceiver] instance that handles Locale changes.
     */
    private val localeChangeReceiver by lazy { LocaleChangeReceiver(context.applicationContext as CaffeinateApplication, ::onLocaleChanged) }

    /**
     * The window manager used to display the overlay.
     */
    private val windowManager by lazy { context.getSystemService(Context.WINDOW_SERVICE) as WindowManager }

    /**
     * The layout flags for the overlay.
     */
    @Suppress("DEPRECATION")
    private val layoutFlags by lazy {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else                                           -> WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    /**
     * The layout parameters for the overlay.
     */
    private val layoutParams by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlags,
            WindowManager.LayoutParams.FLAG_BLUR_BEHIND or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
    }

    /**
     * A flag indicating whether the overlay is currently visible.
     */
    private var isOverlayVisible = false

    /**
     * The text displayed in the overlay.
     */
    var overlayText: String = ""
        set(value) {
            Log.d("Setting overlay text to $value, isRTL: ${CaffeinateApplication.isRTL}...")
            field = value
            if (isOverlayVisible) {
                overlayView.setContent {
                    OverlayContent(text = value, context = context)
                }
            }
        }

    /**
     * The alpha value of the overlay.
     */
    var alpha: Float
        get() = overlayView.alpha
        set(value) {
            Log.d("changing overlay alpha to $value...")
            overlayView.alpha = value
        }

    /**
     * Shows the overlay.
     */
    fun showOverlay(initialText: String? = null) {
        if (!isOverlayVisible) {
            Log.d("Showing overlay...")

            layoutParams.run {
                gravity = Gravity.TOP or when {
                    CaffeinateApplication.isRTL -> Gravity.START
                    else                        -> Gravity.END
                }
                x = 30
                y = 0
            }

            initialText?.let { overlayText = it }

            overlayView.setContent {
                OverlayContent(text = overlayText, context = context)
            }

            windowManager.addView(overlayView, layoutParams)

            localeChangeReceiver.isRegistered = true

            isOverlayVisible = true

            Log.d("Overlay shown.")
        }
    }

    /**
     * Hides the overlay view.
     */
    fun hideOverlay() {
        if (isOverlayVisible) {
            Log.d("Hiding overlay...")

            windowManager.removeViewImmediate(overlayView)

            localeChangeReceiver.isRegistered = false

            isOverlayVisible = false

            Log.d("Overlay hidden.")
        }
    }

    /**
     * Called when the locale has changed.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun onLocaleChanged(context: Context, intent: Intent) {
        Log.d("App locale changed from system settings! Apply new Locale...")

        layoutParams.gravity = Gravity.TOP or when {
            CaffeinateApplication.isRTL -> Gravity.START
            else                        -> Gravity.END
        }

        windowManager.updateViewLayout(overlayView, layoutParams)

        CaffeinateApplication.applicationLocale.run { Log.d("Locale changed to $displayName ($language)") }
    }

    /**
     * Compose content for the overlay.
     */
    @Composable
    private fun OverlayContent(text: String, context: Context) {
        val ctx = LocalContext.current
        Text(
            text = text,
            color = MaterialTheme.colorScheme.primary,
            fontSize = when (CaffeinateApplication.isRTL) {
                true -> ctx.resources.getDimension(com.hifnawy.caffeinate.R.dimen.overlayTextSizeRTL).sp
                false -> ctx.resources.getDimension(com.hifnawy.caffeinate.R.dimen.overlayTextSizeLTR).sp
            },
            modifier = Modifier.fillMaxSize(),
            style = MaterialTheme.typography.titleLarge
        )
    }
}
