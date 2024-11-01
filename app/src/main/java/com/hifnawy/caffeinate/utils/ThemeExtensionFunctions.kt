package com.hifnawy.caffeinate.utils

import android.content.res.Resources
import android.util.TypedValue
import androidx.annotation.ColorInt
import com.hifnawy.caffeinate.utils.ThemeExtensionFunctions.themeColor

/**
 * A utility class that provides extension functions for theme-related operations.
 *
 * This class contains extension functions that simplify the process of retrieving theme attributes,
 * such as colors, from the current theme in an Android application.
 *
 * Example usage:
 * ```
 * val primaryColor = theme.themeColor
 * ```
 *
 * @receiver [Resources.Theme] The theme from which the primary color is retrieved.
 *
 * @property themeColor [ColorInt] Retrieves the primary color of the current theme.
 * This is an extension property for [Resources.Theme] that resolves the `colorPrimary` attribute
 * and returns its integer representation.
 *
 * @return [ColorInt] The primary color of the current theme.
 *
 * @throws Resources.NotFoundException If the `colorPrimary` attribute is not defined in the current theme.
 *
 * @author AbdAlMoniem AlHifnawy
 *
 * @see Resources.Theme
 * @see TypedValue
 */
object ThemeExtensionFunctions {

    /**
     * Retrieves the primary color of the current theme.
     *
     * @return [ColorInt] the primary color of the current theme.
     */
    val Resources.Theme.themeColor: Int
        @ColorInt
        get() {
            val typedValue = TypedValue()
            this@themeColor.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
            return typedValue.data
        }
}