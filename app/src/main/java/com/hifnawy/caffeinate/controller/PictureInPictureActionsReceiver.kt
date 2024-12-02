package com.hifnawy.caffeinate.controller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.controller.KeepAwakeService.Companion.KeepAwakeServiceState
import com.hifnawy.caffeinate.controller.PiPAction.NEXT_TIMEOUT
import com.hifnawy.caffeinate.controller.PiPAction.RESTART
import com.hifnawy.caffeinate.controller.PiPAction.TOGGLE

/**
 * Enum containing the possible actions that can be sent to the Caffeinate application while it is in picture-in-picture mode.
 *
 * The actions are:
 * - [RESTART] restart the KeepAwake service.
 * - [NEXT_TIMEOUT] switch to the next timeout duration.
 * - [TOGGLE] toggle the KeepAwake service on or off.
 *
 * @author AbdAlMoniem AlHifnawy
 */
enum class PiPAction {

    /**
     * Restart the KeepAwake service.
     */
    RESTART,

    /**
     * Switch to the next timeout duration.
     */
    NEXT_TIMEOUT,

    /**
     * Toggle the KeepAwake service on or off.
     */
    TOGGLE,
}

/**
 * A [BroadcastReceiver] that handles actions sent to the Caffeinate application from the picture-in-picture mode.
 *
 * This receiver is responsible for handling actions sent to the application from the picture-in-picture mode.
 * It is used to restart the KeepAwake service when the user restarts it from the picture-in-picture mode, for example.
 *
 * The receiver is registered in the manifest file and is enabled by default.
 *
 * @author AbdAlMoniem AlHifnawy
 */
class PictureInPictureActionsReceiver(
        private val caffeinateApplication: CaffeinateApplication,
        private val onReceiveCallback: ((Context, Intent) -> Unit)? = null
) : RegistrableBroadcastReceiver(
        caffeinateApplication,
        IntentFilter().apply {
            addAction(RESTART.name)
            addAction(NEXT_TIMEOUT.name)
            addAction(TOGGLE.name)
        }
) {

    /**
     * A listener interface for handling action click events in picture-in-picture mode.
     *
     * Implement this interface to handle action clicks such as [PiPAction.RESTART],
     * [PiPAction.NEXT_TIMEOUT], and [PiPAction.TOGGLE]. The [onActionClick] method
     * will be invoked with the corresponding [PiPAction] when an action is clicked.
     */
    fun interface OnActionClickListener {

        /**
         * Called when an action is clicked in picture-in-picture mode.
         *
         * @param action The [PiPAction] that was clicked.
         */
        fun onActionClick(action: PiPAction)
    }

    /**
     * The listener that will be notified of action clicks.
     *
     * This listener should be set to handle action click events from the
     * picture-in-picture mode. It may be `null` if no action handling is required.
     */
    var onActionClickListener: OnActionClickListener? = null

    /**
     * Called when the BroadcastReceiver receives an Intent broadcast.
     *
     * @param context The Context in which the receiver is running.
     * @param intent The Intent being received.
     *
     * @see BroadcastReceiver
     */
    override fun onReceive(context: Context, intent: Intent) = onReceiveCallback?.invoke(context, intent) ?: run {
        intent.action?.run { if (this in PiPAction.entries.map { it.name }) onActionClickListener?.onActionClick(PiPAction.valueOf(this)) }

        when (intent.action) {
            RESTART.name -> KeepAwakeService.restart(caffeinateApplication)
            NEXT_TIMEOUT.name -> KeepAwakeService.startNextTimeout(caffeinateApplication, wrapAround = true)
            TOGGLE.name -> when (caffeinateApplication.lastStatusUpdate) {
                is ServiceStatus.Stopped -> KeepAwakeService.startNextTimeout(caffeinateApplication)
                is ServiceStatus.Running -> KeepAwakeService.toggleState(caffeinateApplication, KeepAwakeServiceState.STATE_STOP)
            }

            else -> Unit
        }
    }
}