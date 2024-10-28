package com.hifnawy.caffeinate.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hifnawy.caffeinate.CaffeinateApplication
import timber.log.Timber as Log

class LocaleChangeReceiver(private val caffeinateApplication: CaffeinateApplication) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_LOCALE_CHANGED == intent.action) {
            Log.d("App locale changed from system settings! Apply new Locale...")
            caffeinateApplication.applyLocaleConfiguration()
            Log.d("Locale changed to ${CaffeinateApplication.applicationLocale.displayName} (${CaffeinateApplication.applicationLocale.language})")
        }
    }
}