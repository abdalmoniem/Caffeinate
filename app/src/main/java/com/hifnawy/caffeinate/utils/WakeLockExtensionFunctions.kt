package com.hifnawy.caffeinate.utils

import android.os.PowerManager.WakeLock
import timber.log.Timber as Log

object WakeLockExtensionFunctions {

    fun WakeLock.releaseSafely(variableName: String) = when {
        isHeld -> {
            Log.d("releasing $variableName...")
            release()
            Log.d("$variableName released!")
        }

        else   -> Log.d("$variableName is already released!")
    }
}