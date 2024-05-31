package com.hifnawy.caffeinate.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.hifnawy.caffeinate.CaffeinateApplication

class SharedPrefsManager(private val caffeinateApplication: CaffeinateApplication) {
    private companion object {

        private const val SHARED_PREFERENCES_ALL_PERMISSIONS_GRANTED = "all.permissions.granted"
        private const val SHARED_PREFERENCES_ENABLE_DIMMING = "enable.dimming"
        private const val SHARED_PREFERENCES_THEME = "theme"
        private const val SHARED_PREFERENCES_ENABLE_MATERIAL_YOU = "enable.material.you"
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
    var theme: Theme
        get() = Theme.valueOf(sharedPreferences.getString(SHARED_PREFERENCES_THEME, Theme.SYSTEM_DEFAULT.name) ?: Theme.SYSTEM_DEFAULT.name)
        set(value) = sharedPreferences.edit().putString(SHARED_PREFERENCES_THEME, value.name).apply()
    var isMaterialYouEnabled: Boolean
        get() = sharedPreferences.getBoolean(SHARED_PREFERENCES_ENABLE_MATERIAL_YOU, false)
        set(value) = sharedPreferences.edit().putBoolean(SHARED_PREFERENCES_ENABLE_MATERIAL_YOU, value).apply()

    private fun notifyObservers(isAllPermissionsGranted: Boolean = this.isAllPermissionsGranted, isDimmingEnabled: Boolean = this.isDimmingEnabled) {
        caffeinateApplication.sharedPrefsObservers.forEach { observer ->
            observer.onIsAllPermissionsGrantedChanged(isAllPermissionsGranted)
            observer.onIsDimmingEnabledChanged(isDimmingEnabled)
        }
    }

    enum class Theme(var value: Int) {
        SYSTEM_DEFAULT(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
        DARK(AppCompatDelegate.MODE_NIGHT_YES)
    }

    interface SharedPrefsChangedListener {

        fun onIsAllPermissionsGrantedChanged(value: Boolean)
        fun onIsDimmingEnabledChanged(value: Boolean)
    }
}