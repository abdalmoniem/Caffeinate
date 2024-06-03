package com.hifnawy.caffeinate.services

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import timber.log.Timber as Log
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.ServiceStatus
import com.hifnawy.caffeinate.ui.MainActivity
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toFormattedTime
import com.hifnawy.caffeinate.utils.SharedPrefsManager
import kotlin.time.Duration

class QuickTileService : TileService() {

    @Suppress("PrivatePropertyName")
    private val LOG_TAG = QuickTileService::class.java.simpleName
    private val caffeinateApplication by lazy { application as CaffeinateApplication }
    private val isAllPermissionsGranted by lazy { SharedPrefsManager(caffeinateApplication).isAllPermissionsGranted }

    override fun onTileAdded() {
        super.onTileAdded()

        updateQuickTile(caffeinateApplication.lastStatusUpdate)
    }

    override fun onStartListening() {
        super.onStartListening()
        val status = caffeinateApplication.lastStatusUpdate
        when (status) {
            is ServiceStatus.Running -> Log.d("${::onStartListening.name}() -> duration: ${status.remaining.toFormattedTime()}, status: $status, isIndefinite: ${status.remaining == Duration.INFINITE}"
            )

            ServiceStatus.Stopped    -> Log.d("${::onStartListening.name}() -> status: $status")
        }

        updateQuickTile(status)
    }

    override fun onClick() {
        if (!checkPermissions()) return

        KeepAwakeService.startNextDuration(caffeinateApplication)
    }

    private fun updateQuickTile(status: ServiceStatus) {
        val quickTile = qsTile ?: return

        when (status) {
            is ServiceStatus.Running -> Log.d("${::updateQuickTile.name}() -> duration: ${status.remaining.toFormattedTime()}, status: $status, isIndefinite: ${status.remaining == Duration.INFINITE}"
            )

            ServiceStatus.Stopped    -> Log.d("${::updateQuickTile.name}() -> status: $status")
        }

        val (tileState, tileSubtitle) = when (status) {
            is ServiceStatus.Stopped -> Pair(Tile.STATE_INACTIVE, getString(R.string.quick_tile_off))
            is ServiceStatus.Running -> Pair(Tile.STATE_ACTIVE, status.remaining.toFormattedTime(this, true))
        }
        val iconDrawable = if (tileState == Tile.STATE_ACTIVE) R.drawable.baseline_coffee_24 else R.drawable.outline_coffee_24

        quickTile.apply {
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
        Log.d("${::isAllPermissionsGranted.name}() -> Permissions Granted: $isAllPermissionsGranted")
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

    companion object QuickTileServiceCompanionObject {

        private val LOG_TAG = this::class.java.simpleName
        fun requestTileStateUpdate(context: Context) {
            Log.d("${LOG_TAG}::${::requestTileStateUpdate.name}()")
            try {
                requestListeningState(context, ComponentName(context, QuickTileService::class.java))
            } catch (e: Exception) {
                Log.e("Error while calling ${LOG_TAG}::${::requestTileStateUpdate.name}()", e)
            }
        }
    }
}