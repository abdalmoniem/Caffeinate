package com.hifnawy.caffeinate.utils

import android.os.PowerManager.WakeLock
import android.util.Log

object WakeLockExtensionFunctions {

    private val LOG_TAG = WakeLockExtensionFunctions::class.simpleName
    fun WakeLock.releaseSafely(variableName: String) {
        val methodName = object {}.javaClass.enclosingMethod?.name
        when {
            isHeld -> {
                Log.d(LOG_TAG, "${methodName}() -> releasing $variableName...")
                release()
                Log.d(LOG_TAG, "${methodName}() -> $variableName released!")
            }

            else   -> Log.d(LOG_TAG, "${methodName}() -> $variableName is already released!")
        }
    }
}