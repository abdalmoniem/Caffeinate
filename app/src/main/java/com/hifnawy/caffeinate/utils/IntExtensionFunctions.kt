package com.hifnawy.caffeinate.utils

import android.content.res.Resources

/**
 * Provides extension functions for [Int] to facilitate common operations.
 *
 * This object contains utility functions that extend the functionality of the [Int] class.
 * These functions provide convenient methods for performing operations on integers.
 *
 * @author AbdAlMoniem AlHifnawy
 */
object IntExtensionFunctions {

    /**
     * Converts an integer value to a size in display pixels (DP).
     *
     * This property multiplies the integer value by the device's display density
     * and returns the result as an integer. It is a convenience method for converting
     * a size in DP to a size in pixels.
     *
     * @receiver [Int] The integer value to be converted.
     * @return [Int] The size in DP, as an integer.
     */
    val Int.dp: Int
        get() = (this * Resources.getSystem().displayMetrics.density).toInt()
}