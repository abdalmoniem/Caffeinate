package com.hifnawy.caffeinate.utils

import android.content.Context
import android.util.LayoutDirection
import androidx.core.text.layoutDirection
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.R
import kotlin.time.Duration

object DurationExtensionFunctions {

    fun Duration.toFormattedTime(hideLegend: Boolean = false): String = toComponents { hours, minutes, seconds, _ ->
        when (this) {
            Duration.INFINITE -> "∞"
            else              -> {
                val format = when {
                    hideLegend || isRTL -> if (hours == 0L) "%02d:%02d" else "%02d:%02d:%02d"
                    else                -> if (hours == 0L) "%02dm %02ds" else "%02dh %02dm %02ds"
                }

                when (hours) {
                    0L   -> String.format(CaffeinateApplication.applicationLocale, format, minutes, seconds)
                    else -> String.format(CaffeinateApplication.applicationLocale, format, hours, minutes, seconds)
                }
            }
        }
    }

    fun Duration.toLocalizedFormattedTime(context: Context, hideLegend: Boolean = false): String = toComponents { hours, minutes, seconds, _ ->
        val hourLetter = context.getString(R.string.time_format_hour_letter)
        val minuteLetter = context.getString(R.string.time_format_minute_letter)
        val secondLetter = context.getString(R.string.time_format_second_letter)

        when (this) {
            Duration.INFINITE -> "∞"
            else              -> {
                val format = when {
                    hideLegend || isRTL -> if (hours == 0L) "%02d:%02d" else "%02d:%02d:%02d"
                    else                -> if (hours == 0L) "%02d${minuteLetter} %02d${secondLetter}" else "%02d${hourLetter} %02d${minuteLetter} %02d${secondLetter}"
                }

                when (hours) {
                    0L   -> String.format(CaffeinateApplication.applicationLocale, format, minutes, seconds)
                    else -> String.format(CaffeinateApplication.applicationLocale, format, hours, minutes, seconds)
                }
            }
        }
    }
    private val isRTL: Boolean
        get() = CaffeinateApplication.applicationLocale.layoutDirection == LayoutDirection.RTL
}