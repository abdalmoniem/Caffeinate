package com.hifnawy.caffeinate.services

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.ServiceStatus
import com.hifnawy.caffeinate.ServiceStatusObserver
import com.hifnawy.caffeinate.ui.MainActivity
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toFormattedTime

class QuickTileService : TileService() {

    private val sharedPreferences by lazy { getSharedPreferences(packageName, Context.MODE_PRIVATE) }
    private val arePermissionsGranted by lazy { sharedPreferences.getBoolean(MainActivity.SHARED_PREFERENCES_ALL_PERMISSIONS_GRANTED, false) }

    override fun onStartListening() {
        super.onStartListening()

        Log.d(LOG_TAG, "onStartListening()")

        updateQuickTile()
    }

    override fun onClick() {
        if (!checkPermissions()) return

        KeepAwakeService.startNextDuration(application as CaffeinateApplication)
    }

    private fun checkPermissions(): Boolean {
        Log.d(LOG_TAG, "arePermissionsGranted(): Permissions Granted: $arePermissionsGranted")
        if (!arePermissionsGranted) {
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

    private fun updateQuickTile() {
        val currentStatus = (application as CaffeinateApplication).lastStatusUpdate
        Log.d(LOG_TAG, "updateQuickTile(): running = $currentStatus")
        val tile = qsTile ?: return

        val (tileState, tileSubtitle) = when (currentStatus) {
            is ServiceStatus.Stopped -> Pair(Tile.STATE_INACTIVE, "Off")
            is ServiceStatus.Running -> Pair(Tile.STATE_ACTIVE, currentStatus.remaining.toFormattedTime(true))
        }
        val iconDrawable = if (tileState == Tile.STATE_ACTIVE) R.drawable.baseline_coffee_24 else R.drawable.outline_coffee_24

        tile.apply {
            state = tileState
            label = getString(R.string.app_name)
            contentDescription = getString(R.string.app_name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = tileSubtitle
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) stateDescription = tileSubtitle
            icon = Icon.createWithResource(this@QuickTileService, iconDrawable)

            updateTile()
        }
    }

    companion object {

        private val LOG_TAG = QuickTileService::class.java.simpleName

        fun requestTileStateUpdate(context: Context) {
            Log.d(LOG_TAG, "requestTileStateUpdate()")
            try {
                requestListeningState(context, ComponentName(context, QuickTileService::class.java))
            } catch (ex: Exception) {
                Log.e(LOG_TAG, "Error when calling requestListeningState()", ex)
            }
        }
    }

    class TileServiceStatusObserver(private val context: Context) : ServiceStatusObserver {

        override fun onServiceStatusUpdate(status: ServiceStatus) {
            requestTileStateUpdate(context)
        }
    }
}