package com.hifnawy.caffeinate.ui

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.databinding.OverlayBinding
import timber.log.Timber as Log

/**
 * Handles the display and management of an overlay within the application.
 *
 * This class is responsible for displaying an overlay on the screen with the ability to update
 * its text content. It manages the visibility and ensures the overlay appears correctly
 * depending on the Android version and locale settings.
 *
 * @param context [Context] The context used for accessing system services and resources.
 *
 * @property overlayText [String] The text displayed in the overlay.
 *
 * @throws IllegalStateException if the overlay is not properly initialized.
 *
 * @see CaffeinateApplication
 * @see WindowManager
 * @see LayoutInflater
 */
class OverlayHandler(private val context: Context) {

    /**
     * The binding for the overlay layout.
     *
     * This property is lazy because the overlay is not always visible. It is only
     * initialized when the overlay is first shown.
     *
     * @return [OverlayBinding] The binding for the overlay layout.
     */
    private val overlayBinding by lazy { OverlayBinding.inflate(LayoutInflater.from(context)) }

    /**
     * The window manager used to display the overlay.
     *
     * This property is lazy because the window manager is not always used. It is only
     * initialized when the overlay is first shown.
     *
     * @return [WindowManager] The window manager used to display the overlay.
     */
    private val windowManager by lazy { context.getSystemService(Context.WINDOW_SERVICE) as WindowManager }

    /**
     * The layout flags for the overlay.
     *
     * The layout flags control the behavior of the overlay in terms of its visibility and interaction.
     * The flags are initialized lazily because the overlay is not always visible. It is only initialized
     * when the overlay is first shown.
     *
     * @return [Int] The layout flags for the overlay.
     */
    @Suppress("DEPRECATION")
    private val layoutFlags by lazy {
        when {
            // APPLICATION_OVERLAY FOR ANDROID 26+ AS THE PREVIOUS VERSION RAISES ERRORS
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            // FOR PREVIOUS VERSIONS USE TYPE_PHONE AS THE NEW VERSION IS NOT SUPPORTED
            else                                           -> WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    /**
     * The layout parameters for the overlay.
     *
     * The layout parameters control the size and position of the overlay in terms of its visibility and interaction.
     * The layout parameters are initialized lazily because the overlay is not always visible. It is only initialized
     * when the overlay is first shown.
     *
     * @return [WindowManager.LayoutParams] The layout parameters for the overlay.
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
     *
     * This flag is used to determine whether the overlay should be removed or not.
     * When the overlay is visible, this flag is set to `true` and when the overlay
     * is no longer visible, this flag is set to `false`.
     *
     * This flag is used in the [showOverlay] and [hideOverlay] functions to
     * determine the correct behavior.
     *
     * @return [Boolean] `true` if the overlay is currently visible, `false` otherwise.
     */
    private var isOverlayVisible = false

    /**
     * The text displayed in the overlay.
     *
     * This property is used to set the text of the overlay when the overlay is first
     * shown. It is also used to get the current text of the overlay when the overlay
     * is currently visible.
     *
     * @return [String] The current text of the overlay.
     */
    var overlayText: String
        get() = overlayBinding.remaining.text.toString()
        set(value) {
            Log.d("Setting overlay text to $value...")
            overlayBinding.remaining.text = value
        }

    /**
     * The alpha value of the overlay.
     *
     * This property is used to set the transparency of the overlay. A value of 0.0f
     * means the overlay is completely transparent, and a value of 1.0f means the overlay
     * is completely opaque.
     *
     * @return [Float] The alpha value of the overlay.
     */
    var alpha: Float
        get() = layoutParams.alpha
        set(value) {
            // layoutParams.alpha = value
            Log.d("changing overlay alpha to $value...")
            overlayBinding.root.alpha = value
            // windowManager.updateViewLayout(overlayBinding.root, layoutParams)
        }

    /**
     * Shows the overlay.
     *
     * This function is used to show the overlay after it has been initialized.
     * It displays the overlay on the screen with Gravity.TOP | Gravity.START or
     * Gravity.TOP | Gravity.END depending on the current locale.
     *
     * @throws [WindowManager.BadTokenException] if the window token is invalid.
     * @throws [WindowManager.InvalidDisplayException] if the display is invalid.
     *
     * @see hideOverlay
     */
    fun showOverlay() {
        layoutParams.gravity = Gravity.TOP or when {
            CaffeinateApplication.isRTL -> Gravity.START
            else                        -> Gravity.END
        }
        layoutParams.x = 30
        layoutParams.y = 0

        if (!isOverlayVisible) {
            Log.d("Showing overlay...")
            windowManager.addView(overlayBinding.root, layoutParams)
            isOverlayVisible = true
            Log.d("Overlay shown.")
        }
    }

    /**
     * Hides the overlay view.
     *
     * This method removes the overlay view from the window manager, effectively hiding it from the user.
     *
     * @throws [WindowManager.BadTokenException] if the window token is invalid.
     * @throws [WindowManager.InvalidDisplayException] if the display is invalid.
     *
     * @see showOverlay
     */
    fun hideOverlay() {
        if (isOverlayVisible) {
            Log.d("Hiding overlay...")
            windowManager.removeViewImmediate(overlayBinding.root)
            isOverlayVisible = false
            Log.d("Overlay hidden.")
        }
    }
}