package com.hifnawy.caffeinate.controller

import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.controller.KeepAwakeService.Companion.KeepAwakeServiceState
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toFormattedTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import timber.log.Timber as Log

/**
 * A coroutine scope that runs a job that periodically updates the status of the
 * [KeepAwakeService] based on the current timeout.
 *
 * @param caffeinateApplication [CaffeinateApplication] The [CaffeinateApplication] instance that owns this service.
 *
 * @author AbdAlMoniem AlHifnawy
 */
class TimeoutJob(private val caffeinateApplication: CaffeinateApplication) : CoroutineScope {

    /**
     * Returns the current time as a string formatted as "hh:mm:ss.SS a".
     *
     * This property is used to log the current time when the timeout job is initialized or
     * updated.
     *
     * @return [String] The current time formatted as "hh:mm:ss.SS a".
     */
    private val currentTime: String
        get() = SimpleDateFormat(
                "hh:mm:ss.SS a",
                Locale.ENGLISH
        ).format(Date(System.currentTimeMillis()))

    /**
     * The job that is running the timeout loop.
     *
     * This job is created when the [TimeoutJob] instance is created and is cancelled when the
     * [TimeoutJob.cancel] method is called.
     *
     * @see [TimeoutJob.cancel]
     */
    private var job = Job()

    /**
     * The [CoroutineContext][kotlin.coroutines.CoroutineContext] that is used to create coroutines.
     *
     * This context is a combination of a [Default][Dispatchers.Default] dispatcher and a [Job] that is used to
     * cancel the timeout job. The [Job] is created when the [TimeoutJob] instance is created and is
     * cancelled when the [cancel][TimeoutJob.cancel] method is called.
     *
     * @return [kotlin.coroutines.CoroutineContext] The [CoroutineContext][kotlin.coroutines.CoroutineContext] that is used to create coroutines.
     *
     * @see [TimeoutJob.cancel]
     */
    override val coroutineContext
        get() = Dispatchers.Default + job

    /**
     * Cancels the timeout job and releases any system resources that were allocated
     * for it.
     *
     * Calling this method will cancel the timeout job and release any system resources
     * that were allocated for it. If the timeout job was running, it will be stopped.
     *
     * @throws [CancellationException] if the timeout job was running and was cancelled.
     *
     * @see [TimeoutJob.start]
     */
    fun cancel(): Unit = job.cancel(CancellationException("${this::class.simpleName} cancelled!"))

    /**
     * Starts the timeout job.
     *
     * This method starts the timeout job with the specified [duration]. The job will update the status of the
     * [KeepAwakeService] periodically based on the current timeout. If the timeout is indefinite,
     * the job will run indefinitely.
     *
     * If the timeout job was already running, it will be stopped and restarted with the new duration.
     *
     * @param duration [Duration] the duration to use for the timeout job.
     *
     * @return [Job] the [Job] that is running the timeout loop.
     */
    fun start(duration: Duration) = launch {
        Log.d("timeout initialized with duration: ${duration.toFormattedTime()}, isIndefinite: ${duration.isInfinite()}")

        generateSequence(duration) { it - 1.seconds }.forEach { duration ->
            update(duration)

            delay(1.seconds)
        }
    }

    /**
     * Updates the status of the [KeepAwakeService].
     *
     * This method is called periodically by the timeout job to update the status of the
     * [KeepAwakeService] based on the current timeout. If the timeout is indefinite,
     * the job will run indefinitely.
     *
     * If the timeout job was already running, it will be stopped and restarted with the new duration.
     *
     * @param newRemaining [Duration] the new remaining duration for which the service is running.
     */
    private suspend fun update(newRemaining: Duration) = withContext(Dispatchers.Main) {
        val status = caffeinateApplication.lastStatusUpdate
        when (status) {
            is ServiceStatus.Running -> {
                when (newRemaining) {
                    0.seconds -> KeepAwakeService.toggleState(caffeinateApplication, KeepAwakeServiceState.STATE_STOP)
                    else      -> status.run { if (newRemaining < remaining || !isRestarted) remaining = newRemaining }
                }
            }

            is ServiceStatus.Stopped -> KeepAwakeService.toggleState(caffeinateApplication, KeepAwakeServiceState.STATE_START)
        }

        Log.d("${currentTime}: duration: ${newRemaining.toFormattedTime()}, status: $status, isIndefinite: ${newRemaining.isInfinite()}")
    }
}