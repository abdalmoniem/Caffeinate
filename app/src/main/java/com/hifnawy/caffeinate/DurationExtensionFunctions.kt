package com.hifnawy.caffeinate

import java.util.Locale
import kotlin.time.Duration

object DurationExtensionFunctions {

    fun Duration.format(hideLegend: Boolean = false): String = this.toComponents { hours, minutes,
                                                                                   seconds, _ ->
        when {
            this == Duration.INFINITE -> "∞"
            else                      -> {
                if (hideLegend) {
                    if (hours == 0.toLong())
                        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                    else
                        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
                } else {
                    if (hours == 0.toLong())
                        String.format(Locale.getDefault(), "%02dm %02ds", minutes, seconds)
                    else
                        String.format(Locale.getDefault(), "%02dh %02dm %02ds", hours, minutes, seconds)
                }
            }
        }
    }
}