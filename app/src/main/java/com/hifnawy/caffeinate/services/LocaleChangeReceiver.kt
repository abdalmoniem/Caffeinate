package com.hifnawy.caffeinate.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hifnawy.caffeinate.CaffeinateApplication
import timber.log.Timber as Log

/**
 * A BroadcastReceiver that listens for the ACTION_LOCALE_CHANGED intent, which is broadcast whenever the system locale changes.
 *
 * @param caffeinateApplication [CaffeinateApplication] The application instance.
 *
 * @constructor Creates an instance of [LocaleChangeReceiver] with the provided [CaffeinateApplication].
 *
 * @author AbdAlMoniem AlHifnawy
 *
 * @see CaffeinateApplication
 */
class LocaleChangeReceiver(private val caffeinateApplication: CaffeinateApplication) : BroadcastReceiver() {

    /**
     * Called when the BroadcastReceiver receives an Intent broadcast.
     *
     * @param context [Context] The Context in which the receiver is running.
     * @param intent [Intent] The Intent being received.
     *
     * @see BroadcastReceiver
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_LOCALE_CHANGED == intent.action) {
            Log.d("App locale changed from system settings! Apply new Locale...")
            caffeinateApplication.applyLocaleConfiguration()
            Log.d("Locale changed to ${CaffeinateApplication.applicationLocale.displayName} (${CaffeinateApplication.applicationLocale.language})")
        }
    }
}