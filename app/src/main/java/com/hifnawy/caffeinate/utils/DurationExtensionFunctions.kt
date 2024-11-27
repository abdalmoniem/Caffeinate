package com.hifnawy.caffeinate.utils

import android.content.Context
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.R
import kotlin.time.Duration

/**
 * Provides extension functions for [Duration] that provide human-readable representations of the duration.
 *
 * @author AbdAlMoniem AlHifnawy
 */
object DurationExtensionFunctions {

    /**
     * Converts the duration to a formatted string suitable for display to the user.
     *
     * The format of the string is determined by the current locale. If the locale is a right-to-left locale, the format will be `HH:mm:ss`.
     * Otherwise, the format will be `HH MM SS`.
     *
     * @param hideLegend [Boolean] if `true`, the string will not include the `hour`, `minute`, or `second` labels.
     *
     * @return [String] the formatted string.
     */
    fun Duration.toFormattedTime(hideLegend: Boolean = false): String = toComponents { hours, minutes, seconds, _ ->
        when {
            isInfinite() -> "Ꝏ"
            else         -> {
                val format = when {
                    hideLegend || CaffeinateApplication.isRTL -> if (hours == 0L) "%02d:%02d" else "%02d:%02d:%02d"
                    else                                      -> if (hours == 0L) "%02dm %02ds" else "%02dh %02dm %02ds"
                }

                when (hours) {
                    0L   -> String.format(CaffeinateApplication.applicationLocale, format, minutes, seconds)
                    else -> String.format(CaffeinateApplication.applicationLocale, format, hours, minutes, seconds)
                }
            }
        }
    }

    /**
     * Formats the duration in a localized string that is suitable for display to the user.
     *
     * The format of the string is determined by the current locale. If the locale is a right-to-left locale, the format will be `HH:mm:ss`.
     * Otherwise, the format will be `HH MM SS`.
     *
     * @param context [Context] the context to use for resolving the string resources.
     * @param hideLegend [Boolean] if `true`, the string will not include the `hour`, `minute`, or `second` labels.
     *
     * @return [String] the formatted string.
     */
    fun Duration.toLocalizedFormattedTime(context: Context, hideLegend: Boolean = false): String = toComponents { hours, minutes, seconds, _ ->
        val hourLetter = context.getString(R.string.time_format_hour_letter)
        val minuteLetter = context.getString(R.string.time_format_minute_letter)
        val secondLetter = context.getString(R.string.time_format_second_letter)

        when {
            isInfinite() -> "∞"
            else         -> {
                val format = when {
                    hideLegend || CaffeinateApplication.isRTL -> if (hours == 0L) "%02d:%02d" else "%02d:%02d:%02d"
                    else                                      -> if (hours == 0L) "%02d${minuteLetter} %02d${secondLetter}" else "%02d${hourLetter} %02d${minuteLetter} %02d${secondLetter}"
                }

                when (hours) {
                    0L   -> String.format(CaffeinateApplication.applicationLocale, format, minutes, seconds)
                    else -> String.format(CaffeinateApplication.applicationLocale, format, hours, minutes, seconds)
                }
            }
        }
    }
}