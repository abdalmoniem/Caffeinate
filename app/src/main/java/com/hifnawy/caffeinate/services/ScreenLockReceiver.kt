package com.hifnawy.caffeinate.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hifnawy.caffeinate.CaffeinateApplication
import timber.log.Timber

class ScreenLockReceiver(private val caffeinateApplication: CaffeinateApplication) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("Screen Locked, Stopping...")
        KeepAwakeService.toggleState(caffeinateApplication, KeepAwakeService.Companion.STATE.STOP)
    }
}