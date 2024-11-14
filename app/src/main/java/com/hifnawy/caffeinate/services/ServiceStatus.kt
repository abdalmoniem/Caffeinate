package com.hifnawy.caffeinate.services

import com.hifnawy.caffeinate.services.ServiceStatus.Running
import com.hifnawy.caffeinate.services.ServiceStatus.Running.RemainingValueObserver
import com.hifnawy.caffeinate.services.ServiceStatus.Stopped
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toFormattedTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Represents the status of the Caffeinate service.
 *
 * The service can be either running or stopped.
 *
 * @author AbdAlMoniem AlHifnawy
 *
 * @see Running
 * @see Stopped
 */
sealed class ServiceStatus {

    /**
     * Represents the status of the Caffeinate service when it is running.
     *
     * This class contains a timeout that is used to determine when the service should stop.
     * The timeout is specified in seconds and is used by the service to run for the specified
     * amount of time.
     *
     * @param startTimeout [Duration] the timeout duration in seconds.
     *
     * @property isCountingDown [Boolean] whether the service is currently counting down or not.
     * @property isRestarted [Boolean] whether the service has been restarted or not.
     * @property remaining [Duration] the remaining timeout duration in seconds.
     * @property onRemainingUpdated [RemainingValueObserver] a callback that is called when the remaining timeout duration is updated.
     *
     * @author AbdAlMoniem AlHifnawy
     */
    data class Running(private val startTimeout: Duration) : ServiceStatus() {

        /**
         * Interface for observing changes to the remaining timeout duration while the service is running.
         *
         * Implement this interface to receive updates about the remaining timeout duration while the service is running.
         * The callback provided will be called whenever the remaining timeout duration is updated.
         *
         * @author AbdAlMoniem AlHifnawy
         *
         * @see Running
         * @see remaining
         */
        fun interface RemainingValueObserver : Observer {

            /**
             * Called when the remaining timeout duration is updated.
             *
             * This method is called whenever the remaining timeout duration is updated.
             * It is called with no arguments and is intended to be overridden by classes
             * that implement this interface.
             *
             * @see remaining
             */
            fun onRemainingUpdated()
        }

        /**
         * Indicates whether the service is currently counting down or not.
         *
         * If the service is currently running and the remaining duration is less than the
         * starting timeout, then the service is considered to be counting down.
         *
         * @return [Boolean] `true` if the service is currently counting down, `false` otherwise.
         *
         * @see ServiceStatus.Running.remaining
         * @see ServiceStatus.Running.startTimeout
         */
        val isCountingDown: Boolean
            get() = 0.seconds <= remaining && remaining <= startTimeout && startTimeout != Duration.INFINITE

        /**
         * Indicates whether the service is currently restarted or not.
         *
         * If the service is currently running and the remaining duration is equal to the
         * starting timeout, then the service is considered to be restarted.
         *
         * @return [Boolean] `true` if the service is currently restarted, `false` otherwise.
         *
         * @see ServiceStatus.Running.remaining
         * @see ServiceStatus.Running.startTimeout
         */
        val isRestarted: Boolean
            get() = remaining == startTimeout && startTimeout != Duration.INFINITE

        /**
         * A callback that is called when the remaining timeout duration is updated.
         *
         * This callback is called whenever the remaining timeout duration is updated.
         * It is called with the current [Running] instance as an argument.
         *
         * @see remaining
         */
        var onRemainingUpdated: RemainingValueObserver? = null

        /**
         * The remaining timeout duration.
         *
         * This property is updated whenever the service is running and the remaining timeout duration
         * changes. The property is also settable, and setting it will trigger the
         * [onRemainingUpdated] callback.
         *
         * @see onRemainingUpdated
         */
        var remaining: Duration = startTimeout
            set(value) {
                field = value

                onRemainingUpdated?.onRemainingUpdated()
            }

        /**
         * Returns a string in the format
         * "Running([Duration.toFormattedTime][com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toFormattedTime])".
         *
         * @return [String] a string representation of the object.
         */
        override fun toString() =
                "${Running::class.java.simpleName}(${::remaining.name}: ${remaining.toFormattedTime()}, " +
                "isIndefinite: ${remaining == Duration.INFINITE})"
    }

    /**
     * The service is currently stopped.
     *
     * @author AbdAlMoniem AlHifnawy
     */
    data object Stopped : ServiceStatus()
}

/**
 * An interface for observing changes in the status of the Caffeinate service.
 *
 * Implement this interface to receive updates about the service's status,
 * which can be either running or stopped.
 *
 * @see Observer
 * @see ServiceStatus
 * @see com.hifnawy.caffeinate.utils.SharedPrefsObserver
 */
fun interface ServiceStatusObserver : Observer {

    /**
     * Called when the status of the Caffeinate service is updated.
     *
     * @param status [ServiceStatus] the new status of the service
     */
    fun onServiceStatusUpdated(status: ServiceStatus)
}