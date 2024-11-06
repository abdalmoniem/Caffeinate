package com.hifnawy.caffeinate.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.services.KeepAwakeService.Companion.KeepAwakeServiceState
import timber.log.Timber as Log

/**
 * A BroadcastReceiver that listens for the ACTION_SCREEN_OFF intent, which is broadcast whenever the screen is turned off.
 *
 * This receiver is responsible for handling the screen lock event and stopping the KeepAwakeService when the screen is locked.
 *
 * @param caffeinateApplication [CaffeinateApplication] The application instance.
 *
 * @constructor Creates an instance of [ScreenLockReceiver] with the provided [CaffeinateApplication].
 *
 * @throws IllegalStateException if the application state is not properly initialized.
 *
 * @author AbdAlMoniem AlHifnawy
 *
 * @see KeepAwakeService
 * @see KeepAwakeServiceState
 */
class ScreenLockReceiver(private val caffeinateApplication: CaffeinateApplication) : BroadcastReceiver() {

    /**
     * Called when the BroadcastReceiver receives an Intent broadcast.
     *
     * @param context The Context in which the receiver is running.
     * @param intent The Intent being received.
     *
     * @see BroadcastReceiver
     */
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("Screen Locked, Stopping...")
        KeepAwakeService.toggleState(caffeinateApplication, KeepAwakeServiceState.STATE_STOP)
    }
}