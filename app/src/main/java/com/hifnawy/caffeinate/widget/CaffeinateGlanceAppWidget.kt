package com.hifnawy.caffeinate.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.glance.Background
import androidx.glance.Color
import androidx.glance.GlanceAppWidget
import androidx.glance.GlanceModifier
import androidx.glance.action.sendActionWithPayload
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.material.MaterialTheme
import androidx.glance.material.Surface
import androidx.glance.text.Text
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.controller.KeepAwakeService
import com.hifnawy.caffeinate.controller.ServiceStatus
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toLocalizedFormattedTime

/**
 * A Glance app widget that reflects the current Caffeinate service state and
 * lets the user toggle the service with a tap.
 *
 * Behavior parity with the previous [RemoteViews]-based widget:
 * - Shows "off" text / icon when the service is stopped.
 * - Shows the remaining time when the service is running.
 * - Tapping the widget calls [KeepAwakeService.startNextTimeout] to start /
 *   cycle to the next timeout.
 */
class CaffeinateGlanceAppWidget : GlanceAppWidget() {

    @Composable
    override fun provideGlance(context: Context, appWidgetId: Int) {
        val app = context.applicationContext as? CaffeinateApplication
        val status = app?.lastStatusUpdate
        val text = when {
            status is ServiceStatus.Stopped -> context.getString(R.string.caffeinate_button_off)
            status is ServiceStatus.Running -> status.remaining.toLocalizedFormattedTime(context)
            else -> context.getString(R.string.caffeinate_button_off)
        }

        Surface(
            modifier = GlanceModifier.fillMaxSize(),
            background = Background.Color(Color(0xFF1976D2)),
            onClick = sendActionWithPayload(CaffeinateGlanceReceiver.ACTION_TOGGLE, null)
        ) {
            Text(
                text = text,
                modifier = GlanceModifier.matchParent(),
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

/**
 * Receiver for the Caffeinate Glance app widget.
 *
 * Handles widget update broadcasts and the custom toggle action sent when the
 * user taps the widget.
 */
class CaffeinateGlanceReceiver : GlanceAppWidgetReceiver() {

    override fun provideGlance(context: Context, appWidgetId: Int): GlanceAppWidget {
        return CaffeinateGlanceAppWidget()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) {
            val app = context.applicationContext
            if (app is CaffeinateApplication) {
                KeepAwakeService.startNextTimeout(app)
            }
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_TOGGLE = "com.hifnawy.caffeinate.widget.ACTION_TOGGLE"
    }
}
