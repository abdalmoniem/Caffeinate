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
import com.hifnawy.caffeinate.ServiceStatusObserver
import com.hifnawy.caffeinate.services.QuickTileService.Companion.requestTileStateUpdate
import com.hifnawy.caffeinate.ui.MainActivity
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toLocalizedFormattedTime
import com.hifnawy.caffeinate.utils.MutableListExtensionFunctions.addObserver
import com.hifnawy.caffeinate.utils.MutableListExtensionFunctions.itemClasses
import com.hifnawy.caffeinate.utils.MutableListExtensionFunctions.removeObserver
import com.hifnawy.caffeinate.utils.SharedPrefsManager
import timber.log.Timber as Log

/**
 * A [TileService] that provides a quick tile that can be used to start or stop the Caffeinate service.
 *
 * The class implements the [ServiceStatusObserver] interface, since on some devices the [requestTileStateUpdate]
 * function stops working after a few seconds. Fow now it is still unknown why that might be, but implementing
 * the [ServiceStatusObserver] interface seems to keep it updated for as long as the [KeepAwakeService] is running.
 *
 * @author AbdAlMoniem AlHifnawy
 */
class QuickTileService : TileService(), ServiceStatusObserver {

    /**
     * Lazily initializes the [CaffeinateApplication] instance associated with this service.
     *
     * The [CaffeinateApplication] instance is used to access the application context and the [KeepAwakeService].
     *
     * @return the [CaffeinateApplication] instance associated with this service.
     */
    private val caffeinateApplication by lazy { application as CaffeinateApplication }

    /**
     * Lazily initializes a variable that indicates whether all permissions necessary for the application to
     * function correctly have been granted.
     *
     * @return a [Boolean] value indicating whether all permissions are granted.
     *
     * @author AbdAlMoniem AlHifnawy
     */
    private val isAllPermissionsGranted by lazy { SharedPrefsManager(caffeinateApplication).isAllPermissionsGranted }

    /**
     * Called when the user begins interacting with the tile.
     *
     * When the user starts interacting with the tile, this method is called. The tile is
     * guaranteed to be in the [Tile.STATE_ACTIVE] state at this point.
     *
     * @see TileService.onStartListening
     */
    override fun onStartListening() = caffeinateApplication.run {
        // If the current class isn't in the list of observers, add it. Since only one QuickTile is added to the QuickSettings
        // Panel, this ensures that only one observer is added over the lifetime of the application when a timeout is started.
        if (this@QuickTileService::class !in keepAwakeServiceObservers.itemClasses) {
            keepAwakeServiceObservers.addObserver(this@QuickTileService)
            updateQuickTile(lastStatusUpdate)
        }
    }

    /**
     * Called when the user stops interacting with the tile.
     *
     * When the user stops interacting with the tile, this method is called. The tile is
     * guaranteed to be in the [Tile.STATE_INACTIVE] state at this point.
     *
     * @see TileService.onStopListening
     */
    override fun onStopListening() = caffeinateApplication.run {
        // only remove the observer if the service is stopped.
        if (lastStatusUpdate is ServiceStatus.Stopped) {
            keepAwakeServiceObservers.removeObserver(this@QuickTileService)
            updateQuickTile(lastStatusUpdate)
        }
    }

    /**
     * Called when the status of the Caffeinate service is updated.
     *
     * @param status [ServiceStatus] the new status of the service
     */
    override fun onServiceStatusUpdated(status: ServiceStatus) = updateQuickTile(status)

    /**
     * Called when the user clicks on the tile.
     *
     * This method is called when the user clicks on the tile. If all necessary permissions are granted,
     * it starts the next timeout.
     *
     * @see TileService.onClick
     */
    override fun onClick() = ifAllPermissionsGranted { KeepAwakeService.startNextTimeout(caffeinateApplication) }

    /**
     * Executes the given [action] if all necessary permissions are granted.
     *
     * This function checks the current permission status and, if all required permissions are not granted,
     * it launches the [MainActivity] to prompt the user to grant the permissions.
     *
     * @param action [() -> Unit][action] The action to execute if all permissions are granted.
     */
    private fun ifAllPermissionsGranted(action: () -> Unit) {
        Log.d("Permissions Granted: $isAllPermissionsGranted")

        when (isAllPermissionsGranted) {
            false -> launchMainActivity()
            else  -> action()
        }
    }

    /**
     * Updates the quick tile with the current status of the Caffeinate service.
     *
     * @param status [ServiceStatus] the current status of the Caffeinate service
     */
    private fun updateQuickTile(status: ServiceStatus) = caffeinateApplication.run {
        val quickTile = qsTile ?: return@run

        Log.d("status: $status")

        val (tileState, tileSubtitle) = when (status) {
            is ServiceStatus.Stopped -> Tile.STATE_INACTIVE to localizedApplicationContext.getString(R.string.quick_tile_off)
            is ServiceStatus.Running -> Tile.STATE_ACTIVE to status.remaining.toLocalizedFormattedTime(this, true)
        }
        val iconDrawable = when (tileState) {
            Tile.STATE_ACTIVE -> R.drawable.baseline_coffee_24
            else              -> R.drawable.outline_coffee_24
        }

        quickTile.run {
            state = tileState
            label = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> localizedApplicationContext.getString(R.string.app_name)
                else                                           -> tileSubtitle
            }
            contentDescription = localizedApplicationContext.getString(R.string.app_name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = tileSubtitle
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) stateDescription = tileSubtitle
            icon = Icon.createWithResource(this@QuickTileService, iconDrawable)

            updateTile()
        }
    }

    /**
     * Launches the [MainActivity] to prompt the user to grant the permissions.
     */
    private fun launchMainActivity() = Intent(this, MainActivity::class.java)
        .run {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent =
                        PendingIntent.getActivity(this@QuickTileService, 0, this, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

                startActivityAndCollapse(pendingIntent)
            } else {
                startActivity(this)
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
         * Handles an exception that occurred while requesting a tile state update.
         *
         * This method logs the exception and its message.
         *
         * @param ex [Exception] the exception to handle
         */
        private fun handleException(ex: Exception) = Log.e("${ex::class.simpleName}: An exception was raised", ex)

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
        fun requestTileStateUpdate(context: Context) = try {
            Log.d("requesting quick tile state update...")
            requestListeningState(context, ComponentName(context, QuickTileService::class.java))
        } catch (ex: NullPointerException) {
            handleException(ex)
        } catch (ex: SecurityException) {
            handleException(ex)
        } catch (ex: IllegalArgumentException) {
            handleException(ex)
        }
    }
}