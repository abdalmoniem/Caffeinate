package com.hifnawy.caffeinate.utils

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import com.google.android.material.color.DynamicColors
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.controller.SharedPrefsManager

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
     * @param contrastLevel [SharedPrefsManager.ContrastLevel] the contrast level to use.
     * @param nightMode [Int] the night mode to use. Can be one of
     * - [MODE_NIGHT_FOLLOW_SYSTEM]
     * - [MODE_NIGHT_NO]
     * - [MODE_NIGHT_YES].
     * @param isMaterialYouEnabled [Boolean] `true` if the Material You theme should be enabled, `false` otherwise.
     */
    fun AppCompatActivity.setActivityTheme(
            contrastLevel: SharedPrefsManager.ContrastLevel = SharedPrefsManager.ContrastLevel.STANDARD,
            nightMode: Int = MODE_NIGHT_FOLLOW_SYSTEM,
            isMaterialYouEnabled: Boolean = false
    ) {
        require(nightMode in listOf(MODE_NIGHT_FOLLOW_SYSTEM, MODE_NIGHT_NO, MODE_NIGHT_YES)) {
            val supportedNightModes = listOf(
                    "${::MODE_NIGHT_FOLLOW_SYSTEM.name} ($MODE_NIGHT_FOLLOW_SYSTEM)",
                    "${::MODE_NIGHT_NO.name} ($MODE_NIGHT_NO)",
                    "${::MODE_NIGHT_YES.name} ($MODE_NIGHT_YES)"
            )

            "nightMode must be one of the following: $supportedNightModes"
        }
        val themeResId = when {
            contrastLevel == SharedPrefsManager.ContrastLevel.STANDARD && !isMaterialYouEnabled -> R.style.AppTheme
            contrastLevel == SharedPrefsManager.ContrastLevel.MEDIUM && !isMaterialYouEnabled   -> R.style.ThemeOverlay_AppTheme_MediumContrast
            contrastLevel == SharedPrefsManager.ContrastLevel.HIGH && !isMaterialYouEnabled     -> R.style.ThemeOverlay_AppTheme_HighContrast
            DynamicColors.isDynamicColorAvailable() && isMaterialYouEnabled                     -> R.style.AppTheme_Dynamic
            else                                                                                -> R.style.AppTheme
        }

        setDefaultNightMode(nightMode)
        setTheme(themeResId)
    }
}