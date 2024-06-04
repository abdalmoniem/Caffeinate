package com.hifnawy.caffeinate

import android.app.Application
import android.content.Context
import android.os.Build
import com.hifnawy.caffeinate.services.QuickTileService
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toFormattedTime
import com.hifnawy.caffeinate.utils.SharedPrefsManager
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import timber.log.Timber as Log

class CaffeinateApplication : Application() {

    private val durations by lazy { listOf(30.seconds, 5.minutes, 10.minutes, 15.minutes, 30.minutes, 60.minutes, Duration.INFINITE) }
    val firstTimeout by lazy { durations.first() }
    val prevTimeout: Duration
        get() {
            val index = durations.indexOf(timeout)
            val prevIndex = (index - 1 + durations.size) % durations.size

            return durations[prevIndex]
        }
    val nextTimeout: Duration
        get() {
            val index = durations.indexOf(timeout)
            val nextIndex = (index + 1) % durations.size

            return durations[nextIndex]
        }
    var timeout = firstTimeout
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

        keepAwakeServiceObservers.forEach { observer ->
            observer.onServiceStatusUpdate(status)
        }

        QuickTileService.requestTileStateUpdate(localizedApplicationContext)
    }

    private fun getCurrentLocale(): Locale {
        // Implement your logic to get the current locale
        // For example, retrieve it from system settings or app settings
        // As of Android 13, you can get the current locale directly from the system settings for the app
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> resources.configuration.locales[0]
            else                                                  -> Locale.getDefault()
        }
    }

    private fun applyLocaleConfiguration() {
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