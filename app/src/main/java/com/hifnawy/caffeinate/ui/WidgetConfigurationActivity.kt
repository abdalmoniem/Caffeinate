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
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.databinding.ActivityWidgetConfigurationBinding
import com.hifnawy.caffeinate.databinding.WidgetBinding

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
            val appWidgetManager = AppWidgetManager.getInstance(this@WidgetConfigurationActivity)

            widgetPreviewCard1.setOnClickListener { cardView ->
                cardView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                val options = Bundle().apply { putBoolean("showBackground", true) }
                appWidgetManager.updateAppWidgetOptions(appWidgetId, options)

                setResult(RESULT_OK, Intent().apply { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId) })
                finish()
            }

            widgetPreviewCard2.setOnClickListener { cardView ->
                cardView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                val options = Bundle().apply { putBoolean("showBackground", false) }
                appWidgetManager.updateAppWidgetOptions(appWidgetId, options)

                setResult(RESULT_OK, Intent().apply { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId) })
                finish()
            }
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
                widgetBackground.setColorFilter((application as CaffeinateApplication).backgroundColor)
                widgetBackground.visibility = if (showBackground) View.VISIBLE else View.GONE

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