package com.hifnawy.caffeinate

import android.app.Application
import android.content.Context
import android.os.Build
import com.hifnawy.caffeinate.services.QuickTileService
import com.hifnawy.caffeinate.ui.CheckBoxItem
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toFormattedTime
import com.hifnawy.caffeinate.utils.SharedPrefsManager
import java.util.Locale
import kotlin.time.Duration
import timber.log.Timber as Log

class CaffeinateApplication : Application() {

    private val sharedPreferences by lazy { SharedPrefsManager(this) }
    lateinit var timeoutCheckBoxes: MutableList<CheckBoxItem>
        private set
    val firstTimeout: Duration
        get() = timeoutCheckBoxes.first { checkBoxItem -> checkBoxItem.isChecked }.duration
    val lastTimeout: Duration
        get() = timeoutCheckBoxes.last { checkBoxItem -> checkBoxItem.isChecked }.duration
    val prevTimeout: Duration
        get() {
            val timeoutCheckBox = timeoutCheckBoxes.first { timeoutCheckBox -> timeoutCheckBox.duration == timeout }
            val index = timeoutCheckBoxes.indexOf(timeoutCheckBox)
            var prevIndex = (index - 1 + timeoutCheckBoxes.size) % timeoutCheckBoxes.size

            while (!timeoutCheckBoxes[prevIndex].isChecked) prevIndex = (prevIndex - 1 + timeoutCheckBoxes.size) % timeoutCheckBoxes.size

            return timeoutCheckBoxes[prevIndex].duration
        }
    val nextTimeout: Duration
        get() {
            val timeoutCheckBox = timeoutCheckBoxes.first { timeoutCheckBox -> timeoutCheckBox.duration == timeout }
            val index = timeoutCheckBoxes.indexOf(timeoutCheckBox)
            var nextIndex = (index + 1) % timeoutCheckBoxes.size

            while (!timeoutCheckBoxes[nextIndex].isChecked) nextIndex = (nextIndex + 1) % timeoutCheckBoxes.size

            return timeoutCheckBoxes[nextIndex].duration
        }
    var timeout: Duration = sharedPreferences.timeouts.first()
    var keepAwakeServiceObservers = mutableListOf<ServiceStatusObserver>()
    var sharedPrefsObservers = mutableListOf<SharedPrefsManager.SharedPrefsChangedListener>()
    var lastStatusUpdate: ServiceStatus = ServiceStatus.Stopped
        set(status) {
            field = status

            notifyKeepAwakeServiceObservers(status)
        }
    lateinit var localizedApplicationContext: Context
        private set

    private fun notifyKeepAwakeServiceObservers(status: ServiceStatus) {
        if (status is ServiceStatus.Stopped) timeout = firstTimeout

        keepAwakeServiceObservers.forEach { observer -> observer.onServiceStatusUpdate(status) }

        QuickTileService.requestTileStateUpdate(localizedApplicationContext)
    }

    private fun getCurrentLocale(): Locale = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> resources.configuration.locales[0]
        else                                                  -> Locale.getDefault()
    }

    fun applyLocaleConfiguration() {
        val configuration = resources.configuration
        val locale = getCurrentLocale()

        applicationLocale = locale

        Locale.setDefault(locale)
        configuration.setLocale(locale)
        localizedApplicationContext = createConfigurationContext(configuration)
    }

    override fun onCreate() {
        super.onCreate()

        applyLocaleConfiguration()

        if (BuildConfig.DEBUG) {
            Log.plant(Log.DebugTree())
        }

        timeoutCheckBoxes = sharedPreferences.timeoutCheckBoxes
        timeout = firstTimeout
    }

    companion object {

        lateinit var applicationLocale: Locale
            private set
    }
}

interface ServiceStatusObserver {

    fun onServiceStatusUpdate(status: ServiceStatus)
}

sealed class ServiceStatus {
    class Running(val remaining: Duration) : ServiceStatus() {

        override fun toString() = "${Running::class.java.simpleName}(${remaining.toFormattedTime()})"
    }

    data object Stopped : ServiceStatus()
}