package com.hifnawy.caffeinate.utils

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import com.google.android.material.color.DynamicColors
import com.hifnawy.caffeinate.R

/**
 * Utility functions for working with [AppCompatActivity].
 *
 * This class provides functions that make it easier to work with [AppCompatActivity].
 * It provides a set of extension functions that can be called on any [AppCompatActivity] instance.
 *
 * @author AbdAlMoniem AlHifnawy
 */
object ActivityExtensionFunctions {

    /**
     * Sets the theme of the current activity.
     *
     * This function sets the theme of the current activity based on the given [nightMode] and [isMaterialYouEnabled] values.
     *
     * If [isMaterialYouEnabled] is `true`, the activity will use the Material You theme. This theme is
     * available on Android 12+ and changes the color scheme of the activity based on the current system theme.
     *
     * If [isMaterialYouEnabled] is `false`, the activity will use the baseline theme. This theme is used
     * when the Material You theme is not available.
     *
     * @param nightMode [Int] the night mode to use. Can be one of
     * - [MODE_NIGHT_FOLLOW_SYSTEM]
     * - [MODE_NIGHT_NO]
     * - [MODE_NIGHT_YES].
     * @param isMaterialYouEnabled [Boolean] `true` if the Material You theme should be enabled, `false` otherwise.
     */
    fun AppCompatActivity.setActivityTheme(nightMode: Int = MODE_NIGHT_FOLLOW_SYSTEM, isMaterialYouEnabled: Boolean = false) {
        require(nightMode in listOf(MODE_NIGHT_FOLLOW_SYSTEM, MODE_NIGHT_NO, MODE_NIGHT_YES)) {
            val supportedNightModes = listOf(
                    "${::MODE_NIGHT_FOLLOW_SYSTEM.name} ($MODE_NIGHT_FOLLOW_SYSTEM)",
                    "${::MODE_NIGHT_NO.name} ($MODE_NIGHT_NO)",
                    "${::MODE_NIGHT_YES.name} ($MODE_NIGHT_YES)"
            )

            "nightMode must be one of the following: $supportedNightModes"
        }

        setDefaultNightMode(nightMode)
        if (DynamicColors.isDynamicColorAvailable() && isMaterialYouEnabled) {
            setTheme(R.style.Theme_Caffeinate_Dynamic)
            DynamicColors.applyToActivityIfAvailable(this)
        } else setTheme(R.style.Theme_Caffeinate_Baseline)
    }
}