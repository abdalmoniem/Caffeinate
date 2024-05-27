package com.hifnawy.caffeinate

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.hifnawy.caffeinate.services.KeepAwakeService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CaffeineDurationSelector(private val context: Context) {

    interface CaffeineDurationCallback {

        fun onCaffeineStarted(duration: Duration)
        fun onCaffeineStopped()
        fun onCaffeineDurationChanged(isActive: Boolean, duration: Duration)
    }

    var caffeineDurationCallback: CaffeineDurationCallback? = null

    @Suppress("PrivatePropertyName")
    private val LOG_TAG = this::class.simpleName
    private val clickHandler by lazy { Handler(Looper.getMainLooper()) }
    private val sharedPreferences by lazy {
        context.getSharedPreferences(
                context.packageName, Context
            .MODE_PRIVATE
        )
    }
    private val selectedDurations by lazy {
        listOf(30.seconds, 5.minutes, 10.minutes, 15.minutes, 30.minutes, Duration.INFINITE)
    }
    private var selectedDuration = 0.minutes
    private var selectedDurationIndex = 0
    private var isCaffeineStarted = false

    fun selectNextDuration() {
        if (selectedDurationIndex == -1) {
            selectedDurationIndex = 0
        }

        if (!isCaffeineStarted && (selectedDurationIndex <= selectedDurations.lastIndex)) {
            selectedDuration = selectedDurations[selectedDurationIndex]

            Log.d(
                    LOG_TAG,
                    "selectNextDuration(), isCaffeineStarted: $isCaffeineStarted, selectedDurationIndex: $selectedDurationIndex, selectedDuration: $selectedDuration"
            )

            clickHandler.removeCallbacksAndMessages(null)

            when {
                isCaffeineStarted -> {
                    isCaffeineStarted = false
                    caffeineDurationCallback?.onCaffeineStopped()
                }

                else              -> {
                    clickHandler.postDelayed(
                            {
                                if (selectedDurationIndex >= 0) {
                                    isCaffeineStarted = true
                                    caffeineDurationCallback?.onCaffeineStarted(selectedDuration)
                                } else {
                                    selectedDurationIndex = 0
                                }

                                sharedPreferences.edit().putBoolean(
                                        KeepAwakeService.SHARED_PREFS_IS_CAFFEINE_STARTED,
                                        isCaffeineStarted
                                ).apply()
                                clickHandler.removeCallbacksAndMessages(null)
                            },
                            1000.milliseconds.inWholeMilliseconds
                    )
                }
            }

            selectedDurationIndex += 1
            val isActive = isCaffeineStarted || (selectedDurationIndex > 0)

            caffeineDurationCallback?.onCaffeineDurationChanged(isActive, selectedDuration)
        } else {
            clearState()
            caffeineDurationCallback?.onCaffeineStopped()
        }

        sharedPreferences.edit().putBoolean(
                KeepAwakeService.SHARED_PREFS_IS_CAFFEINE_STARTED,
                isCaffeineStarted
        ).apply()
    }

    fun clearState() {
        Log.d(
                LOG_TAG,
                "clearState(), Stopping..."
        )
        isCaffeineStarted = false
        selectedDurationIndex = -1
    }
}