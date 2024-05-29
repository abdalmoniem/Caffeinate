package com.hifnawy.caffeinate.utils

import java.util.Locale
import kotlin.time.Duration

object DurationExtensionFunctions {

    fun Duration.toFormattedTime(hideLegend: Boolean = false): String = this.toComponents { hours, minutes, seconds, _ ->
        when (this) {
            Duration.INFINITE -> "∞"
            else              -> {
                val format = when {
                    hideLegend -> if (hours == 0L) "%02d:%02d" else "%02d:%02d:%02d"
                    else       -> if (hours == 0L) "%02dm %02ds" else "%02dh %02dm %02ds"
                }

                when (hours) {
                    0L -> String.format(Locale.getDefault(), format, minutes, seconds)
                    else -> String.format(Locale.getDefault(), format, hours, minutes, seconds)
                }
            }
        }
    }
}