package com.hifnawy.caffeinate.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.databinding.ActivityWidgetConfigurationBinding
import com.hifnawy.caffeinate.services.ServiceStatus
import com.hifnawy.caffeinate.services.ServiceStatusObserver
import com.hifnawy.caffeinate.utils.ColorUtil
import com.hifnawy.caffeinate.utils.MutableListExtensionFunctions.addObserver
import com.hifnawy.caffeinate.utils.MutableListExtensionFunctions.removeObserver
import com.hifnawy.caffeinate.utils.SharedPrefsManager
import com.hifnawy.caffeinate.widgets.Widget
import java.io.Serializable
import timber.log.Timber as Log

/**
 * Activity for configuring the widget.
 *
 * This activity provides the user interface for configuring the appearance and behavior of the widget.
 * It allows users to preview different widget styles and select their preferred configuration.
 *
 * The activity handles the interaction with the [AppWidgetManager] to update the widget options based
 * on the user's selection and ensures the result is set appropriately before finishing.
 */
class WidgetConfigurationActivity : AppCompatActivity(), ServiceStatusObserver {

    /**
     * Lazily initializes the [ActivityWidgetConfigurationBinding] for this activity.
     *
     * This binding is used to access and manipulate the views in the layout file
     * associated with this activity. It is inflated using the [getLayoutInflater] and
     * provides a type-safe way to interact with the views.
     *
     * @return [ActivityWidgetConfigurationBinding] the binding instance for this activity.
     */
    private val binding by lazy { ActivityWidgetConfigurationBinding.inflate(layoutInflater) }

    /**
     * The [CaffeinateApplication] instance that is the context of this activity.
     *
     * This field is initialized lazily when it is first accessed. The instance is
     * created by casting the [android.app.Application] context of this activity to
     * [CaffeinateApplication].
     *
     * @return [CaffeinateApplication] the application instance.
     */
    private val caffeinateApplication by lazy { application as CaffeinateApplication }

    /**
     * A [SharedPrefsManager] instance for accessing and modifying the application's
     * shared preferences.
     *
     * This field is initialized lazily when it is first accessed. The instance is
     * created from the [CaffeinateApplication] instance that is the context of this activity.
     */
    private val sharedPreferences by lazy { SharedPrefsManager(caffeinateApplication) }
    /**
     * A lazy delegate that provides an instance of [ColorUtil] for managing colors.
     *
     * This delegate is used to access and modify the application's colors, allowing the service
     * to respond to changes in the application's theme.
     *
     * @return [ColorUtil] the instance for handling colors.
     *
     * @see ColorUtil
     */
    /**
     * Lazily initializes the [RemoteViews] for the widget.
     *
     * This [RemoteViews] instance is used to manage the layout of the widget,
     * allowing the application to update its UI remotely.
     *
     * @return [RemoteViews] the remote views instance for the widget.
     */
    private val remoteViews by lazy { RemoteViews(packageName, R.layout.widget) }

    /**
     * Called when the activity is starting.
     *
     * This method is where the activity is initialized. It is called after [onRestoreInstanceState] and before [onStart].
     *
     * The activity is being re-initialized after being shut down, this method is called with the saved state that the activity previously supplied in [onSaveInstanceState].
     * Otherwise, it is called with a null state.
     *
     * @param savedInstanceState [Bundle] If the activity is being re-initialized after being shut down, then this [Bundle] contains the data it most recently supplied in [onSaveInstanceState].
     * Otherwise it is null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppCompatDelegate.setDefaultNightMode(sharedPreferences.theme.value)
        if (DynamicColors.isDynamicColorAvailable() && sharedPreferences.isMaterialYouEnabled) {
            setTheme(R.style.Theme_Caffeinate_Dynamic)
            DynamicColors.applyToActivityIfAvailable(this)
        } else setTheme(R.style.Theme_Caffeinate_Baseline)

        enableEdgeToEdge()
        setContentView(binding.root)

        with(binding) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val widgetsConfiguration = sharedPreferences.widgetsConfiguration.ifEmpty { mutableMapOf() }
            val widgetPreviewsConfiguration = mutableMapOf(
                    widgetPreviewContainer1.id to WidgetConfiguration(appWidgetId, true),
                    widgetPreviewContainer2.id to WidgetConfiguration(appWidgetId, false)
            )

            Log.d("Configuring widget $appWidgetId")
            val clickListener = View.OnClickListener { view ->
                view ?: return@OnClickListener
                val widgetConfiguration = widgetPreviewsConfiguration[view.tag] ?: return@OnClickListener

                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                sharedPreferences.widgetsConfiguration = widgetsConfiguration.apply {
                    set(appWidgetId, widgetConfiguration)
                }

                Widget.updateAllWidgets(this@WidgetConfigurationActivity)

                Log.d("Configured widget $appWidgetId, widgetsConfiguration: $widgetsConfiguration")

                setResult(RESULT_OK, Intent().apply { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId) })
                finish()
            }

            widgetPreviewsConfiguration.forEach { entry ->
                val widgetPreviewContainer = findViewById<FrameLayout>(entry.key) ?: return@forEach
                widgetPreviewContainer.removeAllViews()
                addWidgetPreview(widgetPreviewContainer, entry.value.showBackground)
                val widgetPreviewContainerParent = widgetPreviewContainer.parent as MaterialCardView
                widgetPreviewContainerParent.apply {
                    tag = entry.key
                    widgetPreviewContainerParent.setOnClickListener(clickListener)
                }
            }
        }

        onServiceStatusUpdated(caffeinateApplication.lastStatusUpdate)
    }

    /**
     * Called after [onRestoreInstanceState], [onRestart], or [onPause], for your activity to start interacting with the user. This is a good place to
     * begin animations, open exclusive-access devices (such as the camera), etc.
     *
     * Keep in mind that onResume is not the best indicator that your activity is visible to the user (as described in the ActivityLifecycle document).
     *
     * @see [onPause]
     * @see [onStop]
     * @see [onDestroy]
     */
    override fun onResume() = caffeinateApplication.run {
        super.onResume()
        keepAwakeServiceObservers.addObserver(this@WidgetConfigurationActivity)
    }

    /**
     * Called as part of the activity lifecycle when an activity is going into the background, but has not (yet) been destroyed. Use this method to
     * release resources, such as broadcast receivers, that will not be needed while the activity is paused.
     *
     * This is usually a good place to commit unsaved changes to persistent data, stop animations and other ongoing actions, etc.
     *
     * @see [onResume]
     * @see [onStop]
     * @see [onDestroy]
     */
    override fun onPause() = caffeinateApplication.run {
        super.onPause()
        caffeinateApplication.keepAwakeServiceObservers.removeObserver(this@WidgetConfigurationActivity)
    }

    /**
     * Called when the status of the Caffeinate service is updated.
     *
     * @param status [ServiceStatus] the new status of the service
     */
    override fun onServiceStatusUpdated(status: ServiceStatus) = with(binding) {
        updateWidgetPreview(widgetPreviewContainer1, true)
        updateWidgetPreview(widgetPreviewContainer2, false)
    }

    /**
     * Adds a widget preview to the specified container.
     *
     * @param widgetPreviewContainer [FrameLayout] the container to add the widget preview to
     * @param showBackground [Boolean] `true` if the widget preview should show its background, `false` otherwise
     */
    private fun addWidgetPreview(widgetPreviewContainer: FrameLayout, showBackground: Boolean) {
        val widgetView = remoteViews.apply(applicationContext, widgetPreviewContainer)
        widgetPreviewContainer.addView(widgetView)

        updateWidgetPreview(widgetPreviewContainer, showBackground)
    }

    /**
     * Updates the widget preview in the specified container to show or hide its background depending on the given flag.
     *
     * @param widgetPreviewContainer [FrameLayout] the container holding the widget preview
     * @param showBackground [Boolean] `true` if the widget preview should show its background, `false` otherwise
     */
    private fun updateWidgetPreview(widgetPreviewContainer: FrameLayout, showBackground: Boolean) {
        Widget.updateRemoteViews(remoteViews, showBackground)
        remoteViews.reapply(applicationContext, widgetPreviewContainer)
    }
}

/**
 * Data class representing the configuration of a Caffeinate widget.
 *
 * This class encapsulates the configuration of a single Caffeinate widget, including its ID and whether it should show its background.
 *
 * @property widgetId [Int] the ID of the widget. This is the unique identifier of the widget as assigned by the
 * [AppWidgetManager] when the widget is created.
 * @property showBackground [Boolean] `true` if the widget should show its background, `false` otherwise.
 *
 * @author AbdAlMoniem AlHifnawy
 */
data class WidgetConfiguration(val widgetId: Int, val showBackground: Boolean) : Serializable
