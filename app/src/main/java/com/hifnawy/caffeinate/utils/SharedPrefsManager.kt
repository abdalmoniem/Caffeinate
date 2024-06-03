package com.hifnawy.caffeinate.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.hifnawy.caffeinate.CaffeinateApplication

class SharedPrefsManager(private val caffeinateApplication: CaffeinateApplication) {
    private companion object {

        private const val SHARED_PREFERENCES_THEME = "theme"
        private const val SHARED_PREFERENCES_ENABLE_DIMMING = "enable.dimming"
        private const val SHARED_PREFERENCES_ENABLE_MATERIAL_YOU = "enable.material.you"
        private const val SHARED_PREFERENCES_ENABLE_WHILE_LOCKED = "enable.while.locked"
        private const val SHARED_PREFERENCES_ALL_PERMISSIONS_GRANTED = "all.permissions.granted"
    }

    private val sharedPreferences by lazy { caffeinateApplication.getSharedPreferences(caffeinateApplication.packageName, Context.MODE_PRIVATE) }
    var isAllPermissionsGranted: Boolean
        get() = sharedPreferences.getBoolean(SHARED_PREFERENCES_ALL_PERMISSIONS_GRANTED, false)
        set(value) {
            sharedPreferences.edit().putBoolean(SHARED_PREFERENCES_ALL_PERMISSIONS_GRANTED, value).apply()
            notifySharedPrefsObservers { observer -> observer.onIsAllPermissionsGrantedChanged(value) }
        }
    var isDimmingEnabled: Boolean
        get() = sharedPreferences.getBoolean(SHARED_PREFERENCES_ENABLE_DIMMING, false)
        set(value) {
            sharedPreferences.edit().putBoolean(SHARED_PREFERENCES_ENABLE_DIMMING, value).apply()
            notifySharedPrefsObservers { observer -> observer.onIsDimmingEnabledChanged(value) }
        }
    var isWhileLockedEnabled: Boolean
        get() = sharedPreferences.getBoolean(SHARED_PREFERENCES_ENABLE_WHILE_LOCKED, false)
        set(value) {
            sharedPreferences.edit().putBoolean(SHARED_PREFERENCES_ENABLE_WHILE_LOCKED, value).apply()
            notifySharedPrefsObservers { observer -> observer.onIsWhileLockedEnabledChanged(value) }
        }
    var theme: Theme
        get() = Theme.valueOf(sharedPreferences.getString(SHARED_PREFERENCES_THEME, Theme.SYSTEM_DEFAULT.name) ?: Theme.SYSTEM_DEFAULT.name)
        set(value) = sharedPreferences.edit().putString(SHARED_PREFERENCES_THEME, value.name).apply()
    var isMaterialYouEnabled: Boolean
        get() = sharedPreferences.getBoolean(SHARED_PREFERENCES_ENABLE_MATERIAL_YOU, false)
        set(value) = sharedPreferences.edit().putBoolean(SHARED_PREFERENCES_ENABLE_MATERIAL_YOU, value).apply()

    private fun notifySharedPrefsObservers(notifyCallback: (observer: SharedPrefsChangedListener) -> Unit) = caffeinateApplication.sharedPrefsObservers.forEach(notifyCallback)

    enum class Theme(var value: Int) {
        SYSTEM_DEFAULT(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
        DARK(AppCompatDelegate.MODE_NIGHT_YES)
    }

    interface SharedPrefsChangedListener {

        fun onIsAllPermissionsGrantedChanged(isAllPermissionsGranted: Boolean)
        fun onIsDimmingEnabledChanged(isDimmingEnabled: Boolean)
        fun onIsWhileLockedEnabledChanged(isWhileLockedEnabled: Boolean)
    }
}