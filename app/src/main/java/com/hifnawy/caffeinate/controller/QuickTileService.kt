package com.hifnawy.caffeinate.controller

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
import com.hifnawy.caffeinate.controller.QuickTileService.Companion.requestTileStateUpdate
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toLocalizedFormattedTime
import com.hifnawy.caffeinate.utils.MutableListExtensionFunctions.addObserver
import com.hifnawy.caffeinate.utils.MutableListExtensionFunctions.itemClasses
import com.hifnawy.caffeinate.utils.MutableListExtensionFunctions.removeObserver
import com.hifnawy.caffeinate.view.MainActivity
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
class QuickTileService : TileService(), ServiceStatusObserver, SharedPrefsObserver {

    /**
     * Lazily initializes the [CaffeinateApplication] instance associated with this service.
     *
     * The [CaffeinateApplication] instance is used to access the application context and the [KeepAwakeService].
     *
     * @return the [CaffeinateApplication] instance associated with this service.
     */
    private val caffeinateApplication by lazy { application as CaffeinateApplication }

    /**
     * Lazily initializes the [SharedPrefsManager] instance associated with this service.
     *
     * The [SharedPrefsManager] instance is used to access and modify the shared preferences of the application.
     *
     * @return the [SharedPrefsManager] instance associated with this service.
     */
    private val sharedPrefsManager by lazy { SharedPrefsManager(caffeinateApplication) }

    /**
     * Lazily initializes a variable that indicates whether all permissions necessary for the application to
     * function correctly have been granted.
     *
     * @return a [Boolean] value indicating whether all permissions are granted.
     *
     * @author AbdAlMoniem AlHifnawy
     */
    private val isAllPermissionsGranted by lazy { sharedPrefsManager.isAllPermissionsGranted }

    /**
     * Lazily initializes a variable that holds the attributes of the tile, including the tile state, tile subtitle,
     * and the icon drawable.
     *
     * The tile state is determined based on the current status of the Caffeinate service. If the service is stopped,
     * the tile state is set to [Tile.STATE_INACTIVE] and the tile subtitle is set accordingly. If the service is
     * running, the tile state is set to [Tile.STATE_ACTIVE] and the tile subtitle is set to the remaining time until
     * the service is stopped.
     *
     * The icon drawable is determined based on the tile state. If the tile state is [Tile.STATE_INACTIVE], the icon
     * drawable is set to `R.drawable.coffee_icon_off`. Otherwise, the icon drawable is set to `R.drawable.coffee_icon_on`.
     *
     * @return a [Triple] containing the tile state, tile subtitle, and the icon drawable.
     *
     * @author AbdAlMoniem AlHifnawy
     */
    context(status: ServiceStatus)
    private val tileAttributes
        get() = caffeinateApplication.run {
            val (tileState, tileSubtitle) = when (status) {
                is ServiceStatus.Stopped -> Tile.STATE_INACTIVE to when {
                    isShowStatusInQuickTileTitleEnabled -> localizedApplicationContext.getString(R.string.app_name)
                    else                                -> localizedApplicationContext.getString(R.string.quick_tile_off)
                }

                is ServiceStatus.Running -> Tile.STATE_ACTIVE to status.remaining.toLocalizedFormattedTime(caffeinateApplication, true)
            }

            val iconDrawable = when (tileState) {
                Tile.STATE_ACTIVE -> R.drawable.coffee_icon_on
                else              -> R.drawable.coffee_icon_off
            }

            Triple(tileState, tileSubtitle, iconDrawable)
        }


    /**
     * Determines the tile label based on the current tile subtitle and the value of [isShowStatusInQuickTileTitleEnabled].
     *
     * If the SDK version is at least [Build.VERSION_CODES.Q] and [isShowStatusInQuickTileTitleEnabled] is `true`,
     * the tile label is set to the tile subtitle. Otherwise, if [isShowStatusInQuickTileTitleEnabled] is `false`,
     * the tile label is set to the localized application name.
     *
     * @return a [String] representing the tile label.
     *
     * @author AbdAlMoniem AlHifnawy
     */
    context(tileSubtitle: String)
    private val tileLabel
        get() = caffeinateApplication.run {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> when {
                    isShowStatusInQuickTileTitleEnabled -> tileSubtitle
                    else                                -> localizedApplicationContext.getString(R.string.app_name)
                }

                else                                           -> tileSubtitle
            }
        }

    /**
     * A [Boolean] value indicating whether the "Show Application Status in Quick Tile Title" feature is enabled.
     *
     * This value is obtained from the [SharedPrefsManager] associated with the [CaffeinateApplication] instance
     * associated with this service.
     *
     * @author AbdAlMoniem AlHifnawy
     */
    private var isShowStatusInQuickTileTitleEnabled = false

    /**
     * Called when the user begins interacting with the tile.
     *
     * When the user starts interacting with the tile, this method is called. The tile is
     * guaranteed to be in the [Tile.STATE_ACTIVE] state at this point.
     *
     * @see TileService.onStartListening
     */
    override fun onStartListening() = caffeinateApplication.run {
        isShowStatusInQuickTileTitleEnabled = sharedPrefsManager.isShowStatusInQuickTileTitleEnabled

        // If the current class isn't in the list of observers, add it. Since only one QuickTile is added to the QuickSettings
        // Panel, this ensures that only one observer is added over the lifetime of the application when a timeout is started.
        if (this@QuickTileService::class !in keepAwakeServiceObservers.itemClasses) {
            keepAwakeServiceObservers.addObserver(this@QuickTileService)
            updateQuickTile(lastStatusUpdate)
        }

        if (this@QuickTileService::class !in sharedPrefsObservers.itemClasses) {
            sharedPrefsObservers.addObserver(this@QuickTileService)
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
            sharedPrefsObservers.removeObserver(this@QuickTileService)
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
     * Called when the "Show Quick Tile Status in Title" preference changes.
     *
     * @param isShowQuickTileStatusInTitleEnabled [Boolean] `true` if the "Show Quick Tile Status in Title" feature is enabled, `false` otherwise.
     */
    override fun onIsShowQuickTileStatusInTitleEnabledUpdated(isShowQuickTileStatusInTitleEnabled: Boolean) = isShowQuickTileStatusInTitleEnabled.let {
        this@QuickTileService.isShowStatusInQuickTileTitleEnabled = it
    }

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

        val (tileState, tileSubtitle, iconDrawable) = with(status) { tileAttributes }

        quickTile.run {
            state = tileState
            label = with(tileSubtitle) { tileLabel }

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
        private fun handleException(ex: Exception) = Log.e(ex, "${ex::class.simpleName}: An exception was raised")

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