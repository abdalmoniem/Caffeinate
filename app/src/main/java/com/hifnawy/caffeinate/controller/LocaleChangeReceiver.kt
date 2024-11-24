package com.hifnawy.caffeinate.controller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.hifnawy.caffeinate.CaffeinateApplication
import timber.log.Timber as Log

/**
 * A BroadcastReceiver that listens for the [Intent.ACTION_LOCALE_CHANGED] intent, which is broadcast whenever the system locale changes.
 *
 * @param caffeinateApplication [CaffeinateApplication] The application instance.
 * @param onReceiveCallback [((Context, Intent) -> Unit)][onReceiveCallback] An optional callback function that is invoked when the
 * [Intent.ACTION_LOCALE_CHANGED] intent is received.
 *
 * @constructor Creates an instance of [LocaleChangeReceiver] with the provided [CaffeinateApplication].
 *
 * @author AbdAlMoniem AlHifnawy
 *
 * @see CaffeinateApplication
 */
open class LocaleChangeReceiver(
        private val caffeinateApplication: CaffeinateApplication,
        private val onReceiveCallback: ((Context, Intent) -> Unit)? = null
) :
        RegistrableBroadcastReceiver(caffeinateApplication, IntentFilter(Intent.ACTION_LOCALE_CHANGED)) {

    /**
     * Called when the BroadcastReceiver receives an Intent broadcast.
     *
     * @param context [Context] The Context in which the receiver is running.
     * @param intent [Intent] The Intent being received.
     *
     * @see BroadcastReceiver
     * @see RegistrableBroadcastReceiver
     * @see KeepAwakeService
     */
    override fun onReceive(context: Context, intent: Intent) = when (intent.action) {
        Intent.ACTION_LOCALE_CHANGED -> {
            onReceiveCallback?.invoke(context, intent) ?: run {
                Log.d("App locale changed from system settings! Apply new Locale...")
                caffeinateApplication.applyLocaleConfiguration()
                CaffeinateApplication.applicationLocale.run { Log.d("Locale changed to $displayName ($language)") }
            }
        }

        else                         -> Unit
    }
}