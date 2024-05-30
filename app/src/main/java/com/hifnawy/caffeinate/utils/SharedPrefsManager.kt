package com.hifnawy.caffeinate.utils

import android.content.Context
import com.hifnawy.caffeinate.CaffeinateApplication

class SharedPrefsManager(private val caffeinateApplication: CaffeinateApplication) {
    private companion object {

        private const val SHARED_PREFERENCES_ALL_PERMISSIONS_GRANTED = "all.permissions.granted"
        private const val SHARED_PREFERENCES_ENABLE_DIMMING = "enable.dimming"
    }

    private val sharedPreferences by lazy { caffeinateApplication.getSharedPreferences(caffeinateApplication.packageName, Context.MODE_PRIVATE) }
    var isAllPermissionsGranted: Boolean
        get() = sharedPreferences.getBoolean(SHARED_PREFERENCES_ALL_PERMISSIONS_GRANTED, false)
        set(value) {
            sharedPreferences.edit().putBoolean(SHARED_PREFERENCES_ALL_PERMISSIONS_GRANTED, value).apply()
            notifyObservers()
        }
    var isDimmingEnabled: Boolean
        get() = sharedPreferences.getBoolean(SHARED_PREFERENCES_ENABLE_DIMMING, false)
        set(value) {
            sharedPreferences.edit().putBoolean(SHARED_PREFERENCES_ENABLE_DIMMING, value).apply()
            notifyObservers()
        }

    private fun notifyObservers(isAllPermissionsGranted: Boolean = this.isAllPermissionsGranted, isDimmingEnabled: Boolean = this.isDimmingEnabled) {
        caffeinateApplication.sharedPrefsObservers.forEach { observer ->
            observer.onIsAllPermissionsGrantedChanged(isAllPermissionsGranted)
            observer.onIsDimmingEnabledChanged(isDimmingEnabled)
        }
    }

    interface SharedPrefsChangedListener {

        fun onIsAllPermissionsGrantedChanged(value: Boolean)
        fun onIsDimmingEnabledChanged(value: Boolean)
    }
}