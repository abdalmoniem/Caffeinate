package com.hifnawy.caffeinate.utils

import android.os.PowerManager.WakeLock
import timber.log.Timber as Log

object WakeLockExtensionFunctions {

    fun WakeLock.releaseSafely(variableName: String) {
        val methodName = object {}.javaClass.enclosingMethod?.name
        when {
            isHeld -> {
                Log.d("${methodName}() -> releasing $variableName...")
                release()
                Log.d("${methodName}() -> $variableName released!")
            }

            else   -> Log.d("${methodName}() -> $variableName is already released!")
        }
    }
}