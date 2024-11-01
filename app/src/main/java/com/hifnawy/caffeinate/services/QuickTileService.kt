package com.hifnawy.caffeinate.services

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.ServiceStatus
import com.hifnawy.caffeinate.ui.MainActivity
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toFormattedTime
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toLocalizedFormattedTime
import com.hifnawy.caffeinate.utils.SharedPrefsManager
import kotlin.time.Duration
import timber.log.Timber as Log

/**
 * A [TileService] that provides a quick tile that can be used to start or stop the Caffeinate service.
 *
 * @author AbdAlMoniem AlHifnawy
 */
class QuickTileService : TileService() {

    private val caffeinateApplication by lazy { application as CaffeinateApplication }
    private val isAllPermissionsGranted by lazy { SharedPrefsManager(caffeinateApplication).isAllPermissionsGranted }

    /**
     * Updates the quick tile with the current status of the Caffeinate service.
     */
    override fun onTileAdded() {
        super.onTileAdded()

        updateQuickTile(caffeinateApplication.lastStatusUpdate)
    }

    /**
     * Updates the quick tile with the current status of the Caffeinate service.
     */
    override fun onStartListening() {
        super.onStartListening()
        val status = caffeinateApplication.lastStatusUpdate
        when (status) {
            is ServiceStatus.Running -> Log.d("duration: ${status.remaining.toFormattedTime()}, status: $status, isIndefinite: ${status.remaining == Duration.INFINITE}")
            ServiceStatus.Stopped    -> Log.d("status: $status")
        }

        updateQuickTile(status)
    }

    /**
     * Updates the quick tile with the current status of the Caffeinate service.
     */
    override fun onClick() {
        if (!checkPermissions()) return

        KeepAwakeService.startNextTimeout(caffeinateApplication)
    }

    /**
     * Updates the quick tile with the current status of the Caffeinate service.
     *
     * @param status [ServiceStatus] the current status of the Caffeinate service
     */
    private fun updateQuickTile(status: ServiceStatus) {
        val quickTile = qsTile ?: return

        when (status) {
            is ServiceStatus.Running -> Log.d("duration: ${status.remaining.toFormattedTime()}, status: $status, isIndefinite: ${status.remaining == Duration.INFINITE}")
            ServiceStatus.Stopped    -> Log.d("status: $status")
        }

        val (tileState, tileSubtitle) = when (status) {
            is ServiceStatus.Stopped -> Pair(
                    Tile.STATE_INACTIVE,
                    caffeinateApplication.localizedApplicationContext.getString(R.string.quick_tile_off)
            )

            is ServiceStatus.Running -> Pair(Tile.STATE_ACTIVE, status.remaining.toLocalizedFormattedTime(this, true))
        }
        val iconDrawable = if (tileState == Tile.STATE_ACTIVE) R.drawable.baseline_coffee_24 else R.drawable.outline_coffee_24

        quickTile.apply {
            state = tileState
            label = caffeinateApplication.localizedApplicationContext.getString(R.string.app_name)
            contentDescription = caffeinateApplication.localizedApplicationContext.getString(R.string.app_name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = tileSubtitle
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) stateDescription = tileSubtitle
            icon = Icon.createWithResource(this@QuickTileService, iconDrawable)

            updateTile()
        }
    }

    /**
     * Checks if all necessary permissions are granted for the application to function correctly.
     *
     * This method logs the current permission status and, if permissions are not granted,
     * it starts the MainActivity to prompt the user to grant the necessary permissions.
     * For devices running Android UPSIDE_DOWN_CAKE or higher, it uses `startActivityAndCollapse`
     * to start the activity.
     *
     * @return [Boolean] `true` if all permissions are granted, `false` otherwise
     */
    private fun checkPermissions(): Boolean {
        Log.d("Permissions Granted: $isAllPermissionsGranted")
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

    /**
     * A companion object for [QuickTileService].
     *
     * This companion object provides a helper method that can be used to request a tile state update.
     *
     * @author AbdAlMoniem AlHifnawy
     */
    companion object {

        /**
         * Requests a tile state update for the tile provided by [QuickTileService].
         *
         * This method can be used to request a tile state update from anywhere in the app. It
         * checks if the tile is available and, if so, requests a tile state update using the
         * [requestListeningState][TileService.requestListeningState] method.
         *
         * @param context [Context] the context in which the tile state update is requested
         *
         * @throws NullPointerException if the context is null
         * @throws SecurityException if the app does not have the necessary permissions to request a tile state update
         * @throws IllegalArgumentException if the tile is not available
         */
        fun requestTileStateUpdate(context: Context) {
            try {
                requestListeningState(context, ComponentName(context, QuickTileService::class.java))
            } catch (ex: NullPointerException) {
                Log.e("NullPointerException: An exception was raised while requesting tile state update", ex)
            } catch (ex: SecurityException) {
                Log.e("SecurityException: An exception was raised while requesting tile state update", ex)
            } catch (ex: IllegalArgumentException) {
                Log.e("IllegalArgumentException: An exception was raised while requesting tile state update", ex)
            }
        }
    }
}