package com.hifnawy.caffeinate.controller

import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.controller.KeepAwakeService.Companion.KeepAwakeServiceState
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toFormattedTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
    private val currentTime
        get() = SimpleDateFormat("hh:mm:ss.SS a", Locale.ENGLISH).format(Date(System.currentTimeMillis()))

    /**
     * The job that is running the timeout loop.
     *
     * This job is created when the [TimeoutJob] instance is created and is cancelled when the [stop] method is called.
     *
     * @see [stop]
     */
    private var job: Job? = null

    /**
     * The [CoroutineContext][kotlin.coroutines.CoroutineContext] that is used to create coroutines.
     *
     * This context is a combination of a [Default][Dispatchers.Default] dispatcher and a [Job] that is used to
     * cancel the timeout job. The [Job] is created when the [TimeoutJob] instance is created and is
     * cancelled when the [stop] method is called.
     *
     * @return [kotlin.coroutines.CoroutineContext] The [CoroutineContext][kotlin.coroutines.CoroutineContext] that is used to create coroutines.
     *
     * @see [Dispatchers.Default]
     * @see [Job]
     * @see [start]
     * @see [stop]
     * @see [cancel]
     */
    override val coroutineContext
        get() = Dispatchers.Default + Job()

    /**
     * Stops the timeout job.
     *
     * This method stops the timeout job. If the timeout job was running, it will be cancelled.
     *
     * @throws [CancellationException] if the timeout job was running and was cancelled.
     *
     * @see [start]
     * @see [cancel]
     */
    fun stop() = job?.cancel(CancellationException("${javaClass.simpleName} cancelled!"))

    /**
     * Cancels the timeout job and releases any system resources that were allocated
     * for it.
     *
     * Calling this method will cancel the timeout job and release any system resources
     * that were allocated for it. If the timeout job was running, it will be stopped.
     *
     * @throws [CancellationException] if the timeout job was running and was cancelled.
     *
     * @see [start]
     * @see [stop]
     */
    fun cancel() = cancel(CancellationException("${javaClass.simpleName} cancelled!"))

    /**
     * Starts the timeout job.
     *
     * This method starts the timeout job with the specified [startDuration]. The job will update the status of the
     * [KeepAwakeService] periodically based on the current timeout. If the timeout is indefinite,
     * the job will run indefinitely.
     *
     * If the timeout job was already running, it will be stopped and restarted with the new duration.
     *
     * @param startDuration [Duration] the duration to use for the timeout job.
     * @param startAfter [Duration] the duration to delay before starting the timeout job.
     */
    fun start(startDuration: Duration, startAfter: Duration? = null) {
        job = launch {
            startAfter?.let { delay(it) }

            val delayDuration = 1.seconds
            Log.d(
                    "$currentTime: timeout initialized with duration: ${startDuration.toFormattedTime()}, " +
                    "timeout update period: ${delayDuration.toFormattedTime()}"
            )

            generateSequence(startDuration) { it - delayDuration }.forEach { duration ->
                update(duration)

                delay(delayDuration)
            }
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
        if (status !is ServiceStatus.Running) return@withContext

        when (newRemaining) {
            0.seconds -> KeepAwakeService.toggleState(caffeinateApplication, KeepAwakeServiceState.STATE_STOP)
            else      -> status.run { if (newRemaining < remaining || !isRestarted) remaining = newRemaining }
        }

        Log.d("$currentTime: updated status: $status")
    }
}