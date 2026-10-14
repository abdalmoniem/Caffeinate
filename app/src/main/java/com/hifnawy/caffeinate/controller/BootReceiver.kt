package com.hifnawy.caffeinate.controller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.controller.KeepAwakeService.Companion.KeepAwakeServiceState
import timber.log.Timber as Log

/**
 * A [BroadcastReceiver] that restarts the Caffeinate service automatically
 * after the device boots, without launching the main activity.
 *
 * The service is only restarted if it was running before the device was shut
 * down, as tracked by [CaffeinateApplication.isServiceRunning]. This lets the
 * user keep their screen awake across reboots without any user interaction.
 *
 * @author AbdAlMoniem AlHifnawy
 *
 * @see KeepAwakeService
 * @see CaffeinateApplication.isServiceRunning
 */
class BootReceiver : BroadcastReceiver() {

    /**
     * Called when the device finishes booting.
     *
     * If the Caffeinate service was running before shutdown, it is started
     * again in the background so the screen stays awake for the previously
     * selected timeout.
     *
     * @param context [Context] the application context.
     * @param intent [Intent] the broadcast intent that triggered this receiver.
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        val application = context.applicationContext as? CaffeinateApplication ?: return

        if (application.isServiceRunning) {
            Log.d("BootReceiver: restarting KeepAwakeService after boot")
            KeepAwakeService.toggleState(application, KeepAwakeServiceState.STATE_START)
        }
    }
}
