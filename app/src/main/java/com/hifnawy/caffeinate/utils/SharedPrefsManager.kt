package com.hifnawy.caffeinate.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.services.Observer
import com.hifnawy.caffeinate.services.ServiceStatus
import com.hifnawy.caffeinate.ui.CheckBoxItem
import com.hifnawy.caffeinate.ui.WidgetConfiguration
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toFormattedTime
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toLocalizedFormattedTime
import com.hifnawy.caffeinate.utils.SharedPreferencesExtensionFunctions.getSerializableList
import com.hifnawy.caffeinate.utils.SharedPreferencesExtensionFunctions.getSerializableMap
import com.hifnawy.caffeinate.utils.SharedPreferencesExtensionFunctions.putSerializableList
import com.hifnawy.caffeinate.utils.SharedPreferencesExtensionFunctions.putSerializableMap
import com.hifnawy.caffeinate.utils.SharedPrefsManager.SharedPrefsKeys.ALL_PERMISSIONS_GRANTED
import com.hifnawy.caffeinate.utils.SharedPrefsManager.SharedPrefsKeys.ENABLE_DIMMING
import com.hifnawy.caffeinate.utils.SharedPrefsManager.SharedPrefsKeys.ENABLE_MATERIAL_YOU
import com.hifnawy.caffeinate.utils.SharedPrefsManager.SharedPrefsKeys.ENABLE_OVERLAY
import com.hifnawy.caffeinate.utils.SharedPrefsManager.SharedPrefsKeys.ENABLE_WHILE_LOCKED
import com.hifnawy.caffeinate.utils.SharedPrefsManager.SharedPrefsKeys.IS_SERVICE_RUNNING
import com.hifnawy.caffeinate.utils.SharedPrefsManager.SharedPrefsKeys.LAST_REMAINING_TIMEOUT
import com.hifnawy.caffeinate.utils.SharedPrefsManager.SharedPrefsKeys.THEME
import com.hifnawy.caffeinate.utils.SharedPrefsManager.SharedPrefsKeys.TIMEOUT_CHECK_BOXES
import com.hifnawy.caffeinate.utils.SharedPrefsManager.SharedPrefsKeys.WIDGET_CONFIGURATION
import com.hifnawy.caffeinate.utils.SharedPrefsManager.Theme.DARK
import com.hifnawy.caffeinate.utils.SharedPrefsManager.Theme.LIGHT
import com.hifnawy.caffeinate.utils.SharedPrefsManager.Theme.SYSTEM_DEFAULT
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Manages shared preferences for the application, providing convenient methods to access and modify them.
 *
 * This class encapsulates the shared preferences logic, allowing clients to easily retrieve and update preferences
 * related to permissions, themes, and other settings. It also notifies registered observers of changes.
 *
 * @param caffeinateApplication The application instance used to access shared preferences.
 *
 * @constructor Creates an instance of [SharedPrefsManager] with the provided [CaffeinateApplication].
 *
 * @author AbdAlMoniem AlHifnawy
 *
 * @see CaffeinateApplication
 */
class SharedPrefsManager(private val caffeinateApplication: CaffeinateApplication) {

    /**
     * An enumeration of the shared preferences keys used by the application.
     *
     * These keys are used to store and retrieve values from the shared preferences of the application.
     *
     * @property ALL_PERMISSIONS_GRANTED A [Boolean] value indicating whether all permissions necessary for the application to function have been granted.
     * @property THEME A [Theme] value indicating the current theme of the application.
     * @property ENABLE_DIMMING A [Boolean] value indicating whether the screen should be dimmed while the service is running.
     * @property ENABLE_MATERIAL_YOU A [Boolean] value indicating whether Material You design elements should be enabled.
     * @property ENABLE_OVERLAY A [Boolean] value indicating whether the overlay should be enabled.
     * @property ENABLE_WHILE_LOCKED A [Boolean] value indicating whether the service should be enabled while the screen is locked.
     * @property TIMEOUT_CHECK_BOXES A [List] of [CheckBoxItem] values indicating the timeouts that should be displayed in the RecyclerView.
     *
     * @author AbdAlMoniem AlHifnawy
     */
    private enum class SharedPrefsKeys {

        /**
         * A [Boolean] value indicating whether all permissions necessary for the application to function have been granted.
         */
        ALL_PERMISSIONS_GRANTED,

        /**
         * A [Theme] value indicating the current theme of the application.
         */
        THEME,

        /**
         * A [Boolean] value indicating whether the screen should be dimmed while the service is running.
         */
        ENABLE_DIMMING,

        /**
         * A [Boolean] value indicating whether Material You design elements should be enabled.
         */
        ENABLE_MATERIAL_YOU,

        /**
         * A [Boolean] value indicating whether the overlay should be enabled.
         */
        ENABLE_OVERLAY,

        /**
         * A [Boolean] value indicating whether the service should be enabled while the screen is locked.
         */
        ENABLE_WHILE_LOCKED,

        /**
         * A [List] of [CheckBoxItem] values indicating the timeouts that should be displayed in the RecyclerView.
         */
        TIMEOUT_CHECK_BOXES,

        /**
         * A [Boolean] value indicating whether the service is running.
         *
         * > TODO: Remove or find a way to infer the service status without using shared preferences
         *         because this will put a strain on the device's storage since it will store
         *         the last remaining timeout in the shared preferences every time the service
         *         status is updated. which is every one second.
         */
        IS_SERVICE_RUNNING,

        /**
         * A [Long] value indicating the last remaining timeout value stored in shared preferences.
         *
         * > TODO: Remove or find a way to infer the service status without using shared preferences
         *         because this will put a strain on the device's storage since it will store
         *         the last remaining timeout in the shared preferences every time the service
         *         status is updated. which is every one second.
         */
        LAST_REMAINING_TIMEOUT,

        /**
         * A [WidgetConfiguration] value indicating the widget configuration.
         */
        WIDGET_CONFIGURATION
    }

    /**
     * An enumeration of the theme options that are available to the user.
     *
     * Themes control the overall visual appearance of the application. The theme can be set to one of the following values:
     * @property SYSTEM_DEFAULT Follows the device's system theme.
     * @property LIGHT A light theme.
     * @property DARK A dark theme.
     *
     * @property value [Int] The value of the theme, which is used to set the theme for the application.
     */
    enum class Theme(var value: Int) {

        /**
         * A theme that follows the device's system theme.
         *
         * The application will follow the device's system theme setting, which can be either light or dark.
         *
         * @see AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
         */
        SYSTEM_DEFAULT(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),

        /**
         * A light theme.
         *
         * The application will use a light theme, which is suitable for daytime use.
         *
         * @see AppCompatDelegate.MODE_NIGHT_NO
         */
        LIGHT(AppCompatDelegate.MODE_NIGHT_NO),

        /**
         * A dark theme.
         *
         * The application will use a dark theme, which is suitable for nighttime use.
         *
         * @see AppCompatDelegate.MODE_NIGHT_YES
         */
        DARK(AppCompatDelegate.MODE_NIGHT_YES)
    }

    private val sharedPreferences by lazy { caffeinateApplication.getSharedPreferences(caffeinateApplication.packageName, Context.MODE_PRIVATE) }

    /**
     * List of available timeouts that can be selected by the user.
     *
     * This list is used to populate the [DialogChooseTimeoutsBinding][com.hifnawy.caffeinate.databinding.DialogChooseTimeoutsBinding] and provide the
     * user with a variety of timeouts to choose from.
     *
     * The items in this list are used to generate the list items in the
     * [DialogChooseTimeoutsBinding][com.hifnawy.caffeinate.databinding.DialogChooseTimeoutsBinding] and are used to populate the
     * [CheckBoxItem] list.
     *
     * @return [List] A list of [Duration] objects representing the available timeouts.
     */
    // val timeouts by lazy { listOf(30.seconds, 5.minutes, 10.minutes, 15.minutes, 30.minutes, 60.minutes, 120.minutes, 240.minutes, 480.minutes, Duration.INFINITE) }
    val timeouts by lazy { listOf(30.seconds, 5.minutes, 10.minutes, 15.minutes, 30.minutes, 60.minutes, Duration.INFINITE) }

    /**
     * Checks if all necessary permissions are granted.
     *
     * This property is used to keep track of whether all necessary permissions have been granted. It is used to determine whether the main activity
     * should display the [DialogChooseTimeoutsBinding][com.hifnawy.caffeinate.databinding.DialogChooseTimeoutsBinding] or not, and to enable or
     * disable the [DialogChooseTimeoutsBinding][com.hifnawy.caffeinate.databinding.DialogChooseTimeoutsBinding] based on the state of this property.
     *
     * @return [Boolean] `true` if all necessary permissions are granted, `false` otherwise.
     */
    var isAllPermissionsGranted: Boolean
        get() = sharedPreferences.getBoolean(ALL_PERMISSIONS_GRANTED.name, false)
        set(value) {
            sharedPreferences.edit().putBoolean(ALL_PERMISSIONS_GRANTED.name, value).apply()
            notifySharedPrefsObservers { observer -> observer.onIsAllPermissionsGrantedUpdated(value) }
        }

    /**
     * Retrieves or sets the current theme of the application.
     *
     * This property is used to get or update the theme preference stored in shared preferences.
     * It allows the application to persist the user's theme choice across sessions.
     *
     * @return [Theme] The current theme set in the application.
     *
     * @see SharedPrefsManager.Theme
     */
    var theme: Theme
        get() = Theme.valueOf(sharedPreferences.getString(THEME.name, SYSTEM_DEFAULT.name) ?: SYSTEM_DEFAULT.name)
        set(value) = sharedPreferences.edit().putString(THEME.name, value.name).apply()

    /**
     * Retrieves or sets whether the "Material You" feature is enabled.
     *
     * This property is used to determine if the "Material You" dynamic theming feature is enabled in the application.
     * It allows the application to apply or remove dynamic theming based on the user's preference stored in shared preferences.
     *
     * @return [Boolean] `true` if the "Material You" feature is enabled, `false` otherwise.
     */
    var isMaterialYouEnabled: Boolean
        get() = sharedPreferences.getBoolean(ENABLE_MATERIAL_YOU.name, false)
        set(value) = sharedPreferences.edit().putBoolean(ENABLE_MATERIAL_YOU.name, value).apply()

    /**
     * Retrieves or sets whether the screen overlay should be shown while it is being kept awake.
     *
     * This property is used to get or update the preference of whether the screen overlay should be shown while it is being kept awake.
     * It allows the application to persist the user's preference across sessions.
     *
     * @return [Boolean] `true` if the screen overlay should be shown, `false` otherwise.
     */
    var isOverlayEnabled: Boolean
        get() = sharedPreferences.getBoolean(ENABLE_OVERLAY.name, false)
        set(value) {
            sharedPreferences.edit().putBoolean(ENABLE_OVERLAY.name, value).apply()
            notifySharedPrefsObservers { observer -> observer.onIsOverlayEnabledUpdated(value) }
        }

    /**
     * Retrieves or sets whether the screen should be dimmed while it is being kept awake.
     *
     * This property is used to get or update the preference of whether the screen should be dimmed while it is being kept awake.
     * It allows the application to persist the user's preference across sessions.
     *
     * @return [Boolean] `true` if the screen should be dimmed while it is being kept awake, `false` otherwise.
     */
    var isDimmingEnabled: Boolean
        get() = sharedPreferences.getBoolean(ENABLE_DIMMING.name, false)
        set(value) {
            sharedPreferences.edit().putBoolean(ENABLE_DIMMING.name, value).apply()
            notifySharedPrefsObservers { observer -> observer.onIsDimmingEnabledUpdated(value) }
        }

    /**
     * Retrieves or sets whether the "While Locked" feature is enabled.
     *
     * This property manages the preference for enabling the "While Locked" feature, allowing the application
     * to determine if the keep awake screen should be active while the device is locked.
     * The preference is stored in shared preferences and persists across sessions.
     *
     * @return [Boolean] `true` if the "While Locked" feature is enabled, `false` otherwise.
     */
    var isWhileLockedEnabled: Boolean
        get() = sharedPreferences.getBoolean(ENABLE_WHILE_LOCKED.name, false)
        set(value) {
            sharedPreferences.edit().putBoolean(ENABLE_WHILE_LOCKED.name, value).apply()
            notifySharedPrefsObservers { observer -> observer.onIsWhileLockedEnabledUpdated(value) }
        }

    /**
     * Retrieves or sets the list of timeout check boxes.
     *
     * This property manages the list of timeout check boxes that are shown in the UI. The list is stored in shared
     * preferences and persists across sessions.
     *
     * @return [List] A list of [CheckBoxItem] objects representing the list of timeout check boxes.
     */
    var timeoutCheckBoxes: MutableList<CheckBoxItem>
        get() = when {
            sharedPreferences.contains(TIMEOUT_CHECK_BOXES.name) -> sharedPreferences.getSerializableList<MutableList<CheckBoxItem>>(
                    TIMEOUT_CHECK_BOXES.name
            )

            else                                                 -> timeouts.map { timeout ->
                CheckBoxItem(text = timeout.toFormattedTime(), isChecked = true, isEnabled = true, duration = timeout)
            }
        }.map { checkBoxItem ->
            checkBoxItem.copy(text = checkBoxItem.duration.toLocalizedFormattedTime(caffeinateApplication.localizedApplicationContext))
        }.toMutableList()
        set(value) = sharedPreferences.edit().putSerializableList(TIMEOUT_CHECK_BOXES.name, value.map { checkBoxItem ->
            checkBoxItem.copy(text = checkBoxItem.duration.toLocalizedFormattedTime(caffeinateApplication.localizedApplicationContext))
        }).apply()

    /**
     * Retrieves or sets the widget configuration.
     *
     * This property manages the configuration of widgets, stored in shared preferences.
     * It allows the application to persist widget configuration across sessions.
     *
     * @return [MutableMap] A map of widget IDs to their [WidgetConfiguration].
     */
    var widgetsConfiguration: MutableMap<Int, WidgetConfiguration>
        get() = sharedPreferences.getSerializableMap<MutableMap<Int, WidgetConfiguration>>(WIDGET_CONFIGURATION.name)
        set(value) = sharedPreferences.edit().putSerializableMap(WIDGET_CONFIGURATION.name, value).apply()

    /**
     * Retrieves or sets whether the service is running.
     *
     * This property allows the application to determine if the service is currently running. The value is stored in
     * shared preferences and persists across sessions.
     *
     * > TODO: Remove or find a way to infer the service status without using shared preferences
     *         because this will put a strain on the device's storage since it will store
     *         the last remaining timeout in the shared preferences every time the service
     *         status is updated. which is every one second.
     *
     * @return [Boolean] `true` if the service is running, `false` otherwise.
     */
    var isServiceRunning: Boolean
        get() = sharedPreferences.getBoolean(IS_SERVICE_RUNNING.name, false)
        set(value) {
            sharedPreferences.edit().putBoolean(IS_SERVICE_RUNNING.name, value).apply()
            val timeout = when (value) {
                true  -> (caffeinateApplication.lastStatusUpdate as ServiceStatus.Running).remaining.inWholeSeconds
                false -> -1L
            }

            sharedPreferences.edit().putLong(LAST_REMAINING_TIMEOUT.name, timeout).apply()
        }

    /**
     * Retrieves the last remaining timeout value stored in shared preferences.
     *
     * This property returns the last remaining timeout value stored in shared preferences, which is the value of the
     * remaining timeout at the time the service was last stopped. The value is stored in shared preferences and persists
     * across sessions.
     *
     * If the service is not running or the last remaining timeout has not been stored, this property returns `-1`.
     *
     * > TODO: Remove or find a way to infer the service status without using shared preferences
     *         because this will put a strain on the device's storage since it will store
     *         the last remaining timeout in the shared preferences every time the service
     *         status is updated. which is every one second.
     *
     * @return [Long] The last remaining timeout value stored in shared preferences, or `-1` if the service is not running
     *         or the last remaining timeout has not been stored.
     */
    val lastRemainingTimeout: Long
        get() = sharedPreferences.getLong(LAST_REMAINING_TIMEOUT.name, -1)

    /**
     * Notifies all registered observers of a change in the shared preferences.
     *
     * The callback provided will be called on each registered observer. The callback should take a single parameter, which is
     * the observer to be notified. The callback should call the appropriate method on the observer to notify it of the change
     * in shared preferences.
     *
     * @param notifyCallback [(observer: SharedPrefsObserver) -> Unit][notifyCallback] the callback to be called on each registered observer.
     */
    private fun notifySharedPrefsObservers(notifyCallback: (observer: SharedPrefsObserver) -> Unit) =
            caffeinateApplication.sharedPrefsObservers.forEach(notifyCallback)
}

/**
 * A listener interface for observing changes to shared preferences.
 *
 * This interface should be implemented by classes that wish to be notified when specific shared preferences change.
 * Implementing classes can register themselves as observers and respond to changes in preferences by overriding the
 * methods provided in this interface. Each method corresponds to a specific preference and is called whenever the
 * associated preference changes.
 *
 * @see Observer
 * @see SharedPrefsManager
 * @see com.hifnawy.caffeinate.services.ServiceStatusObserver
 */
interface SharedPrefsObserver : Observer {

    /**
     * Called when the "All Permissions Granted" preference changes.
     *
     * @param isAllPermissionsGranted [Boolean] `true` if all permissions are granted, `false` otherwise.
     */
    fun onIsAllPermissionsGrantedUpdated(isAllPermissionsGranted: Boolean) = Unit

    /**
     * Called when the "Overlay Enabled" preference changes.
     *
     * @param isOverlayEnabled [Boolean] `true` if the overlay is enabled, `false` otherwise.
     */
    fun onIsOverlayEnabledUpdated(isOverlayEnabled: Boolean) = Unit

    /**
     * Called when the "Dimming Enabled" preference changes.
     *
     * @param isDimmingEnabled [Boolean] `true` if dimming is enabled, `false` otherwise.
     */
    fun onIsDimmingEnabledUpdated(isDimmingEnabled: Boolean) = Unit

    /**
     * Called when the "While Locked Enabled" preference changes.
     *
     * @param isWhileLockedEnabled [Boolean] `true` if the "While Locked" feature is enabled, `false` otherwise.
     */
    fun onIsWhileLockedEnabledUpdated(isWhileLockedEnabled: Boolean) = Unit
}