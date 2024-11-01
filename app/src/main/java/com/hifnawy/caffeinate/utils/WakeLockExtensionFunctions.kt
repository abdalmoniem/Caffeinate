package com.hifnawy.caffeinate.utils

import android.os.PowerManager.WakeLock
import timber.log.Timber as Log

/**
 * Extensions for [WakeLock].
 *
 * @author AbdAlMoniem AlHifnawy
 */
object WakeLockExtensionFunctions {

    /**
     * Safely releases the WakeLock, logging whether or not the WakeLock was being held.
     *
     * @param variableName [String] the name of the variable holding the WakeLock (used for logging).
     */
    fun WakeLock.releaseSafely(variableName: String) = when {
        isHeld -> {
            Log.d("releasing $variableName...")
            release()
            Log.d("$variableName released!")
        }

        else   -> Log.d("$variableName is already released!")
    }
}