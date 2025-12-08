package com.hifnawy.caffeinate.controller

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.controller.SharedPrefsManager.ContrastLevel.HIGH
import com.hifnawy.caffeinate.controller.SharedPrefsManager.ContrastLevel.MEDIUM
import com.hifnawy.caffeinate.controller.SharedPrefsManager.ContrastLevel.STANDARD
import com.hifnawy.caffeinate.controller.SharedPrefsManager.SharedPrefsKeys.ALL_PERMISSIONS_GRANTED
import com.hifnawy.caffeinate.controller.SharedPrefsManager.SharedPrefsKeys.CONTRAST_LEVEL
import com.hifnawy.caffeinate.controller.SharedPrefsManager.SharedPrefsKeys.ENABLE_DIMMING
import com.hifnawy.caffeinate.controller.SharedPrefsManager.SharedPrefsKeys.ENABLE_MATERIAL_YOU
import com.hifnawy.caffeinate.controller.SharedPrefsManager.SharedPrefsKeys.ENABLE_OVERLAY
import com.hifnawy.caffeinate.controller.SharedPrefsManager.SharedPrefsKeys.ENABLE_PICTURE_IN_PICTURE
import com.hifnawy.caffeinate.controller.SharedPrefsManager.SharedPrefsKeys.ENABLE_SHOW_STATUS_IN_QUICK_TILE_TITLE
import com.hifnawy.caffeinate.controller.SharedPrefsManager.SharedPrefsKeys.ENABLE_WHILE_LOCKED
import com.hifnawy.caffeinate.controller.SharedPrefsManager.SharedPrefsKeys.IS_SERVICE_RUNNING
import com.hifnawy.caffeinate.controller.SharedPrefsManager.SharedPrefsKeys.LAST_REMAINING_TIMEOUT
import com.hifnawy.caffeinate.controller.SharedPrefsManager.SharedPrefsKeys.THEME
import com.hifnawy.caffeinate.controller.SharedPrefsManager.SharedPrefsKeys.TIMEOUT_CHECK_BOXES
import com.hifnawy.caffeinate.controller.SharedPrefsManager.SharedPrefsKeys.WIDGET_CONFIGURATION
import com.hifnawy.caffeinate.controller.SharedPrefsManager.Theme.DARK
import com.hifnawy.caffeinate.controller.SharedPrefsManager.Theme.LIGHT
import com.hifnawy.caffeinate.controller.SharedPrefsManager.Theme.SYSTEM_DEFAULT
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toLocalizedFormattedTime
import com.hifnawy.caffeinate.utils.SharedPreferencesExtensionFunctions.getSerializableList
import com.hifnawy.caffeinate.utils.SharedPreferencesExtensionFunctions.getSerializableMap
import com.hifnawy.caffeinate.utils.SharedPreferencesExtensionFunctions.putSerializableList
import com.hifnawy.caffeinate.utils.SharedPreferencesExtensionFunctions.putSerializableMap
import com.hifnawy.caffeinate.view.CheckBoxItem
import com.hifnawy.caffeinate.view.WidgetConfiguration
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
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
     * @property ENABLE_SHOW_STATUS_IN_QUICK_TILE_TITLE A [Boolean] value indicating whether the application status should be shown in the quick tile title.
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
         * A [ContrastLevel] value indicating the current contrast level of the application.
         */
        CONTRAST_LEVEL,

        /**
         * A [Boolean] value indicating whether Material You design elements should be enabled.
         */
        ENABLE_MATERIAL_YOU,

        /**
         * A [Boolean] value indicating whether the application status should be shown in the quick tile title.
         */
        ENABLE_SHOW_STATUS_IN_QUICK_TILE_TITLE,

        /**
         * A [Boolean] value indicating whether the overlay should be enabled.
         */
        ENABLE_OVERLAY,

        /**
         * A [Boolean] value indicating whether the application should be enabled in picture-in-picture mode.
         */
        ENABLE_PICTURE_IN_PICTURE,

        /**
         * A [Boolean] value indicating whether the screen should be dimmed while the service is running.
         */
        ENABLE_DIMMING,

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
     * An enumeration of the contrast level options that are available to the user.
     *
     * Contrast levels control the overall visual contrast of the application. The contrast level can be set to one of the following values:
     * @property STANDARD The standard contrast level.
     * @property MEDIUM A medium contrast level.
     * @property HIGH A high contrast level.
     */
    enum class ContrastLevel {

        /**
         * The standard contrast level.
         */
        STANDARD,

        /**
         * A medium contrast level.
         */
        MEDIUM,

        /**
         * A high contrast level.
         */
        HIGH;

        /**
         * Whether the contrast level is the [STANDARD] contrast level.
         *
         * This is a shorthand for [SharedPrefsManager.ContrastLevel] == [STANDARD].
         *
         * @return [Boolean] `true` if the contrast level is the [STANDARD] contrast level, `false` otherwise.
         */
        val isStandard
            get() = this == STANDARD

        /**
         * Whether the contrast level is not the [STANDARD] contrast level. So either [MEDIUM] or [HIGH].
         *
         * This is a shorthand for [SharedPrefsManager.ContrastLevel] != [STANDARD].
         *
         * @return [Boolean] `true` if the contrast level is the [STANDARD] contrast level, `false` otherwise.
         */
        val isNotStandard
            get() = this != STANDARD
    }

    /**
     * An enumeration of the theme options that are available to the user.
     *
     * Themes control the overall visual appearance of the application. The theme can be set to one of the following values:
     * @property SYSTEM_DEFAULT Follows the device's system theme.
     * @property LIGHT A light theme.
     * @property DARK A dark theme.
     *
     * @property mode [Int] The value of the theme, which is used to set the theme for the application.
     */
    enum class Theme(var mode: Int) {

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
     * @return [List] A list of [Duration] objects representing the available timeouts.
     */
    private val timeouts by lazy { listOf(30.seconds, 5.minutes, 10.minutes, 15.minutes, 30.minutes, 60.minutes, Duration.INFINITE) }

    /**
     * Retrieves or sets whether all necessary permissions are granted.
     *
     * @return [Boolean] `true` if all necessary permissions are granted, `false` otherwise.
     */
    var isAllPermissionsGranted: Boolean
        get() = sharedPreferences.getBoolean(ALL_PERMISSIONS_GRANTED.name, false)
        set(value) {
            sharedPreferences.edit { putBoolean(ALL_PERMISSIONS_GRANTED.name, value) }
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
        set(value) = sharedPreferences.edit { putString(THEME.name, value.name) }

    /**
     * Retrieves or sets the contrast level of the application.
     *
     * This property is used to get or update the contrast level preference stored in shared preferences.
     * It allows the application to persist the user's contrast level choice across sessions.
     *
     * @return [ContrastLevel] The current contrast level set in the application.
     *
     * @see SharedPrefsManager.ContrastLevel
     */
    var contrastLevel: ContrastLevel
        get() = ContrastLevel.valueOf(sharedPreferences.getString(CONTRAST_LEVEL.name, STANDARD.name) ?: STANDARD.name)
        set(value) = sharedPreferences.edit { putString(CONTRAST_LEVEL.name, value.name) }

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
        set(value) = sharedPreferences.edit { putBoolean(ENABLE_MATERIAL_YOU.name, value) }

    /**
     * Retrieves or sets whether the quick tile status should be shown in the title.
     *
     * This property is used to determine if the quick tile status should be shown in the title.
     * It allows the application to show or hide the quick tile status based on the user's preference stored in shared preferences.
     *
     * @return [Boolean] `true` if the quick tile status should be shown in the title, `false` otherwise.
     */
    var isShowStatusInQuickTileTitleEnabled: Boolean
        get() = sharedPreferences.getBoolean(ENABLE_SHOW_STATUS_IN_QUICK_TILE_TITLE.name, false)
        set(value) {
            sharedPreferences.edit { putBoolean(ENABLE_SHOW_STATUS_IN_QUICK_TILE_TITLE.name, value) }
            notifySharedPrefsObservers { observer -> observer.onIsShowQuickTileStatusInTitleEnabledUpdated(value) }
        }

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
            sharedPreferences.edit { putBoolean(ENABLE_OVERLAY.name, value) }
            notifySharedPrefsObservers { observer -> observer.onIsOverlayEnabledUpdated(value) }
        }

    /**
     * Retrieves or sets whether picture-in-picture mode is enabled.
     *
     * This property is used to get or update the preference of whether picture-in-picture mode should be enabled.
     * It allows the application to persist the user's preference across sessions.
     *
     * @return [Boolean] `true` if picture-in-picture mode is enabled, `false` otherwise.
     */
    var isPictureInPictureEnabled: Boolean
        get() = sharedPreferences.getBoolean(ENABLE_PICTURE_IN_PICTURE.name, false)
        set(value) = sharedPreferences.edit { putBoolean(ENABLE_PICTURE_IN_PICTURE.name, value) }

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
            sharedPreferences.edit { putBoolean(ENABLE_DIMMING.name, value)}
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
            sharedPreferences.edit { putBoolean(ENABLE_WHILE_LOCKED.name, value) }
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
    val timeoutCheckBoxes by TimeoutCheckBoxesDelegate(sharedPreferences, TIMEOUT_CHECK_BOXES.name, timeouts, caffeinateApplication)

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
        set(value) = sharedPreferences.edit { putSerializableMap(WIDGET_CONFIGURATION.name, value) }

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
            val status = caffeinateApplication.lastStatusUpdate
            val timeout = when {
                status is ServiceStatus.Running && value -> status.remaining.inWholeSeconds
                else                                     -> -1L
            }

            sharedPreferences.edit { putBoolean(IS_SERVICE_RUNNING.name, value) }
            sharedPreferences.edit { putLong(LAST_REMAINING_TIMEOUT.name, timeout) }
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
 * @see com.hifnawy.caffeinate.controller.ServiceStatusObserver
 */
interface SharedPrefsObserver : Observer {

    /**
     * Called when the "All Permissions Granted" preference changes.
     *
     * @param isAllPermissionsGranted [Boolean] `true` if all permissions are granted, `false` otherwise.
     */
    fun onIsAllPermissionsGrantedUpdated(isAllPermissionsGranted: Boolean) = Unit

    /**
     * Called when the "Show Quick Tile Status in Title" preference changes.
     *
     * @param isShowQuickTileStatusInTitleEnabled [Boolean] `true` if the "Show Quick Tile Status in Title" feature is enabled, `false` otherwise.
     */
    fun onIsShowQuickTileStatusInTitleEnabledUpdated(isShowQuickTileStatusInTitleEnabled: Boolean) = Unit

    /**
     * Called when the "Overlay Enabled" preference changes.
     *
     * @param isOverlayEnabled [Boolean] `true` if the overlay is enabled, `false` otherwise.
     */
    fun onIsOverlayEnabledUpdated(isOverlayEnabled: Boolean) = Unit

    /**
     * Called when the "Picture in Picture Enabled" preference changes.
     *
     * @param isPictureInPictureEnabled [Boolean] `true` if the "Picture in Picture" feature is enabled, `false` otherwise.
     */
    fun onIsPictureInPictureEnabledUpdated(isPictureInPictureEnabled: Boolean) = Unit

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

/**
 * A delegate class for reading and writing a list of [CheckBoxItem]s to SharedPreferences.
 *
 * This class uses a delegate property to read and write a list of [CheckBoxItem]s to SharedPreferences.
 * It caches the list of [CheckBoxItem]s in memory and only reads from or writes to SharedPreferences
 * when the value is changed.
 *
 * @property sharedPreferences the SharedPreferences to read from and write to.
 * @property key the key to use when writing/reading to/from SharedPreferences.
 * @property initialTimeouts the list of timeouts to use as the initial value for the property.
 * @property context the context to use for formatting the timeouts.
 *
 * @author AbdAlMoniem AlHifnawy
 */
private class TimeoutCheckBoxesDelegate(

        /**
         * The SharedPreferences to read from and write to.
         *
         * This property is a val, meaning it cannot be reassigned. It is also a private val, meaning it can only be accessed within this class.
         */
        private val sharedPreferences: SharedPreferences,

        /**
         * The key to use when writing/reading to/from SharedPreferences.
         *
         * This property is a val, meaning it cannot be reassigned. It is also a private val, meaning it can only be accessed within this class.
         *
         * @see SharedPreferences
         * @see TimeoutCheckBoxesDelegate
         */
        private val key: String,

        /**
         * The initial list of timeouts to use as the initial value for the property.
         *
         * This property is a val, meaning it cannot be reassigned. It is also a private val, meaning it can only be accessed within this class.
         *
         * @see List
         * @see Duration
         */
        private val initialTimeouts: List<Duration>,

        /**
         * The context to use for formatting the timeouts.
         *
         * This property is a val, meaning it cannot be reassigned. It is also a private val, meaning it can only be accessed within this class.
         *
         * @see Context
         */
        private val context: Context
) : ReadWriteProperty<Any, MutableList<CheckBoxItem>> {

    /**
     * The cached list of [CheckBoxItem]s.
     *
     * This property is used to cache the list of [CheckBoxItem]s in memory, so that it is not necessary to read from SharedPreferences every time the property is accessed.
     *
     * This property is a var, meaning it can be reassigned. However, it is private, meaning it can only be accessed within this class.
     *
     * @see MutableList
     * @see CheckBoxItem
     */
    private lateinit var timeoutCheckBoxItems: MutableList<CheckBoxItem>

    /**
     * Gets the value of the property.
     *
     * This method returns the value of the property, which is a list of [CheckBoxItem]s.
     *
     * The value is cached in memory, so that it is not necessary to read from SharedPreferences every time the property is accessed.
     * If the value is not yet cached, it is loaded from SharedPreferences.
     *
     * @param thisRef the object that the property is being accessed on.
     * @param property the property that is being accessed.
     * @return [MutableList] the value of the property, which is a [MutableList] of [CheckBoxItem]s.
     */
    override fun getValue(thisRef: Any, property: KProperty<*>): MutableList<CheckBoxItem> {
        if (!::timeoutCheckBoxItems.isInitialized) timeoutCheckBoxItems = load()

        return MutableListInterceptor(timeoutCheckBoxItems, ::save)
    }

    /**
     * Sets the value of the property.
     *
     * This method sets the value of the property, which is a list of [CheckBoxItem]s.
     *
     * The value is cached in memory, so that it is not necessary to write to SharedPreferences every time the property is assigned.
     * If the value is not yet cached, it is saved to SharedPreferences.
     *
     * @param thisRef the object that the property is being accessed on.
     * @param property the property that is being accessed.
     * @param value the value of the property, which is a list of [CheckBoxItem]s.
     */
    override fun setValue(thisRef: Any, property: KProperty<*>, value: MutableList<CheckBoxItem>) {
        timeoutCheckBoxItems = value

        save(value)
    }

    /**
     * Loads the list of [CheckBoxItem]s from SharedPreferences.
     *
     * If the list is already cached in memory, it returns the cached list.
     * If the list is not cached, it loads the list from SharedPreferences.
     * If the list is not available in SharedPreferences, it returns the default list of [CheckBoxItem]s.
     *
     * @return [CheckBoxItem] the list of [CheckBoxItem]s.
     */
    private fun load() = when {
        sharedPreferences.contains(key) -> sharedPreferences.getSerializableList<MutableList<CheckBoxItem>>(key)
        else                            -> initialTimeouts.localizedCheckBoxItems
    }

    /**
     * Saves the list of [CheckBoxItem]s to SharedPreferences.
     *
     * @param list [MutableList] the list of [CheckBoxItem]s to be saved.
     */
    private fun save(list: List<CheckBoxItem>) = sharedPreferences.edit { putSerializableList(key, list.localized) }

    /**
     * Returns a new list of [CheckBoxItem]s, where each item's text is localized to the current locale.
     *
     * This method takes a list of [CheckBoxItem]s as an argument and returns a new list of [CheckBoxItem]s.
     * Each item in the returned list is a [CheckBoxItem] with the text localized to the current locale.
     *
     * @return [MutableList] a new [MutableList] of [CheckBoxItem]s, where each item's text is localized.
     */
    private val List<CheckBoxItem>.localized
        get() = map { checkBoxItem -> checkBoxItem.copy(text = checkBoxItem.duration.toLocalizedFormattedTime(context)) }.toMutableList()

    /**
     * Returns a new list of [CheckBoxItem]s, where each item's text is localized to the current locale.
     *
     * This method takes a list of [Duration]s as an argument and returns a new list of [CheckBoxItem]s.
     * Each item in the returned list is a [CheckBoxItem] with the text localized to the current locale.
     *
     * @return [MutableList] a new [MutableList] of [CheckBoxItem]s, where each item's text is localized.
     */
    private val List<Duration>.localizedCheckBoxItems
        get() = map { timeout ->
            CheckBoxItem(text = timeout.toLocalizedFormattedTime(context), isChecked = true, isEnabled = true, duration = timeout)
        }.toMutableList()

    /**
     * An implementation of [MutableList] that wraps a delegate and intercepts the
     * add and remove operations to notify a listener.
     *
     * This class is used to create a list that can be modified by the user in the
     * UI, and notify the [SharedPrefsManager] when the list is modified, so that
     * the list can be saved to the preferences.
     *
     * @param list the delegate list that will be modified.
     * @param onModify the function that will be called when the list is modified.
     *
     * @author AbdAlMoniem AlHifnawy
     */
    @Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
    private class MutableListInterceptor(

            /**
             * A list of [CheckBoxItem]s that is being intercepted for modifications.
             *
             * This list serves as a delegate for [MutableList] operations and notifies a listener
             * whenever the list is modified. This is useful for persisting changes or updating the UI
             * based on list operations like addition or removal of items.
             *
             * @property list The underlying mutable list of [CheckBoxItem]s being intercepted.
             * @property onModify A callback function that is invoked with the list whenever it is modified.
             */
            private val list: MutableList<CheckBoxItem>,

            /**
             * A callback function that is invoked with the list whenever it is modified.
             *
             * This callback is used to notify the [SharedPrefsManager] when the list is modified,
             * so that the list can be saved to the preferences.
             */
            private val onModify: (MutableList<CheckBoxItem>) -> Unit
    ) : MutableList<CheckBoxItem> by list {

        /**
         * Adds the specified [CheckBoxItem] to the list.
         *
         * This method appends the given [CheckBoxItem] to the end of the list. It then triggers
         * the [onModify] callback to notify observers of the change.
         *
         * @param element [CheckBoxItem] The element to be added to this list.
         * @return [Boolean] `true` if the list changed as a result of the call, `false` otherwise.
         */
        override fun add(element: CheckBoxItem): Boolean {
            val result = list.add(element)

            onModify(list)

            return result
        }

        /**
         * Adds all of the elements in the specified collection to this list.
         *
         * This method appends all of the elements in the specified collection to the end of this list,
         * in the order that they are returned by the specified collection's iterator. It then triggers
         * the [onModify] callback to notify observers of the change.
         *
         * @param elements [Collection] The collection containing elements to be added to this list.
         * @return [Boolean] `true` if this list changed as a result of the call, `false` otherwise.
         */
        override fun addAll(elements: Collection<CheckBoxItem>): Boolean {
            val result = list.addAll(elements)

            onModify(list)

            return result
        }

        /**
         * Removes all elements from this list.
         *
         * This method clears all [CheckBoxItem]s from the list and triggers the [onModify] callback
         * to notify observers of the change. After this operation, the list will be empty.
         */
        override fun clear() {
            list.clear()

            onModify(list)
        }

        /**
         * Removes the element from this list, if it is present.
         *
         * This method is overridden to notify the [onModify] callback after the removal operation.
         *
         * @param element The element to be removed from this list, if present.
         * @return [Boolean] `true` if this list contained the specified element, `false` otherwise.
         */
        override fun remove(element: CheckBoxItem): Boolean {
            val result = list.remove(element)

            onModify(list)

            return result
        }

        /**
         * Removes the element at the specified position in this list and returns it.
         *
         * This method removes the [CheckBoxItem] at the given index from the list and triggers
         * the [onModify] callback to notify observers of the change.
         *
         * @param index [Int] The index of the element to remove.
         * @return [CheckBoxItem] The element that was removed from the list.
         * @throws IndexOutOfBoundsException If the index is out of range (index < 0 || index >= size).
         */
        override fun removeAt(index: Int): CheckBoxItem {
            val result = list.removeAt(index)

            onModify(list)

            return result
        }

        /**
         * Replaces the element at the specified position in this list with the specified element.
         *
         * This method updates the element at the given index in the list with the provided
         * [CheckBoxItem] and triggers the [onModify] callback to notify observers of the change.
         *
         * @param index [Int] The index of the element to replace.
         * @param element [CheckBoxItem] The element to be stored at the specified position.
         * @return [CheckBoxItem] The element previously at the specified position.
         */
        override fun set(index: Int, element: CheckBoxItem): CheckBoxItem {
            val result = list.set(index, element)

            onModify(list)

            return result
        }
    }
}