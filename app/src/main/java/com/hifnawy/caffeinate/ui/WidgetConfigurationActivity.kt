package com.hifnawy.caffeinate.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.card.MaterialCardView
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.databinding.ActivityWidgetConfigurationBinding
import com.hifnawy.caffeinate.databinding.WidgetBinding
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
class WidgetConfigurationActivity : AppCompatActivity() {

    /**
     * The binding for the activity's layout.
     *
     * This field is used to access the views and resources defined in the
     * activity's layout file. It is initialized in the [onCreate] method
     * using view binding.
     */
    private lateinit var binding: ActivityWidgetConfigurationBinding

    /**
     * A [SharedPrefsManager] instance for accessing and modifying the application's
     * shared preferences.
     *
     * This field is initialized lazily when it is first accessed. The instance is
     * created from the [CaffeinateApplication] instance that is the context of this activity.
     */
    private val sharedPreferences by lazy { SharedPrefsManager(application as CaffeinateApplication) }

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
            widgetPreview1.setImageBitmap(widgetPreview(true))
            widgetPreview2.setImageBitmap(widgetPreview(false))
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
    }

    /**
     * Returns a [Bitmap] representing a preview of the Caffeinate widget with the specified [showBackground].
     *
     * @param showBackground [Boolean] `true` if the background should be shown in the preview, `false` otherwise.
     *
     * @return [Bitmap] the preview of the Caffeinate widget.
     */
    private fun widgetPreview(showBackground: Boolean): Bitmap =
            WidgetBinding.inflate(layoutInflater).run {
                val iconOff = AppCompatResources.getDrawable(this@WidgetConfigurationActivity, R.drawable.outline_coffee_24)
                    ?.apply {
                        mutate()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            colorFilter = BlendModeColorFilter(getColor(R.color.colorNeutralVariant), BlendMode.SRC_IN)
                        } else {
                            @Suppress("DEPRECATION")
                            setColorFilter(getColor(R.color.colorNeutralVariant), PorterDuff.Mode.SRC_IN)
                        }
                    }

                widgetBackground.visibility = if (showBackground) View.VISIBLE else View.GONE
                widgetImageView.setImageDrawable(iconOff)

                root.run {
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
