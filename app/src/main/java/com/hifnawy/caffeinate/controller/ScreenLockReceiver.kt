package com.hifnawy.caffeinate.controller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.controller.KeepAwakeService.Companion.KeepAwakeServiceState
import timber.log.Timber as Log

/**
 * A BroadcastReceiver that listens for the [Intent.ACTION_SCREEN_OFF] intent, which is broadcast whenever the screen is turned off.
 *
 * This receiver is responsible for handling the screen lock event and stopping the KeepAwakeService when the screen is locked.
 *
 * @param caffeinateApplication [CaffeinateApplication] The application instance.
 * @param onReceiveCallback [((Context, Intent) -> Unit)][onReceiveCallback] An optional callback function that is invoked when the
 * [Intent.ACTION_SCREEN_OFF] intent is received.
 * @constructor Creates an instance of [ScreenLockReceiver] with the provided [CaffeinateApplication].
 *
 * @throws IllegalStateException if the application state is not properly initialized.
 *
 * @author AbdAlMoniem AlHifnawy
 *
 * @see BroadcastReceiver
 * @see RegistrableBroadcastReceiver
 * @see KeepAwakeService
 */
class ScreenLockReceiver(
        private val caffeinateApplication: CaffeinateApplication,
        private val onReceiveCallback: ((Context, Intent) -> Unit)? = null
) : RegistrableBroadcastReceiver(caffeinateApplication, IntentFilter(Intent.ACTION_SCREEN_OFF)) {

    /**
     * Called when the BroadcastReceiver receives an Intent broadcast.
     *
     * @param context The Context in which the receiver is running.
     * @param intent The Intent being received.
     *
     * @see BroadcastReceiver
     */
    override fun onReceive(context: Context, intent: Intent) = when (intent.action) {
        Intent.ACTION_SCREEN_OFF -> {
            onReceiveCallback?.invoke(context, intent) ?: run {
                Log.d("Screen Locked, Stopping...")
                KeepAwakeService.toggleState(caffeinateApplication, KeepAwakeServiceState.STATE_STOP)
            }
        }

        else                     -> Unit
    }
}