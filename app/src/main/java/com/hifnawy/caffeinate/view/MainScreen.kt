package com.hifnawy.caffeinate.view

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.controller.KeepAwakeService
import com.hifnawy.caffeinate.controller.ServiceStatus
import com.hifnawy.caffeinate.controller.SharedPrefsManager
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toLocalizedFormattedTime
import com.google.android.material.color.DynamicColors

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    context: Context,
    app: CaffeinateApplication,
    prefs: SharedPrefsManager,
    serviceStatus: ServiceStatus,
    isAllPermissionsGranted: Boolean,
    isPictureInPictureSupported: Boolean,
    isInPictureInPictureMode: Boolean,
    onTimeoutsChosen: () -> Unit,
    notificationPermissionLauncher: ActivityResultLauncher<Array<String>>
) {
    Column(modifier = modifier) {
        Text(text = stringResource(id = R.string.app_name), style = MaterialTheme.typography.headlineSmall)

        if (isInPictureInPictureMode) {
            PictureInPictureContent(serviceStatus = serviceStatus, context = context)
        } else {
            MainContent(
                context = context,
                app = app,
                prefs = prefs,
                serviceStatus = serviceStatus,
                isAllPermissionsGranted = isAllPermissionsGranted,
                isPictureInPictureSupported = isPictureInPictureSupported,
                onTimeoutsChosen = onTimeoutsChosen,
                notificationPermissionLauncher = notificationPermissionLauncher
            )
        }
    }
}

@Composable
private fun MainContent(
    context: Context,
    app: CaffeinateApplication,
    prefs: SharedPrefsManager,
    serviceStatus: ServiceStatus,
    isAllPermissionsGranted: Boolean,
    isPictureInPictureSupported: Boolean,
    onTimeoutsChosen: () -> Unit,
    notificationPermissionLauncher: ActivityResultLauncher<Array<String>>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(text = stringResource(id = R.string.permissions_card_title), style = MaterialTheme.typography.titleMedium)

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
            Column {
                val permissions = listOf(
                    PermissionCardType.BATTERY_OPTIMIZATION to checkBatteryOptimization(context),
                    PermissionCardType.BACKGROUND_OPTIMIZATION to checkBackgroundOptimization(context),
                    PermissionCardType.NOTIFICATION to checkNotificationPermission(context),
                    PermissionCardType.OVERLAY to checkOverlayPermission(context)
                )
                permissions.forEachIndexed { index, pair ->
                    val type = pair.first
                    val isGranted = pair.second
                    PermissionCardRow(
                        type = type,
                        isGranted = isGranted,
                        context = context,
                        app = app,
                        prefs = prefs,
                        notificationPermissionLauncher = notificationPermissionLauncher,
                        onClick = { if (!isGranted) onPermissionCardClicked(context, type, app, prefs, notificationPermissionLauncher) }
                    )
                    if (index != permissions.lastIndex) Divider()
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = stringResource(id = R.string.settings_card_title), style = MaterialTheme.typography.titleMedium)

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
            Column {
                SettingCardRow(
                    title = stringResource(id = R.string.app_theme_pref),
                    iconRes = R.drawable.pref_theme_icon,
                    isEnabled = isAllPermissionsGranted,
                    trailing = {
                        ThemeToggleRow(
                            selectedTheme = prefs.theme,
                            onThemeSelected = { theme -> changeThemeAndContrast(prefs, theme, prefs.contrastLevel, prefs.isMaterialYouEnabled) }
                        )
                    },
                    onClick = {}
                )
                Divider()

                SettingCardRow(
                    title = stringResource(id = R.string.contrast),
                    iconRes = R.drawable.pref_contrast_icon,
                    isEnabled = isAllPermissionsGranted,
                    trailing = {
                        ContrastToggleRow(
                            selectedContrast = prefs.contrastLevel,
                            onContrastSelected = { contrast -> changeThemeAndContrast(prefs, prefs.theme, contrast, prefs.isMaterialYouEnabled) }
                        )
                    },
                    onClick = {}
                )
                Divider()

                SettingCardRow(
                    title = stringResource(id = R.string.material_you_pref),
                    iconRes = R.drawable.pref_material_you_icon,
                    subtitle = if (DynamicColors.isDynamicColorAvailable())
                        stringResource(id = R.string.material_you_pref_description)
                    else stringResource(id = R.string.material_you_pref_not_available),
                    isEnabled = isAllPermissionsGranted && DynamicColors.isDynamicColorAvailable(),
                    trailing = {
                        Switch(
                            checked = prefs.isMaterialYouEnabled && prefs.contrastLevel == SharedPrefsManager.ContrastLevel.STANDARD,
                            onCheckedChange = { checked -> changeThemeAndContrast(prefs, prefs.theme, prefs.contrastLevel, checked) },
                            enabled = isAllPermissionsGranted && DynamicColors.isDynamicColorAvailable()
                        )
                    },
                    onClick = { onMaterialYouToggled(prefs) }
                )
                Divider()

                SettingCardRow(
                    title = stringResource(id = R.string.quick_tile_title_pref),
                    iconRes = R.drawable.prefs_quick_tile_icon,
                    subtitle = stringResource(id = R.string.quick_tile_title_pref_description),
                    isEnabled = isAllPermissionsGranted,
                    trailing = {
                        Switch(
                            checked = prefs.isShowStatusInQuickTileTitleEnabled,
                            onCheckedChange = { checked -> prefs.isShowStatusInQuickTileTitleEnabled = checked },
                            enabled = isAllPermissionsGranted
                        )
                    },
                    onClick = { onQuickTileStatusToggled(prefs) }
                )
                Divider()

                SettingCardRow(
                    title = stringResource(id = R.string.show_overlay_pref),
                    iconRes = R.drawable.pref_overlay_icon,
                    subtitle = stringResource(id = R.string.show_overlay_pref_description),
                    isEnabled = isAllPermissionsGranted && checkOverlayPermission(context),
                    trailing = {
                        Switch(
                            checked = prefs.isOverlayEnabled,
                            onCheckedChange = { checked -> prefs.isOverlayEnabled = checked },
                            enabled = isAllPermissionsGranted && checkOverlayPermission(context)
                        )
                    },
                    onClick = { onOverlayToggled(prefs, context) }
                )
                Divider()

                SettingCardRow(
                    title = stringResource(id = R.string.picture_in_picture_pref),
                    iconRes = R.drawable.pref_picture_in_picture_icon,
                    subtitle = stringResource(id = R.string.picture_in_picture_pref_description),
                    isEnabled = isAllPermissionsGranted,
                    trailing = {
                        Switch(
                            checked = prefs.isPictureInPictureEnabled,
                            onCheckedChange = { checked -> prefs.isPictureInPictureEnabled = checked },
                            enabled = isAllPermissionsGranted
                        )
                    },
                    onClick = { onPictureInPictureToggled(prefs) }
                )
                Divider()

                SettingCardRow(
                    title = stringResource(id = R.string.allow_dimming_pref),
                    iconRes = R.drawable.pref_dim_icon,
                    subtitle = stringResource(id = R.string.allow_dimming_pref_description),
                    isEnabled = isAllPermissionsGranted,
                    trailing = {
                        Switch(
                            checked = prefs.isDimmingEnabled,
                            onCheckedChange = { checked -> prefs.isDimmingEnabled = checked },
                            enabled = isAllPermissionsGranted
                        )
                    },
                    onClick = { onDimmingToggled(prefs) }
                )
                Divider()

                SettingCardRow(
                    title = stringResource(id = R.string.allow_while_locked_pref),
                    iconRes = R.drawable.pref_lock_icon,
                    subtitle = stringResource(id = R.string.allow_while_locked_pref_description),
                    isEnabled = isAllPermissionsGranted,
                    trailing = {
                        Switch(
                            checked = prefs.isWhileLockedEnabled,
                            onCheckedChange = { checked -> prefs.isWhileLockedEnabled = checked },
                            enabled = isAllPermissionsGranted
                        )
                    },
                    onClick = { onWhileLockedToggled(prefs) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = stringResource(id = R.string.timeouts_pref), style = MaterialTheme.typography.titleMedium)

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text(
                        text = prefs.timeoutCheckBoxes.joinToString(", ") { it.text },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onTimeoutsChosen,
                        enabled = isAllPermissionsGranted
                    ) {
                        Text(text = stringResource(id = R.string.timeouts_pref_change))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        val remainingText = when (serviceStatus) {
            is ServiceStatus.Stopped -> context.getString(R.string.caffeinate_button_off)
            is ServiceStatus.Running -> serviceStatus.remaining.toLocalizedFormattedTime(context)
        }

        Button(
            onClick = { onToggleClicked(app, prefs) },
            enabled = isAllPermissionsGranted,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (serviceStatus is ServiceStatus.Running)
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(text = remainingText, style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(16.dp))

        val isRestartButtonEnabled = (serviceStatus as? ServiceStatus.Running)?.run {
            (!isRestarted && isCountingDown) || isRestarted
        } ?: false

        if (isRestartButtonEnabled) {
            Button(
                onClick = { onRestartClicked(app, prefs) },
                enabled = isAllPermissionsGranted,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(text = "●")
                    Text(text = stringResource(id = R.string.action_restart_timeout))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PictureInPictureContent(serviceStatus: ServiceStatus, context: Context) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            progress = 0f,
            modifier = Modifier.size(200.dp)
        )
        Text(
            text = when (serviceStatus) {
                is ServiceStatus.Stopped -> context.getString(R.string.caffeinate_button_off)
                is ServiceStatus.Running -> serviceStatus.remaining.toLocalizedFormattedTime(context)
            },
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 24.dp)
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 15.dp)
    )
}

@Composable
private fun PermissionCardRow(
    type: PermissionCardType,
    isGranted: Boolean,
    context: Context,
    app: CaffeinateApplication,
    prefs: SharedPrefsManager,
    notificationPermissionLauncher: ActivityResultLauncher<Array<String>>,
    onClick: () -> Unit
) {
    val pair = when (type) {
        PermissionCardType.BATTERY_OPTIMIZATION -> R.drawable.permission_battery_optimization_icon to
            stringResource(id = R.string.battery_optimization_granted) to
            stringResource(id = R.string.battery_optimization_not_granted)
        PermissionCardType.BACKGROUND_OPTIMIZATION -> R.drawable.permission_background_optimization_icon to
            stringResource(id = R.string.background_optimization_granted) to
            stringResource(id = R.string.background_optimization_not_granted)
        PermissionCardType.NOTIFICATION -> R.drawable.permission_notifications_icon to
            stringResource(id = R.string.notifications_permission_granted) to
            stringResource(id = R.string.notifications_permission_not_granted)
        PermissionCardType.OVERLAY -> R.drawable.permission_draw_on_top_icon to
            stringResource(id = R.string.overlay_permission_granted) to
            stringResource(id = R.string.overlay_permission_not_granted)
    }
    val iconRes = pair.first.first
    val grantedText = pair.first.second
    val notGrantedText = pair.second

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable(onClick = onClick)
            .height(48.dp)
    ) {
        Text(text = if (isGranted) grantedText else notGrantedText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SettingCardRow(
    title: String,
    iconRes: Int,
    subtitle: String? = null,
    isEnabled: Boolean,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable(onClick = onClick)
            .height(56.dp)
    ) {
        Text(text = "●")
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        trailing()
    }
}

@Composable
private fun ThemeToggleRow(
    selectedTheme: SharedPrefsManager.Theme,
    onThemeSelected: (SharedPrefsManager.Theme) -> Unit
) {
    Row {
        val themes = listOf(
            SharedPrefsManager.Theme.SYSTEM_DEFAULT to stringResource(id = R.string.app_theme_system_default),
            SharedPrefsManager.Theme.LIGHT to stringResource(id = R.string.app_theme_system_light),
            SharedPrefsManager.Theme.DARK to stringResource(id = R.string.app_theme_system_dark)
        )
        themes.forEachIndexed { index, pair ->
            val (theme, label) = pair
            Button(
                onClick = { onThemeSelected(theme) },
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Text(text = label)
            }
        }
    }
}

@Composable
private fun ContrastToggleRow(
    selectedContrast: SharedPrefsManager.ContrastLevel,
    onContrastSelected: (SharedPrefsManager.ContrastLevel) -> Unit
) {
    Row {
        val contrasts = listOf(
            SharedPrefsManager.ContrastLevel.STANDARD to stringResource(id = R.string.app_contrast_standard),
            SharedPrefsManager.ContrastLevel.MEDIUM to stringResource(id = R.string.app_contrast_medium),
            SharedPrefsManager.ContrastLevel.HIGH to stringResource(id = R.string.app_contrast_high)
        )
        contrasts.forEachIndexed { index, pair ->
            val (contrast, label) = pair
            Button(
                onClick = { onContrastSelected(contrast) },
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Text(text = label)
            }
        }
    }
}

enum class PermissionCardType {
    BATTERY_OPTIMIZATION,
    BACKGROUND_OPTIMIZATION,
    NOTIFICATION,
    OVERLAY
}

private fun checkBatteryOptimization(context: Context): Boolean {
    return (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .isIgnoringBatteryOptimizations(context.packageName)
}

private fun checkBackgroundOptimization(context: Context): Boolean {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
            !(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isBackgroundRestricted
        else -> true
    }
}

private fun checkNotificationPermission(context: Context): Boolean {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        else -> true
    }
}

private fun checkOverlayPermission(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}

private fun onPermissionCardClicked(context: Context, type: PermissionCardType, app: CaffeinateApplication, prefs: SharedPrefsManager, notificationPermissionLauncher: ActivityResultLauncher<Array<String>>) {
    when (type) {
        PermissionCardType.BATTERY_OPTIMIZATION -> requestBatteryOptimizationPermission(context)
        PermissionCardType.BACKGROUND_OPTIMIZATION -> requestBatteryOptimizationPermission(context)
        PermissionCardType.NOTIFICATION -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
            }
        }
        PermissionCardType.OVERLAY -> {
            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
            context.startActivity(intent)
        }
    }
}

private fun requestBatteryOptimizationPermission(context: Context) {
    context.startActivity(
        android.content.Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:${context.packageName}")
        )
    )
}

private fun onMaterialYouToggled(prefs: SharedPrefsManager) {
    changeThemeAndContrast(prefs, prefs.theme, prefs.contrastLevel, !prefs.isMaterialYouEnabled)
}

private fun onQuickTileStatusToggled(prefs: SharedPrefsManager) {
    prefs.isShowStatusInQuickTileTitleEnabled = !prefs.isShowStatusInQuickTileTitleEnabled
}

private fun onOverlayToggled(prefs: SharedPrefsManager, context: Context) {
    prefs.isOverlayEnabled = !prefs.isOverlayEnabled
}

private fun onPictureInPictureToggled(prefs: SharedPrefsManager) {
    prefs.isPictureInPictureEnabled = !prefs.isPictureInPictureEnabled
}

private fun onDimmingToggled(prefs: SharedPrefsManager) {
    prefs.isDimmingEnabled = !prefs.isDimmingEnabled
}

private fun onWhileLockedToggled(prefs: SharedPrefsManager) {
    prefs.isWhileLockedEnabled = !prefs.isWhileLockedEnabled
}

private fun onToggleClicked(app: CaffeinateApplication, prefs: SharedPrefsManager) {
    if (!prefs.isAllPermissionsGranted) return
    KeepAwakeService.startNextTimeout(app)
}

private fun onToggleLongClicked(app: CaffeinateApplication, prefs: SharedPrefsManager) {
    if (!prefs.isAllPermissionsGranted) return
    when (app.lastStatusUpdate) {
        is ServiceStatus.Stopped -> KeepAwakeService.startIndefinitely(app)
        is ServiceStatus.Running -> KeepAwakeService.toggleState(
            app,
            com.hifnawy.caffeinate.controller.KeepAwakeService.Companion.KeepAwakeServiceState.STATE_STOP
        )
    }
}

private fun onRestartClicked(app: CaffeinateApplication, prefs: SharedPrefsManager) {
    if (!prefs.isAllPermissionsGranted) return
    KeepAwakeService.restart(app)
}

private fun changeThemeAndContrast(
    prefs: SharedPrefsManager,
    selectedTheme: SharedPrefsManager.Theme,
    selectedContrastLevel: SharedPrefsManager.ContrastLevel,
    selectedIsMaterialYouEnabled: Boolean
) {
    val isToRecreate = prefs.theme == selectedTheme
    val newContrastLevel =
        ((selectedContrastLevel.isNotStandard && !selectedIsMaterialYouEnabled) ||
         (selectedContrastLevel.isNotStandard && prefs.contrastLevel.isStandard && prefs.isMaterialYouEnabled)).let { isNotStandard ->
            when {
                isNotStandard -> selectedContrastLevel
                else          -> SharedPrefsManager.ContrastLevel.STANDARD
            }
        }
    val newIsMaterialYouEnabled =
        (selectedIsMaterialYouEnabled && selectedContrastLevel.isStandard) ||
        (selectedIsMaterialYouEnabled && !prefs.isMaterialYouEnabled && prefs.contrastLevel.isNotStandard)

    prefs.theme = selectedTheme
    prefs.contrastLevel = newContrastLevel
    prefs.isMaterialYouEnabled = newIsMaterialYouEnabled
}
