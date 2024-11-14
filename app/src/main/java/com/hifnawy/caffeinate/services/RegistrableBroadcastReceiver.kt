package com.hifnawy.caffeinate.services

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_EXPORTED
import android.content.IntentFilter
import android.os.Build
import timber.log.Timber as Log

/**
 * A [BroadcastReceiver] that can be registered and unregistered dynamically.
 *
 * This abstract class provides a framework for creating a broadcast receiver that can be
 * registered and unregistered at runtime. It maintains the registration state and ensures
 * that the receiver is not registered or unregistered multiple times unnecessarily.
 *
 * @property isRegistered [Boolean] `true` if the receiver is currently registered, `false` otherwise.
 *
 * @see IntentFilter
 * @see BroadcastReceiver
 * @see Context.registerReceiver
 * @see Context.unregisterReceiver
 */
abstract class RegistrableBroadcastReceiver(private val context: Context, private val intentFilter: IntentFilter) : BroadcastReceiver() {

    /**
     * Indicates whether this [BroadcastReceiver] is currently registered to receive broadcasts.
     *
     * This property is used to track the registration state of the receiver, ensuring that
     * it is not registered or unregistered multiple times unnecessarily.
     *
     * @property [Boolean] `true` if the receiver is currently registered, `false` otherwise.
     */
    private var isReceiving: Boolean = false

    /**
     * Indicates whether this [BroadcastReceiver] is currently registered to receive broadcasts.
     *
     * This property is used to track the registration state of the receiver, ensuring that
     * it is not registered or unregistered multiple times unnecessarily. The value of this
     * property is `true` if the receiver is currently registered, `false` otherwise.
     *
     * Note that this property is observable, and changes to its value will trigger the
     * registration or unregistration of the receiver. Therefore, it is not necessary to
     * manually call the [register] or [unregister] methods.
     *
     * @property [Boolean] `true` if the receiver is currently registered, `false` otherwise.
     */
    var isRegistered = false
        set(value) {
            field = value

            when (value) {
                true  -> register()
                false -> unregister()
            }
        }

    /**
     * Registers this [BroadcastReceiver] to receive broadcasts.
     *
     * This method registers this [BroadcastReceiver] to receive broadcasts that are sent when the
     * screen is turned off. If this [BroadcastReceiver] is already registered, this method does
     * nothing.
     *
     * @see IntentFilter
     * @see BroadcastReceiver
     * @see Context.registerReceiver
     */
    private fun register() = when {
        !isReceiving -> {
            Log.d("registering ${javaClass.simpleName}...")

            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> context.registerReceiver(this, intentFilter, RECEIVER_EXPORTED)
                else                                                  -> context.registerReceiver(this, intentFilter)
            }

            isReceiving = true

            Log.d("${javaClass.simpleName} registered!")
        }

        else         -> Log.d("${javaClass.simpleName} is already registered!")
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
    private fun unregister() = when {
        isReceiving -> {
            Log.d("unregistering ${javaClass.simpleName}...")

            context.unregisterReceiver(this)
            isReceiving = false

            Log.d("${javaClass.simpleName} unregistered!")
        }

        else        -> Log.d("${javaClass.simpleName} is already unregistered!")
    }
}