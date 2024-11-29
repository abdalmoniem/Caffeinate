package com.hifnawy.caffeinate.controller

import com.hifnawy.caffeinate.controller.ServiceStatus.Running
import com.hifnawy.caffeinate.controller.ServiceStatus.Running.RemainingValueObserver
import com.hifnawy.caffeinate.controller.ServiceStatus.Stopped
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toFormattedTime
import kotlin.time.Duration

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
    class Running(startTimeout: Duration) : ServiceStatus() {

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
         * The timeout duration in seconds.
         *
         * This property is used to determine when the service should stop.
         *
         * @return [Duration] the timeout duration in seconds.
         */
        var startTimeout: Duration = startTimeout
            private set

        /**
         * Indicates whether the service is running indefinitely.
         *
         * This property returns `true` if the remaining duration is infinite,
         * which indicates that the service is running indefinitely without a timeout.
         *
         * @return [Boolean] `true` if the service is running indefinitely, `false` otherwise.
         */
        val isIndefinite: Boolean
            get() = remaining.isInfinite()

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
        var isCountingDown = false
            get() = remaining < prevRemaining && remaining in Duration.ZERO..startTimeout && startTimeout.isFinite()
            private set

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
        var isRestarted = false
            get() = remaining > prevRemaining && remaining == startTimeout && startTimeout.isFinite()
            private set

        /**
         * The remaining timeout duration.
         *
         * This property is updated whenever the service is running and the remaining timeout duration
         * changes. The property is also settable, and setting it will trigger the
         * [onRemainingUpdated] callback.
         *
         * @see onRemainingUpdated
         */
        var remaining = startTimeout
            set(value) {
                prevRemaining = field
                field = value

                onRemainingUpdated?.onRemainingUpdated()

                if (prevRemaining != field) return
                startTimeout = field
            }

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
         * The previous remaining timeout duration.
         *
         * This property is updated whenever the service is running and the remaining timeout duration
         * changes. It is used to determine whether the service is currently counting down or
         * restarted.
         *
         * @see isCountingDown
         * @see isRestarted
         */
        private var prevRemaining = Duration.INFINITE

        /**
         * Returns a string in the format
         * "Running([remaining].[toFormattedTime]
         * , isRestarted: [isRestarted], isCountingDown: [isCountingDown], isIndefinite: [isIndefinite])".
         *
         * @return [String] a string representation of the object.
         */
        override fun toString() = Running::class.java.simpleName +
                                  "(${::remaining.name}: ${remaining.toFormattedTime()}, " +
                                  "${::isRestarted.name}: $isRestarted, " +
                                  "${::isCountingDown.name}: $isCountingDown, " +
                                  "${::isIndefinite.name}: $isIndefinite)"
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
 * @see com.hifnawy.caffeinate.controller.SharedPrefsObserver
 */
fun interface ServiceStatusObserver : Observer {

    /**
     * Called when the status of the Caffeinate service is updated.
     *
     * @param status [ServiceStatus] the new status of the service
     */
    fun onServiceStatusUpdated(status: ServiceStatus)
}