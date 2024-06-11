package com.hifnawy.caffeinate.services

import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.ServiceStatus
import com.hifnawy.caffeinate.services.KeepAwakeService.Companion.KeepAwakeServiceState
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toFormattedTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class TimeoutJob(private val caffeinateApplication: CaffeinateApplication) : CoroutineScope {

    private val currentTime: String
        get() = SimpleDateFormat("hh:mm:ss.SS a", Locale.ENGLISH).format(Date(System.currentTimeMillis()))
    private var job = Job()
    override val coroutineContext
        get() = Dispatchers.Default + job

    fun cancel() {
        job.cancel(CancellationException("${::cancel.name} -> ${this::class.simpleName} cancelled!"))
    }

    fun start(duration: Duration) {
        launch {
            val isIndefinite = duration == Duration.INFINITE

            Timber.d("timeout initialized with duration: ${duration.toFormattedTime()}, isIndefinite: $isIndefinite")
            val durationSequence = when {
                isIndefinite -> generateSequence(0L) { it + 1L }
                else         -> (duration.inWholeSeconds downTo 0).asSequence()
            }

            durationSequence.forEach {
                when {
                    isIndefinite -> update(Duration.INFINITE)
                    else         -> update(it.toDuration(DurationUnit.SECONDS))
                }

                delay(1.seconds.inWholeMilliseconds)
            }
        }
    }

    private suspend fun update(remaining: Duration) = withContext(Dispatchers.Main) {
        val isIndefinite = remaining == Duration.INFINITE

        caffeinateApplication.apply {
            when (lastStatusUpdate) {
                is ServiceStatus.Running -> {
                    Timber.d("${currentTime}: duration: ${remaining.toFormattedTime()}, status: $lastStatusUpdate, isIndefinite: $isIndefinite")

                    when (remaining) {
                        0.seconds -> KeepAwakeService.toggleState(this, KeepAwakeServiceState.STOP)
                        else      -> lastStatusUpdate = ServiceStatus.Running(remaining)
                    }
                }

                is ServiceStatus.Stopped -> KeepAwakeService.toggleState(this, KeepAwakeServiceState.START)
            }
        }
    }
}