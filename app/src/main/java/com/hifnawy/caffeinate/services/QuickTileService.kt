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
import com.hifnawy.caffeinate.utils.SharedPrefsManager

class QuickTileService : TileService() {

    private val LOG_TAG = QuickTileService::class.java.simpleName
    private val caffeinateApplication by lazy { application as CaffeinateApplication }
    private val isAllPermissionsGranted by lazy { SharedPrefsManager(caffeinateApplication).isAllPermissionsGranted }

    override fun onStartListening() {
        super.onStartListening()

        Log.d(LOG_TAG, "${::onStartListening.name}()")

        updateQuickTile()
    }

    override fun onClick() {
        if (!checkPermissions()) return

        KeepAwakeService.startNextDuration(caffeinateApplication)
    }

    private fun updateQuickTile() {
        val currentStatus = caffeinateApplication.lastStatusUpdate
        Log.d(LOG_TAG, "${::updateQuickTile.name}() -> running = $currentStatus")
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

    private fun checkPermissions(): Boolean {
        Log.d(LOG_TAG, "${::isAllPermissionsGranted.name}() -> Permissions Granted: $isAllPermissionsGranted")
        if (!isAllPermissionsGranted) {
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

    class TileServiceStatusObserver(private val context: Context) : ServiceStatusObserver {

        private val LOG_TAG = TileServiceStatusObserver::class.java.simpleName
        override fun onServiceStatusUpdate(status: ServiceStatus) {
            Log.d(LOG_TAG, "${LOG_TAG}::${::onServiceStatusUpdate.name}()")
            try {
                requestListeningState(context, ComponentName(context, QuickTileService::class.java))
            } catch (ex: Exception) {
                Log.e(LOG_TAG, "Error when calling ${LOG_TAG}::${::onServiceStatusUpdate.name}()", ex)
            }
        }
    }
}