package com.hifnawy.caffeinate.services

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import timber.log.Timber as Log

/**
 * A base class for [BroadcastReceiver]s that provides a common way to register and unregister
 * them.
 *
 * This class is meant to be extended by classes that need to register a [BroadcastReceiver] to
 * receive broadcasts. The [register] method registers the [BroadcastReceiver] to receive
 * broadcasts with the specified [IntentFilter]. The [unregister] method unregisters the
 * [BroadcastReceiver] so that it can no longer receive broadcasts.
 *
 * @author AbdAlMoniem AlHifnawy
 *
 * @see BroadcastReceiver
 * @see IntentFilter
 * @see Context.registerReceiver
 * @see Context.unregisterReceiver
 */
open class BroadcastReceiverHandler(private val context: Context, private val intentFilter: IntentFilter) : BroadcastReceiver() {

    /**
     * A flag indicating whether this [BroadcastReceiver] is registered.
     *
     * This flag is used to track whether this [BroadcastReceiver] is currently registered to receive
     * broadcasts, which are sent when the screen is turned off.
     *
     * @see BroadcastReceiver
     */
    private var isRegistered = false

    /**
     * Called when the BroadcastReceiver receives an Intent broadcast.
     *
     * @param context The Context in which the receiver is running.
     * @param intent The Intent being received.
     *
     * @see BroadcastReceiver
     */
    override fun onReceive(context: Context, intent: Intent) = Unit

    /**
     * Registers this [BroadcastReceiver] to receive broadcasts.
     *
     * This method registers this [BroadcastReceiver] to receive broadcasts that are sent when the
     * screen is turned off. If this [BroadcastReceiver] is already registered, this method does
     * nothing.
     *
     * @see BroadcastReceiver
     * @see IntentFilter
     * @see Context.registerReceiver
     */
    fun register() = when {
        !isRegistered -> {
            Log.d("registering ${this::class.simpleName}...")

            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> context.registerReceiver(this, intentFilter, RECEIVER_EXPORTED)
                else                                                  -> context.registerReceiver(this, intentFilter)
            }

            isRegistered = true

            Log.d("${this::class.simpleName} registered!")
        }

        else          -> Unit
    }

    /**
     * Unregisters this [BroadcastReceiver] from receiving broadcasts.
     *
     * This method unregisters this [BroadcastReceiver] so that it no longer receives broadcasts.
     * It ensures that the receiver is unregistered only if it is currently registered.
     *
     * @see BroadcastReceiver
     * @see Context.unregisterReceiver
     */
    fun unregister() = when {
        isRegistered -> {
            Log.d("unregistering ${this::class.simpleName}...")

            context.unregisterReceiver(this)
            isRegistered = false

            Log.d("${this::class.simpleName} unregistered!")
        }

        else         -> Unit
    }
}