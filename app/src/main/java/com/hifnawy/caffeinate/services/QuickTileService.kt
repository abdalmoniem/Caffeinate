package com.hifnawy.caffeinate.services

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.hifnawy.caffeinate.CaffeineDurationSelector
import com.hifnawy.caffeinate.DurationExtensionFunctions.format
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.ui.MainActivity
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class QuickTileService : TileService() {

    companion object {

        const val QUICK_TILE_ACTION_UPDATE = "quick.tile.action.update"
        const val INTENT_QUICK_TILE_IS_ACTIVE_EXTRA = "intent.quick.tile.is.active"
        const val INTENT_QUICK_TILE_DURATION_EXTRA = "intent.quick.tile.duration"
    }

    private var isCaffeineStarted = false
    private var caffeineDuration = 0.minutes

    @Suppress("PrivatePropertyName")
    private val LOG_TAG = this::class.simpleName
    private val sharedPreferences by lazy { getSharedPreferences(packageName, Context.MODE_PRIVATE) }
    private val quickTileKeepAwakeServiceReceiver by lazy { QuickTileKeepAwakeServiceReceiver() }
    private val caffeineDurationSelector by lazy {
        CaffeineDurationSelector(this).apply { caffeineDurationCallback = CaffeineDurationCallbacksImpl() }
    }
    private var isQuickTileReceiverRegistered = false

    override fun onStartListening() {
        super.onStartListening()

        isCaffeineStarted = sharedPreferences.getBoolean(KeepAwakeService.SHARED_PREFS_IS_CAFFEINE_STARTED, false)
        sharedPreferences.getString(KeepAwakeService.SHARED_PREFS_CAFFEINE_DURATION, null)?.let { caffeineDuration = Duration.parse(it) }

        registerQuickTileReceiver()
        updateQuickTile(isCaffeineStarted, caffeineDuration)
    }

    override fun onStopListening() {
        super.onStopListening()

        isCaffeineStarted = sharedPreferences.getBoolean(KeepAwakeService.SHARED_PREFS_IS_CAFFEINE_STARTED, false)

        Log.d(LOG_TAG, "onStopListening(), isCaffeineStarted: $isCaffeineStarted, caffeineDuration: ${caffeineDuration.format()}")

        if (!isCaffeineStarted) {
            caffeineDuration = 0.minutes

            Log.d(LOG_TAG, "registerQuickTileReceiver(), unregistering ${this::quickTileKeepAwakeServiceReceiver.name}...")
            if (isQuickTileReceiverRegistered) {
                unregisterReceiver(quickTileKeepAwakeServiceReceiver)
                isQuickTileReceiverRegistered = false
            }
            Log.d(LOG_TAG, "registerQuickTileReceiver(), ${this::quickTileKeepAwakeServiceReceiver.name} unregistered!")
        }

        updateQuickTile(isCaffeineStarted, caffeineDuration)
    }

    override fun onClick() {
        if (!arePermissionsGranted()) return

        isCaffeineStarted = sharedPreferences.getBoolean(KeepAwakeService.SHARED_PREFS_IS_CAFFEINE_STARTED, false)

        Log.d(LOG_TAG, "onClick(), isCaffeineStarted: $isCaffeineStarted, ${if (isCaffeineStarted) "stopping" else "starting"}...")

        if (isCaffeineStarted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                isCaffeineStarted = false
                updateQuickTile(false, 0.minutes)
                caffeineDurationSelector.clearState()
                startForegroundService(
                        Intent(this@QuickTileService, KeepAwakeService::class.java).apply {
                            action = KeepAwakeService.KEEP_AWAKE_SERVICE_ACTION_STOP
                        })
            }
        } else {
            caffeineDurationSelector.selectNextDuration()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerQuickTileReceiver() {
        if (!isQuickTileReceiverRegistered) {
            Log.d(LOG_TAG, "registerQuickTileReceiver(), registering ${this::quickTileKeepAwakeServiceReceiver.name}...")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(quickTileKeepAwakeServiceReceiver, IntentFilter(QUICK_TILE_ACTION_UPDATE), Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(quickTileKeepAwakeServiceReceiver, IntentFilter(QUICK_TILE_ACTION_UPDATE))
            }

            isQuickTileReceiverRegistered = true

            Log.d(LOG_TAG, "registerQuickTileReceiver(), ${this::quickTileKeepAwakeServiceReceiver.name} registered!")
        }
    }

    private fun arePermissionsGranted(): Boolean {
        val permissionsGranted =
                sharedPreferences.getBoolean(MainActivity.SHARED_PREFERENCES_ALL_PERMISSIONS_GRANTED, false)
        // Log.d(LOG_TAG, "arePermissionsGranted(), Permissions Granted: $permissionsGranted")
        if (!permissionsGranted) {
            val intent =
                    Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                        PendingIntent.getActivity(
                                this,
                                0,
                                intent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                )
            } else {
                startActivity(intent)
            }
            return false
        } else {
            return true
        }
    }

    private fun updateQuickTile(isCaffeineStarted: Boolean, duration: Duration) = qsTile?.apply {
        val isActive = isCaffeineStarted || (duration > 0.minutes)
        val durationSting = if (isActive) duration.format(true) else "Off"
        val iconDrawable = if (isActive) R.drawable.baseline_coffee_24 else R.drawable.outline_coffee_24

        contentDescription = getString(R.string.app_name)
        state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = durationSting
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) stateDescription = durationSting

        icon = Icon.createWithResource(this@QuickTileService, iconDrawable)

        updateTile()
    }

    inner class CaffeineDurationCallbacksImpl : CaffeineDurationSelector.CaffeineDurationCallback {

        override fun onCaffeineStarted(duration: Duration) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                isCaffeineStarted = true
                startForegroundService(
                        Intent(this@QuickTileService, KeepAwakeService::class.java).apply {
                            action = KeepAwakeService.KEEP_AWAKE_SERVICE_ACTION_START

                            putExtra(KeepAwakeService.KEEP_AWAKE_SERVICE_INTENT_EXTRA_DURATION, duration.toString())
                        })
            }
        }

        override fun onCaffeineStopped() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                isCaffeineStarted = false
                caffeineDurationSelector.clearState()
                startForegroundService(
                        Intent(this@QuickTileService, KeepAwakeService::class.java).apply {
                            action = KeepAwakeService.KEEP_AWAKE_SERVICE_ACTION_STOP
                        })
            }
        }

        @SuppressLint("SetTextI18n")
        override fun onCaffeineDurationChanged(isActive: Boolean, duration: Duration) {
            updateQuickTile(isActive, duration)
        }
    }

    inner class QuickTileKeepAwakeServiceReceiver : BroadcastReceiver() {

        @SuppressLint("SetTextI18n")
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            if (intent.action != QUICK_TILE_ACTION_UPDATE) return

            isCaffeineStarted = intent.getBooleanExtra(INTENT_QUICK_TILE_IS_ACTIVE_EXTRA, false)
            val caffeineDurationStr = intent.getStringExtra(INTENT_QUICK_TILE_DURATION_EXTRA) ?: return
            caffeineDuration = Duration.parse(caffeineDurationStr)

            Log.d(LOG_TAG, "quickTileReceiverOnReceive(), duration: $caffeineDuration, isActive: $isCaffeineStarted")
            updateQuickTile(isCaffeineStarted, caffeineDuration)
        }
    }
}