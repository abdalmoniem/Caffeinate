package com.hifnawy.caffeinate.utils

import android.graphics.Rect
import android.view.View
import com.hifnawy.caffeinate.utils.IntExtensionFunctions.dp

/**
 * Utility functions for working with [View].
 *
 * This class provides functions that make it easier to work with [View].
 * It provides a set of extension functions that can be called on any [View] instance.
 *
 * @author AbdAlMoniem AlHifnawy
 */
object ViewExtensionFunctions {

    /**
     * Returns `true` if the view is visible, `false` otherwise.
     *
     * This property is a shortcut for calling [View.getVisibility] and checking if the result is
     * [View.VISIBLE].
     *
     * @return `true` if the view is visible, `false` otherwise
     */
    var View.isVisible: Boolean
        get() = visibility == View.VISIBLE
        set(value) {
            visibility = if (value) View.VISIBLE else View.GONE
        }

    /**
     * A utility property to set and get the height of a view in pixels.
     *
     * @return [Int] The height of the view in pixels.
     */
    var View.viewHeight: Int
        get() = layoutParams.height
        set(value) {
            if (value <= 0) layoutParams.width = 0.dp
            layoutParams.height = value
            requestLayout()
        }

    /**
     * A utility property that returns the height of the window in display pixels (DP).
     *
     * This property calculates the height of the window by taking into account the display height
     * and the size of the top and bottom system bars. It takes into account the display density
     * and returns the result as an integer.
     *
     * @return [Int] The height of the window in DP, as an integer.
     */
    val View.windowHeight: Int
        get() {
            val height = resources.displayMetrics.heightPixels

            with(rootWindowInsets) {
                @Suppress("DEPRECATION")
                val topInset = when {
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R -> getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars()).top
                    else                                                                 -> systemWindowInsetTop
                }

                @Suppress("DEPRECATION")
                val bottomInset = when {
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R -> getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars()).bottom
                    else                                                                 -> systemWindowInsetBottom
                }

                return height + topInset + bottomInset
            }
        }

    /**
     * Sets a listener to be called when the size of the view changes.
     *
     * This extension function adds an OnLayoutChangeListener to the view which executes the provided
     * callback whenever the view's size changes. The callback provides the new and old dimensions of
     * the view, allowing developers to respond to size changes.
     *
     * @param callback A function to be invoked when the view's size changes. It receives the view itself,
     * the new width and height, and the old width and height as parameters.
     */
    inline fun View.onSizeChange(crossinline callback: (view: View, newWidth: Int, newHeight: Int, oldWidth: Int, oldHeight: Int) -> Unit) =
            addOnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val rect = Rect(left, top, right, bottom)
                val oldRect = Rect(oldLeft, oldTop, oldRight, oldBottom)

                if (rect.width() == oldRect.width() && rect.height() == oldRect.height()) return@addOnLayoutChangeListener

                callback(view, rect.width(), rect.height(), oldRect.width(), oldRect.height())
            }
}