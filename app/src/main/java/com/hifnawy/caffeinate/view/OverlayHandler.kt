package com.hifnawy.caffeinate.view

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.annotation.AttrRes
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.R
import com.google.android.material.R as materialR
import com.hifnawy.caffeinate.databinding.OverlayBinding
import com.hifnawy.caffeinate.controller.LocaleChangeReceiver
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
     * A lazy delegate that provides a reference to the [LocaleChangeReceiver] instance that handles Locale changes.
     *
     * This delegate is used to register and unregister a [LocaleChangeReceiver] instance with the system,
     * allowing the service to respond to changes in the system locale.
     *
     * @return [LocaleChangeReceiver] the instance handling Locale changes.
     *
     * @see LocaleChangeReceiver
     */
    private val localeChangeReceiver by lazy { LocaleChangeReceiver(context.applicationContext as CaffeinateApplication, ::onLocaleChanged) }

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
     * A property that gets/sets the text size of the overlay in pixels.
     *
     * This property is used to set the text size of the overlay when the overlay is first
     * shown. It is also used to get the current text size of the overlay when the overlay
     * is currently visible.
     *
     * The text size is used to size the text of the overlay such that it is visible and
     * readable on the screen. The text size is specified in pixels.
     *
     * @return [Float] The current text size of the overlay in pixels.
     */
    private var overlayTextSize: Float
        get() = overlayBinding.remaining.textSize
        set(value) = overlayBinding.remaining.setTextSize(TypedValue.COMPLEX_UNIT_PX, value)

    /**
     * A property that gets/sets the resource ID of the color attribute used to color the overlay's text.
     *
     * This property is used to set the color of the overlay's text when the overlay is first shown.
     * It is also used to get the current resource ID of the color attribute used to color the overlay's
     * text when the overlay is currently visible.
     *
     * The resource ID of the color attribute is used to get the color from the theme. The color is used
     * to color the text of the overlay such that it is visible and readable on the screen.
     *
     * The default value of this property is [materialR.attr.colorPrimary].
     *
     * @return [Int] The current resource ID of the color attribute used to color the overlay's text.
     */
    @setparam:AttrRes
    private var textColorAttr: Int = materialR.attr.colorPrimary
        set(resId) {
            field = resId
            val dynamicColorContext = DynamicColors.wrapContextIfAvailable(overlayBinding.root.context)
            overlayBinding.remaining.setTextColor(MaterialColors.getColor(dynamicColorContext, resId, Color.MAGENTA))
        }

    /**
     * A property that gets/sets the resource ID of the color attribute used to color the overlay's text.
     *
     * This property is used to set the color of the overlay's text when the overlay is first
     * shown. It is also used to get the current color of the overlay's text when the overlay
     * is currently visible.
     *
     * The color is specified as a resource ID of a color attribute in the theme.
     *
     * @return [Int] The resource ID of the color attribute used to color the overlay's text.
     */
    @get:AttrRes
    @setparam:AttrRes
    private var overlayTextColorAttr: Int
        get() = textColorAttr
        set(resId) {
            textColorAttr = resId
        }

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
            Log.d("Setting overlay text to $value, isRTL: ${CaffeinateApplication.isRTL}...")

            overlayBinding.remaining.text = value
            overlayTextColorAttr = materialR.attr.colorPrimary
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
    var alpha
        get() = overlayBinding.root.alpha
        set(value) {
            Log.d("changing overlay alpha to $value...")
            overlayBinding.root.alpha = value
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

            overlayTextSize = when {
                CaffeinateApplication.isRTL -> context.resources.getDimension(R.dimen.overlayTextSizeRTL)
                else                        -> context.resources.getDimension(R.dimen.overlayTextSizeLTR)
            }
            overlayTextColorAttr = materialR.attr.colorPrimary


            windowManager.addView(overlayBinding.root, layoutParams)

            localeChangeReceiver.isRegistered = true

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

            localeChangeReceiver.isRegistered = false

            isOverlayVisible = false

            Log.d("Overlay hidden.")
        }
    }

    /**
     * Called when the locale has changed.
     *
     * This method is called when the [Intent.ACTION_LOCALE_CHANGED] broadcast is received.
     * It is responsible for updating the overlay's position and text size based on the new locale.
     *
     * @param context [Context] the context in which the locale change occurred.
     * @param intent [Intent] the intent that triggered the locale change.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun onLocaleChanged(context: Context, intent: Intent) {
        Log.d("App locale changed from system settings! Apply new Locale...")

        layoutParams.gravity = Gravity.TOP or when {
            CaffeinateApplication.isRTL -> Gravity.START
            else                        -> Gravity.END
        }

        overlayTextSize = when {
            CaffeinateApplication.isRTL -> context.resources.getDimension(R.dimen.overlayTextSizeRTL)
            else                        -> context.resources.getDimension(R.dimen.overlayTextSizeLTR)
        }

        windowManager.updateViewLayout(overlayBinding.root, layoutParams)

        CaffeinateApplication.applicationLocale.run { Log.d("Locale changed to $displayName ($language)") }
    }
}