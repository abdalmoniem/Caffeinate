package com.hifnawy.caffeinate.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import com.google.android.material.card.MaterialCardView
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.databinding.ActivityWidgetConfigurationBinding
import com.hifnawy.caffeinate.databinding.WidgetBinding
import com.hifnawy.caffeinate.services.ServiceStatus
import com.hifnawy.caffeinate.services.ServiceStatusObserver
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toLocalizedFormattedTime
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
     * The binding for the activity's layout.
     *
     * This field is used to access the views and resources defined in the
     * activity's layout file. It is initialized in the [onCreate] method
     * using view binding.
     */
    private lateinit var binding: ActivityWidgetConfigurationBinding

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
        enableEdgeToEdge()
        binding = ActivityWidgetConfigurationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        with(binding) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val widgetsConfiguration = sharedPreferences.widgetsConfiguration.ifEmpty { mutableMapOf() }
            val widgetPreviewsConfiguration = mutableMapOf(
                    widgetPreviewCard1.id to WidgetConfiguration(appWidgetId, true),
                    widgetPreviewCard2.id to WidgetConfiguration(appWidgetId, false)
            )

            Log.d("Configuring widget $appWidgetId")
            val clickListener = View.OnClickListener { view ->
                view ?: return@OnClickListener
                val widgetConfiguration = widgetPreviewsConfiguration[view.id] ?: return@OnClickListener

                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                sharedPreferences.widgetsConfiguration = widgetsConfiguration.apply {
                    set(appWidgetId, widgetConfiguration)
                }

                Widget.updateAllWidgets(this@WidgetConfigurationActivity)

                Log.d("Configured widget $appWidgetId, widgetsConfiguration: $widgetsConfiguration")

                setResult(RESULT_OK, Intent().apply { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId) })
                finish()
            }

            widgetPreviewsConfiguration.keys.forEach { findViewById<MaterialCardView>(it)?.setOnClickListener(clickListener) }
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
        widgetPreview1.setImageBitmap(widgetPreview(true))
        widgetPreview2.setImageBitmap(widgetPreview(false))
    }

    /**
     * Returns a [Bitmap] representing a preview of the Caffeinate widget with the specified [showBackground].
     *
     * @param showBackground [Boolean] `true` if the background should be shown in the preview, `false` otherwise.
     *
     * @return [Bitmap] the preview of the Caffeinate widget.
     */
    private fun widgetPreview(showBackground: Boolean): Bitmap = WidgetBinding.inflate(layoutInflater).let { widgetBinding ->
        val textColor = when {
            showBackground -> getColor(R.color.colorWidgetTextOnBackground)
            else           -> getColor(R.color.colorWidgetText)
        }
        val iconColor = when {
            showBackground -> getColor(R.color.colorWidgetIconOnBackground)
            else           -> getColor(R.color.colorWidgetIcon)
        }
        val iconFillColor = when {
            showBackground -> getColor(R.color.colorWidgetIconFillOnBackground)
            else           -> getColor(R.color.colorWidgetIconFill)
        }
        val widgetIcon = when (caffeinateApplication.lastStatusUpdate) {
            is ServiceStatus.Stopped -> AppCompatResources.getDrawable(caffeinateApplication, R.drawable.outline_coffee_24)
            is ServiceStatus.Running -> AppCompatResources.getDrawable(caffeinateApplication, R.drawable.baseline_coffee_24)
        }?.apply {
            setTint(iconColor)
        }?.toBitmap()
        val widgetIconFill = AppCompatResources.getDrawable(caffeinateApplication, R.drawable.widget_icon_fill)?.apply {
            setTint(iconFillColor)
        }?.toBitmap()
        val widgetBorder = AppCompatResources.getDrawable(caffeinateApplication, R.drawable.widget_border)?.apply {
            setTint(iconColor)
        }?.toBitmap()
        val backgroundVisibility = when {
            showBackground -> View.VISIBLE
            else           -> View.GONE
        }
        val iconFillVisibility = when (caffeinateApplication.lastStatusUpdate) {
            is ServiceStatus.Stopped -> View.GONE
            is ServiceStatus.Running -> View.VISIBLE
        }
        val widgetText = when (val status = caffeinateApplication.lastStatusUpdate) {
            is ServiceStatus.Stopped -> getString(R.string.caffeinate_button_off)
            is ServiceStatus.Running -> status.remaining.toLocalizedFormattedTime(caffeinateApplication.localizedApplicationContext)
        }

        widgetBinding.widgetBackground.visibility = backgroundVisibility

        widgetBinding.widgetText.setTextColor(textColor)
        widgetBinding.widgetText.text = widgetText
        widgetBinding.widgetIconFill.visibility = iconFillVisibility
        widgetBinding.widgetIconFill.setImageBitmap(widgetIconFill)
        widgetBinding.widgetBorder.setImageBitmap(widgetBorder)
        widgetBinding.widgetIcon.setImageBitmap(widgetIcon)
        widgetBinding.widgetLabel.setTextColor(textColor)
        widgetBinding.widgetLabel.text = getString(R.string.app_name)

        widgetBinding.root.run {
            measure(
                    View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY)
            )
            layout(0, 0, measuredWidth, measuredHeight)
            val bitmap = Bitmap.createBitmap(measuredWidth, measuredHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            draw(canvas)

            bitmap
        }
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
