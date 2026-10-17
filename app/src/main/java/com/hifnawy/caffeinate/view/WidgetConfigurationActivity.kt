package com.hifnawy.caffeinate.view

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.setContent
import androidx.annotation.Keep
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.controller.SharedPrefsManager
import com.hifnawy.caffeinate.ui.theme.CaffeinateTheme
import com.hifnawy.caffeinate.widget.CaffeinateGlanceAppWidget
import com.hifnawy.caffeinate.widget.CaffeinateGlanceReceiver
import java.io.Serializable

/**
 * Widget configuration activity rewritten with Jetpack Compose.
 *
 * Lets the user choose whether the widget shows its background, then saves the
 * choice to [SharedPrefsManager.widgetsConfiguration] and refreshes all Glance
 * widgets. Behaviour parity with the previous Views-based implementation is
 * preserved; only the UI toolkit changed.
 */
class WidgetConfigurationActivity : ComponentActivity() {

    private val caffeinateApplication by lazy { application as CaffeinateApplication }
    private val sharedPreferences by lazy { SharedPrefsManager(caffeinateApplication) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences.run {
            setActivityTheme(contrastLevel, theme.mode, isMaterialYouEnabled)
        }

        setContent {
            val context = LocalContext.current
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val currentConfig = sharedPreferences.widgetsConfiguration[appWidgetId]
            var showBackground by remember { mutableStateOf(currentConfig?.showBackground ?: false) }

            CaffeinateTheme(
                darkTheme = when (sharedPreferences.theme) {
                    SharedPrefsManager.Theme.DARK -> true
                    SharedPrefsManager.Theme.LIGHT -> false
                    else -> LocalContext.current.applicationContext.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
                },
                dynamicColor = sharedPreferences.isMaterialYouEnabled &&
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S,
                contrastLevel = sharedPreferences.contrastLevel
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(text = stringResource(id = R.string.app_name)) },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.configure_widget),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = stringResource(id = R.string.widget_show_background),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = stringResource(id = R.string.widget_show_background_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = showBackground,
                                onCheckedChange = { showBackground = it }
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // Preview
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(
                                    if (showBackground) MaterialTheme.colorScheme.surfaceContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .clickable(onClick = { showBackground = !showBackground })
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (showBackground)
                                    stringResource(id = R.string.widget_background_on)
                                else stringResource(id = R.string.widget_background_off),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = {
                                val configs = sharedPreferences.widgetsConfiguration.toMutableMap()
                                configs[appWidgetId] = com.hifnawy.caffeinate.view.WidgetConfiguration(appWidgetId, showBackground)
                                sharedPreferences.widgetsConfiguration = configs
                                caffeinateApplication.updateAllGlanceWidgets()
                                setResult(RESULT_OK, Intent().apply {
                                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                })
                                finish()
                            },
                            enabled = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = stringResource(android.R.string.ok))
                        }
                    }
                }
            }
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
 */
@Keep
data class WidgetConfiguration(
    @Keep val widgetId: Int,
    @Keep val showBackground: Boolean
) : Serializable
