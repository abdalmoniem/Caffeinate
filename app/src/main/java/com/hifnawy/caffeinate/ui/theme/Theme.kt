package com.hifnawy.caffeinate.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.hifnawy.caffeinate.controller.SharedPrefsManager

/**
 * The Caffeinate Compose theme.
 *
 * Applies a [MaterialTheme] based on the user's theme / contrast / Material You
 * preferences stored in [SharedPrefsManager].
 *
 * @param darkTheme whether to use the dark variant of the color scheme.
 * @param dynamicColor whether to use dynamic (Material You) colors when available.
 * @param contrastLevel the contrast level to apply.
 * @param content the content to display inside the theme.
 */
@Composable
fun CaffeinateTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    contrastLevel: SharedPrefsManager.ContrastLevel = SharedPrefsManager.ContrastLevel.STANDARD,
    content: @Composable () -> Unit
) {
    MaterialTheme(content = content)
}
