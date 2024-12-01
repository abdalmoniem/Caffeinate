package com.hifnawy.caffeinate

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.LayoutDirection
import androidx.core.text.layoutDirection
import com.hifnawy.caffeinate.controller.QuickTileService
import com.hifnawy.caffeinate.controller.ServiceStatus
import com.hifnawy.caffeinate.controller.ServiceStatusObserver
import com.hifnawy.caffeinate.view.CheckBoxItem
import com.hifnawy.caffeinate.utils.LogDebugTree
import com.hifnawy.caffeinate.controller.SharedPrefsManager
import com.hifnawy.caffeinate.controller.SharedPrefsObserver
import com.hifnawy.caffeinate.view.Widget
import java.util.Locale
import kotlin.time.Duration
import timber.log.Timber as Log

/**
 * The main application class for the Caffeinate app.
 *
 * This class extends [Application] and is responsible for initializing global state,
 * setting up shared preferences, and managing application-wide resources.
 *
 * @property sharedPreferences [SharedPrefsManager] A lazily initialized instance of [SharedPrefsManager] used to access and modify shared preferences.
 * @property firstTimeout [Duration] The first timeout duration selected by the user from the list of available durations.
 * @property lastTimeout [Duration] The last timeout duration selected by the user from the list of available durations.
 * @property prevTimeout [Duration] The previously selected timeout duration from the list of available durations.
 * @property nextTimeout [Duration] The next timeout duration to use when the KeepAwakeService is running.
 * @property timeout [Duration] The currently selected timeout duration.
 * @property keepAwakeServiceObservers [List] A list of [ServiceStatusObserver] objects that are notified when the KeepAwakeService's status changes.
 * @property sharedPrefsObservers [List] A list of [SharedPrefsObserver] objects that are
 * notified when shared preferences change.
 * @property lastStatusUpdate [ServiceStatus] The last status update received from the KeepAwakeService.
 * @property timeoutCheckBoxes [List] A list of [CheckBoxItem] objects representing the available timeout durations.
 * @property localizedApplicationContext [Context] The context of the application with the current locale set.
 *
 * @see Application
 * @see SharedPrefsManager
 *
 * @author AbdAlMoniem AlHifnawy
 */
class CaffeinateApplication : Application() {

    /**
     * A [SharedPrefsManager] instance that is used to access and modify the application's shared preferences.
     *
     * This instance is lazily initialized, meaning it is only initialized when it is first accessed.
     *
     * @see SharedPrefsManager
     */
    private val sharedPreferences by lazy { SharedPrefsManager(this) }

    /**
     * The first timeout duration that was selected by the user.
     *
     * This is the first timeout duration that was selected by the user in the list of available timeout durations. When the user selects a new
     * timeout duration, the [KeepAwakeService][com.hifnawy.caffeinate.controller.KeepAwakeService] will use this timeout duration as the new timeout
     * duration.
     *
     * @return [Duration] the first timeout duration that was selected by the user, or [Duration.INFINITE] if no timeout duration was selected.
     */
    val firstTimeout: Duration
        get() = timeoutCheckBoxes.first { checkBoxItem -> checkBoxItem.isChecked }.duration

    /**
     * The last timeout duration that was selected by the user.
     *
     * This is the timeout duration that was selected by the user in the list of available timeout durations. When the user selects a new timeout
     * duration, the [KeepAwakeService][com.hifnawy.caffeinate.controller.KeepAwakeService] will use this timeout duration as the new timeout duration.
     *
     * @return [Duration] the last timeout duration that was selected by the user.
     */
    val lastTimeout: Duration
        get() = timeoutCheckBoxes.last { checkBoxItem -> checkBoxItem.isChecked }.duration

    /**
     * The previously selected timeout duration.
     *
     * This is the duration that was previously selected in the list of available timeout durations. When the current timeout duration is finished,
     * the [KeepAwakeService][com.hifnawy.caffeinate.controller.KeepAwakeService] will use this timeout duration as the new timeout duration.
     *
     * @return [Duration] the previously selected timeout duration.
     */
    val prevTimeout: Duration
        get() {
            val timeoutCheckBox = timeoutCheckBoxes.first { timeoutCheckBox -> timeoutCheckBox.duration == timeout }
            val index = timeoutCheckBoxes.indexOf(timeoutCheckBox)
            var prevIndex = (index - 1 + timeoutCheckBoxes.size) % timeoutCheckBoxes.size

            while (!timeoutCheckBoxes[prevIndex].isChecked) prevIndex = (prevIndex - 1 + timeoutCheckBoxes.size) % timeoutCheckBoxes.size

            return timeoutCheckBoxes[prevIndex].duration
        }

    /**
     * The next timeout duration that will be used when the KeepAwakeService is running.
     *
     * This is the timeout duration that is selected in the list of available timeout durations after the current timeout duration.
     *
     * When the current timeout duration is finished, the KeepAwakeService will use this timeout duration as the new timeout duration.
     *
     * @return [Duration] the next timeout duration that will be used when the KeepAwakeService is running.
     */
    val nextTimeout: Duration
        get() {
            val timeoutCheckBox = timeoutCheckBoxes.first { timeoutCheckBox -> timeoutCheckBox.duration == timeout }
            val index = timeoutCheckBoxes.indexOf(timeoutCheckBox)
            var nextIndex = (index + 1) % timeoutCheckBoxes.size

            while (!timeoutCheckBoxes[nextIndex].isChecked) nextIndex = (nextIndex + 1) % timeoutCheckBoxes.size

            return timeoutCheckBoxes[nextIndex].duration
        }

    /**
     * The currently selected timeout duration.
     *
     * This is the duration that the [KeepAwakeService][com.hifnawy.caffeinate.controller.KeepAwakeService] is currently running for. When the service
     * is running, this value is updated to reflect the remaining duration for which the service is running.
     *
     * When the service is not running, this value is the first timeout duration that is selected in the list of available timeout durations.
     *
     * @see com.hifnawy.caffeinate.controller.KeepAwakeService
     * @see SharedPrefsManager.timeouts
     */
    var timeout: Duration = sharedPreferences.timeouts.first()

    /**
     * A list of observers that are notified whenever the status of the KeepAwakeService changes.
     *
     * This list is used to store all observers that are registered to receive notifications whenever the status of the KeepAwakeService changes.
     *
     * When the status of the KeepAwakeService changes, the [CaffeinateApplication] notifies all observers in this list of the change.
     *
     * @see ServiceStatusObserver
     * @see notifyKeepAwakeServiceObservers
     */
    var keepAwakeServiceObservers = mutableListOf<ServiceStatusObserver>()

    /**
     * A list of observers that are notified whenever a shared preference changes.
     *
     * This list is used to store all observers that are registered to receive notifications whenever a shared preference changes.
     *
     * When a shared preference changes, the [SharedPrefsManager] notifies all observers in this list of the change.
     *
     * @see SharedPrefsManager.notifySharedPrefsObservers
     */
    var sharedPrefsObservers = mutableListOf<SharedPrefsObserver>()

    /**
     * The last status update received from the [KeepAwakeService][com.hifnawy.caffeinate.controller.KeepAwakeService].
     *
     * This value is updated whenever the [KeepAwakeService][com.hifnawy.caffeinate.controller.KeepAwakeService] sends a status update to the
     * [CaffeinateApplication].
     *
     * The [KeepAwakeService][com.hifnawy.caffeinate.controller.KeepAwakeService] sends a status update whenever its status changes, such as when it is
     * started or stopped, or when its timeout duration changes.
     *
     * @see ServiceStatus
     * @see com.hifnawy.caffeinate.controller.KeepAwakeService
     * @see notifyKeepAwakeServiceObservers
     */
    var lastStatusUpdate: ServiceStatus = ServiceStatus.Stopped
        set(status) {
            field = status

            when (status) {
                is ServiceStatus.Running -> if (status.onRemainingUpdated == null) {
                    notifyKeepAwakeServiceObservers(status)
                    status.onRemainingUpdated = ServiceStatus.Running.RemainingValueObserver { notifyKeepAwakeServiceObservers(status) }
                }

                is ServiceStatus.Stopped -> notifyKeepAwakeServiceObservers(status)
            }
        }

    /**
     * A list of [CheckBoxItem]s that represent the timeout durations the user can select from.
     *
     * This list is stored in the app's SharedPreferences and is loaded when the app is launched.
     *
     * The list of timeout durations is used to populate the [RecyclerView][androidx.recyclerview.widget.RecyclerView] in the "Choose timeout" dialog,
     * which is shown when the user clicks the "Choose timeout" button in the app's UI.
     *
     * The list is also used to determine the next timeout duration to use when the current timeout duration is finished.
     *
     * @see CheckBoxItem
     * @see android.content.SharedPreferences
     * @see androidx.recyclerview.widget.RecyclerView
     */
    lateinit var timeoutCheckBoxes: MutableList<CheckBoxItem>
        private set

    /**
     * The context of the application localized to the user's current locale.
     *
     * This context is used to get localized strings, which are used in the application's UI.
     *
     * @see Locale
     * @see Context
     */
    lateinit var localizedApplicationContext: Context
        private set

    /**
     * Notifies all [ServiceStatusObserver]s in the [keepAwakeServiceObservers] list about a change in the service's status.
     *
     * This method is called when the service's status is updated. It iterates over the list of [ServiceStatusObserver]s and calls
     * the [onServiceStatusUpdate][ServiceStatusObserver.onServiceStatusUpdated] method on each observer.
     *
     * It also updates the [QuickTileService] and [Widget] if there are any added Quick Tiles or Widgets.
     *
     * @param status [ServiceStatus] the new status of the service
     *
     * @see ServiceStatusObserver
     * @see ServiceStatus
     * @see keepAwakeServiceObservers
     */
    private fun notifyKeepAwakeServiceObservers(status: ServiceStatus) {
        Log.d("notifying observers...")

        if (status is ServiceStatus.Stopped) timeout = firstTimeout

        keepAwakeServiceObservers.forEach { observer ->
            Log.d("observer: <${observer::class.simpleName}> $observer")
            observer.onServiceStatusUpdated(status)
        }

        QuickTileService.requestTileStateUpdate(this)
        Widget.updateAllWidgets(this)

        Log.d("observers notified!")
    }

    /**
     * Returns the current locale set by the user in the system settings.
     *
     * If the device is running Android 13 (API level 33) or later, the current locale is retrieved
     * from the [resources.configuration.locales][android.content.res.Configuration.getLocales] list. Otherwise, the current locale is retrieved
     * from the [getDefault][Locale.getDefault] method.
     *
     * @return the current locale set by the user in the system settings.
     *
     * @see android.content.res.Configuration
     * @see Locale.getDefault
     */
    private val currentLocale: Locale
        get() = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> resources.configuration.locales[0]
            else                                                  -> Locale.getDefault()
        }

    /**
     * Applies the locale configuration set by the user.
     *
     * This method sets the locale of the application to the one set by the user in the settings.
     * The locale is used to determine the language and formatting of the application's UI.
     *
     * @see SharedPrefsManager
     * @see Locale
     */
    fun applyLocaleConfiguration() {
        val configuration = resources.configuration

        applicationLocale = currentLocale

        Locale.setDefault(currentLocale)

        @Suppress("AppBundleLocaleChanges")
        configuration.setLocale(currentLocale)
        localizedApplicationContext = createConfigurationContext(configuration)

        timeoutCheckBoxes = sharedPreferences.timeoutCheckBoxes
    }

    /**
     * Called when the application is starting.
     *
     * This is where the application initializes global state, sets up logging for debugging,
     * applies locale configuration, and sets the initial timeout value.
     * This method is called before any activity, service, or receiver objects (excluding content providers)
     * have been created.
     *
     * Note: If the application is being re-initialized after being shut down, this method is
     * called with the saved state that the application previously supplied in onSaveInstanceState.
     */
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Log.plant(LogDebugTree())
        }

        applyLocaleConfiguration()

        timeout = firstTimeout
    }

    /**
     * A companion object for holding application-wide constants and configurations.
     *
     * This object contains the application's default locale and provides access to
     * localized application context. The locale is initialized during application startup
     * and can be accessed throughout the application.
     *
     * @property applicationLocale [Locale] The current locale used by the application.
     * @property isRTL [Boolean] `true` if the current locale is right-to-left, `false` otherwise.
     */
    companion object {

        /**
         * The current locale used by the application.
         *
         * This property is initialized during application startup and can be accessed
         * throughout the application. It is used to set the locale for the application's
         * resources and configuration.
         *
         * @see Locale
         */
        lateinit var applicationLocale: Locale
            private set

        /**
         * Checks if the current locale is right-to-left.
         *
         * @return [Boolean] `true` if the current locale is right-to-left, `false` otherwise.
         */
        val isRTL: Boolean
            get() = applicationLocale.layoutDirection == LayoutDirection.RTL
    }
}