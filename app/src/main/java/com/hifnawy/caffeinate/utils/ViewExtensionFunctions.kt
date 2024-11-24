package com.hifnawy.caffeinate.utils

import android.view.View

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
}