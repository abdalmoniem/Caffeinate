package com.hifnawy.caffeinate.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.ServiceStatus
import com.hifnawy.caffeinate.databinding.WidgetBinding
import com.hifnawy.caffeinate.services.KeepAwakeService
import com.hifnawy.caffeinate.ui.MainActivity
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toFormattedTime
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toLocalizedFormattedTime
import com.hifnawy.caffeinate.utils.SharedPrefsManager
import timber.log.Timber as Log

/**
 * A custom implementation of [AppWidgetProvider] that handles widget updates and interactions for the Caffeinate application.
 *
 * This class is responsible for responding to broadcast intents that are sent to the widget, such as updates or user interactions.
 * It updates the widget's UI and manages the interaction between the widget and the [KeepAwakeService].
 *
 * The widget allows users to quickly start or stop the service, providing a convenient way to keep the screen awake for a specified duration.
 *
 * @constructor Creates a new instance of [Widget].
 *
 * @see AppWidgetProvider
 * @see KeepAwakeService
 * @see CaffeinateApplication
 * @see ServiceStatus
 */
class Widget : AppWidgetProvider() {

    /**
     * Receives a broadcast intent and responds accordingly.
     *
     * This method is called when a broadcast intent is sent to this widget. It updates the widget's UI and manages the interaction between the widget
     * and the [KeepAwakeService].
     *
     * @param context [Context] the context in which the widget is running
     * @param intent [Intent] the broadcast intent that was received
     *
     * @see AppWidgetProvider.onReceive
     * @see KeepAwakeService
     * @see CaffeinateApplication
     * @see ServiceStatus
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        caffeinateApplication = context.applicationContext as CaffeinateApplication

        when (intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE, AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED -> updateAllWidgets(caffeinateApplication)
            "CLICK"                                                                                     -> {
                if (!checkPermissions()) return
                KeepAwakeService.startNextTimeout(caffeinateApplication)
            }
        }
    }

    /**
     * Checks if all necessary permissions are granted for the application to function correctly.
     *
     * This method logs the current permission status and, if permissions are not granted,
     * it starts the MainActivity to prompt the user to grant the necessary permissions.
     *
     * @return [Boolean] `true` if all permissions are granted, `false` otherwise
     */
    private fun checkPermissions(): Boolean {
        val isAllPermissionsGranted by lazy { SharedPrefsManager(caffeinateApplication).isAllPermissionsGranted }
        Log.d("Permissions Granted: $isAllPermissionsGranted")

        if (!isAllPermissionsGranted) caffeinateApplication.run {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        }

        return when {
            !isAllPermissionsGranted -> false
            else                     -> true
        }
    }

    /**
     * A companion object for the Widget class.
     *
     * This object provides static-like functionality for the Widget class, allowing access to
     * methods and constants without needing an instance of the class.
     *
     * The companion object may include utility methods related to widget handling and
     * predefined constants used throughout the Widget class.
     *
     * Example usage:
     * ```
     * Widget.updateAllWidgets(caffeinateApplication)
     * ```
     *
     * @see Widget
     */
    companion object {

        /**
         * A reference to the application instance, which is used to access the application context and other application-wide resources.
         *
         * This field is only initialized once the [onReceive] method is called, and it is used by the [updateAllWidgets] method to access the
         * application context and other resources.
         *
         * @see onReceive
         * @see updateAllWidgets
         */
        private lateinit var caffeinateApplication: CaffeinateApplication

        /**
         * Retrieves a Bitmap from the drawable resource and applies a tint with the theme's primary color.
         *
         * @receiver [DrawableRes] The drawable resource ID to convert into a Bitmap.
         *
         * @return [Bitmap] The tinted Bitmap representation of the drawable resource.
         *
         * @throws [UninitializedPropertyAccessException] if the application context is not initialized.
         * @throws [android.content.res.Resources.NotFoundException] if the drawable resource ID is not valid.
         *
         * @see AppCompatResources
         * @see AppCompatResources.getDrawable
         * @see GradientDrawable
         * @see UninitializedPropertyAccessException
         * @see android.content.res.Resources.NotFoundException
         */
        private val @receiver:DrawableRes Int.themeColored: Bitmap
            get() = caffeinateApplication.run {
                (AppCompatResources.getDrawable(applicationContext, this@themeColored) as GradientDrawable)
                    .apply { if (themeColor != 0) setColor(themeColor) }.toBitmap()
            }

        /**
         * Retrieves a Bitmap from the drawable resource and applies a tint with the specified background color.
         *
         * @receiver [DrawableRes] The drawable resource ID to convert into a Bitmap.
         *
         * @return [Bitmap] The tinted Bitmap representation of the drawable resource.
         *
         * @throws [UninitializedPropertyAccessException] if the application context is not initialized.
         * @throws [android.content.res.Resources.NotFoundException] if the drawable resource ID is not valid.
         *
         * @see AppCompatResources
         * @see AppCompatResources.getDrawable
         * @see Drawable
         * @see UninitializedPropertyAccessException
         * @see android.content.res.Resources.NotFoundException
         */
        private val @receiver:DrawableRes Int.backgroundColored: Bitmap
            get() = caffeinateApplication.run {
                (AppCompatResources.getDrawable(applicationContext, this@backgroundColored) as Drawable)
                    .apply { if (backgroundColor != 0) setTint(backgroundColor) }.toBitmap()
            }

        /**
         * Sets the click listeners for the widget.
         *
         * This method sets the [PendingIntent] for the clicks on the widget text and image views. The [PendingIntent] is used to start the [Widget]
         * when the user clicks on the widget.
         *
         * @receiver [RemoteViews] the remote views of the widget.
         * @param context [Context] the context of the app.
         */
        private fun RemoteViews.setClickListeners(context: Context) {
            val widgetView = apply(context, null)
            val widgetBinding = WidgetBinding.bind(widgetView)
            val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(context, Widget::class.java).apply { action = "CLICK" },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            setOnClickPendingIntent(widgetBinding.widgetRoot.id, pendingIntent)
        }

        /**
         * Updates the widget with the given id.
         *
         * @param appWidgetManager [AppWidgetManager] the widget manager
         * @param appWidgetId [Int] the id of the widget to update
         *
         * @see AppWidgetManager
         * @see AppWidgetProvider
         * @see CaffeinateApplication
         */
        private fun updateAppWidget(appWidgetManager: AppWidgetManager, appWidgetId: Int) = caffeinateApplication.run {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val showBackground = options.getBoolean("showBackground")
            val views = RemoteViews(applicationContext.packageName, R.layout.widget)
            val widgetView = views.apply(applicationContext, null)
            val widgetBinding = WidgetBinding.bind(widgetView)
            val widgetText = when (val status = caffeinateApplication.lastStatusUpdate) {
                is ServiceStatus.Stopped -> getString(R.string.caffeinate_button_off)
                is ServiceStatus.Running -> status.remaining.toLocalizedFormattedTime(localizedApplicationContext)
            }

            views.run {
                setViewVisibility(widgetBinding.widgetBackground.id, if (showBackground) View.VISIBLE else View.GONE)
                setImageViewBitmap(widgetBinding.widgetBackground.id, R.drawable.widget_background.backgroundColored)

                setTextViewText(widgetBinding.widgetLabel.id, getString(R.string.app_name))
                setTextViewText(widgetBinding.widgetText.id, widgetText)

                when (lastStatusUpdate) {
                    is ServiceStatus.Stopped -> {
                        setImageViewResource(widgetBinding.widgetBorder.id, R.drawable.widget_background_off)
                        setImageViewResource(widgetBinding.widgetImageView.id, R.drawable.outline_coffee_24)
                    }

                    is ServiceStatus.Running -> {
                        setImageViewBitmap(widgetBinding.widgetBorder.id, R.drawable.widget_background_on.themeColored)
                        setImageViewResource(widgetBinding.widgetImageView.id, R.drawable.baseline_coffee_24)
                    }
                }

                setClickListeners(applicationContext)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /**
         * Updates all widgets with the new status of the KeepAwakeService.
         *
         * This method is called whenever the status of the KeepAwakeService changes, such as when it is started or stopped. It updates the text and
         * image view of all widgets with the new status.
         *
         * The method takes a single parameter, [context], which is the context of the app. This parameter is used to access
         * the [AppWidgetManager] and the [Context] of the app.
         *
         * The method first gets the IDs of all widgets from the [AppWidgetManager] using the [AppWidgetManager.getAppWidgetIds] method. It then loops
         * over the IDs and calls the [updateAppWidget] method to update each widget.
         *
         * @param context [Context] the context of the app
         *
         * @see AppWidgetManager
         * @see AppWidgetProvider
         * @see CaffeinateApplication
         */
        fun updateAllWidgets(context: Context) = context.run {
            caffeinateApplication = applicationContext as CaffeinateApplication
            val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
            val widgetComponent = ComponentName(applicationContext, Widget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
            val widgetText = when (val status = caffeinateApplication.lastStatusUpdate) {
                is ServiceStatus.Stopped -> getString(R.string.caffeinate_button_off)
                is ServiceStatus.Running -> status.remaining.toFormattedTime()
            }

            appWidgetIds.forEach { appWidgetId -> updateAppWidget(appWidgetManager, appWidgetId) }
            // updateAppWidget(appWidgetManager, appWidgetIds)
            if (appWidgetIds.isNotEmpty()) Log.d(
                    "${appWidgetIds.size} widgets updated, " +
                    "widgetIds: ${appWidgetIds.joinToString(", ")}, " +
                    "widgetText: $widgetText"
            )
        }
    }
}