package com.hifnawy.caffeinate.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.ui.CheckBoxItem
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toLocalizedFormattedTime
import com.hifnawy.caffeinate.utils.SharedPreferencesExtensionFunctions.getSerializableList
import com.hifnawy.caffeinate.utils.SharedPreferencesExtensionFunctions.putSerializableList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SharedPrefsManager(private val caffeinateApplication: CaffeinateApplication) {

    private enum class SharedPrefsKeys {
        ALL_PERMISSIONS_GRANTED,
        THEME,
        ENABLE_DIMMING,
        ENABLE_MATERIAL_YOU,
        ENABLE_WHILE_LOCKED,
        TIMEOUT_CHECK_BOXES,
    }

    // val timeouts by lazy { listOf(30.seconds, 5.minutes, 10.minutes, 15.minutes, 30.minutes, 60.minutes, 120.minutes, 240.minutes, 480.minutes, Duration.INFINITE) }
    val timeouts by lazy { listOf(30.seconds, 5.minutes, 10.minutes, 15.minutes, 30.minutes, 60.minutes, Duration.INFINITE) }
    private val sharedPreferences by lazy { caffeinateApplication.getSharedPreferences(caffeinateApplication.packageName, Context.MODE_PRIVATE) }
    var isAllPermissionsGranted: Boolean
        get() = sharedPreferences.getBoolean(SharedPrefsKeys.ALL_PERMISSIONS_GRANTED.name, false)
        set(value) {
            sharedPreferences.edit().putBoolean(SharedPrefsKeys.ALL_PERMISSIONS_GRANTED.name, value).apply()
            notifySharedPrefsObservers { observer -> observer.onIsAllPermissionsGrantedChanged(value) }
        }
    var theme: Theme
        get() = Theme.valueOf(sharedPreferences.getString(SharedPrefsKeys.THEME.name, Theme.SYSTEM_DEFAULT.name) ?: Theme.SYSTEM_DEFAULT.name)
        set(value) = sharedPreferences.edit().putString(SharedPrefsKeys.THEME.name, value.name).apply()
    var isMaterialYouEnabled: Boolean
        get() = sharedPreferences.getBoolean(SharedPrefsKeys.ENABLE_MATERIAL_YOU.name, false)
        set(value) = sharedPreferences.edit().putBoolean(SharedPrefsKeys.ENABLE_MATERIAL_YOU.name, value).apply()
    var isDimmingEnabled: Boolean
        get() = sharedPreferences.getBoolean(SharedPrefsKeys.ENABLE_DIMMING.name, false)
        set(value) {
            sharedPreferences.edit().putBoolean(SharedPrefsKeys.ENABLE_DIMMING.name, value).apply()
            notifySharedPrefsObservers { observer -> observer.onIsDimmingEnabledChanged(value) }
        }
    var isWhileLockedEnabled: Boolean
        get() = sharedPreferences.getBoolean(SharedPrefsKeys.ENABLE_WHILE_LOCKED.name, false)
        set(value) {
            sharedPreferences.edit().putBoolean(SharedPrefsKeys.ENABLE_WHILE_LOCKED.name, value).apply()
            notifySharedPrefsObservers { observer -> observer.onIsWhileLockedEnabledChanged(value) }
        }
    var timeoutCheckBoxes: MutableList<CheckBoxItem>
        get() = when {
            sharedPreferences.contains(SharedPrefsKeys.TIMEOUT_CHECK_BOXES.name) -> sharedPreferences.getSerializableList<MutableList<CheckBoxItem>>(SharedPrefsKeys.TIMEOUT_CHECK_BOXES.name)
            else                                                                 -> timeouts.map { timeout ->
                CheckBoxItem(text = timeout.toLocalizedFormattedTime(caffeinateApplication.localizedApplicationContext), isChecked = true, isEnabled = true, duration = timeout)
            }.toMutableList()
        }
        set(value) = sharedPreferences.edit().putSerializableList(SharedPrefsKeys.TIMEOUT_CHECK_BOXES.name, value).apply()

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