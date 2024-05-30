package com.hifnawy.caffeinate.utils

import android.os.PowerManager.WakeLock
import android.util.Log
import com.hifnawy.caffeinate.services.KeepAwakeService
import kotlin.time.Duration

object WakeLockExtensionFunctions {
    private val LOG_TAG = WakeLockExtensionFunctions::class.simpleName
    fun WakeLock.releaseSafely() {
        val methodName = object{}.javaClass.enclosingMethod?.name
        when {
            isHeld -> {
                Log.d(LOG_TAG, "${methodName}() -> releasing ${this::javaClass.name}...")
                release()
                Log.d(LOG_TAG, "${methodName}() -> ${this::javaClass.name} released!")
            }
            else   -> Log.d(LOG_TAG, "${methodName}() -> ${this::javaClass.name} is already released!")
        }
    }
}