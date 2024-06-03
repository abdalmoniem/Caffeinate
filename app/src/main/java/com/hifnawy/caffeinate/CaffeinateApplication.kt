package com.hifnawy.caffeinate

import android.app.Application
import com.hifnawy.caffeinate.services.QuickTileService
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toFormattedTime
import com.hifnawy.caffeinate.utils.SharedPrefsManager
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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

    private fun notifyKeepAwakeServiceObservers(status: ServiceStatus) {
        if (status is ServiceStatus.Stopped) timeout = firstTimeout

        keepAwakeServiceObservers.forEach { observer ->
            observer.onServiceStatusUpdate(status)
        }

        QuickTileService.requestTileStateUpdate(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
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