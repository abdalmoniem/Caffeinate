package com.hifnawy.caffeinate.view

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.material.color.DynamicColors
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.controller.PiPAction
import com.hifnawy.caffeinate.controller.ServiceStatus
import com.hifnawy.caffeinate.controller.SharedPrefsManager
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toLocalizedFormattedTime

/**
 * The main Compose screen.
 *
 * Mirrors the previous Views-based activity layout:
 * - top app bar (navigation icon reflects running/stopped state)
 * - permissions section (battery, background, notification, overlay)
 * - settings section (theme, contrast, Material You, quick-tile status, overlay,
 *   picture-in-picture, dimming, while-locked)
 * - timeouts section (enabled durations + choose button)
 * - primary toggle button (shows remaining time / off)
 * - restart button (visible when running)
 * - picture-in-picture content overlay
 */
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
    onToggleClicked: () -> Unit,
    onToggleLongClicked: () -> Unit,
    onRestartClicked: () -> Unit,
    onTimeoutsChosen: () -> Unit,
    onThemeSelected: (SharedPrefsManager.Theme) -> Unit,
    onContrastSelected: (SharedPrefsManager.ContrastLevel) -> Unit,
    onMaterialYouToggled: (Boolean) -> Unit,
    onQuickTileStatusToggled: (Boolean) -> Unit,
    onOverlayToggled: (Boolean) -> Unit,
    onPictureInPictureToggled: (Boolean) -> Unit,
    onDimmingToggled: (Boolean) -> Unit,
    onWhileLockedToggled: (Boolean) -> Unit,
    onPermissionCardClicked: (PermissionCardType) -> Unit,
    onAboutClicked: () -> Unit,
    onPrivacyClicked: () -> Unit,
    notificationPermissionLauncher: ActivityResultLauncher<Array<String>>
) {
    Column(modifier = modifier) {
        // Top app bar
        TopAppBar(
            title = { Text(text = stringResource(id = R.string.app_name)) },
            navigationIcon = {
                val painter = when (serviceStatus) {
                    is ServiceStatus.Stopped -> painterResource(id = R.drawable.toolbar_icon_off)
                    is ServiceStatus.Running -> painterResource(id = R.drawable.toolbar_icon_on)
                }
                IconButton(onClick = {}) {
                    Icon(painter = painter, contentDescription = null)
                }
            }
        )

        // Body
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
                onToggleClicked = onToggleClicked,
                onToggleLongClicked = onToggleLongClicked,
                onRestartClicked = onRestartClicked,
                onTimeoutsChosen = onTimeoutsChosen,
                onThemeSelected = onThemeSelected,
                onContrastSelected = onContrastSelected,
                onMaterialYouToggled = onMaterialYouToggled,
                onQuickTileStatusToggled = onQuickTileStatusToggled,
                onOverlayToggled = onOverlayToggled,
                onPictureInPictureToggled = onPictureInPictureToggled,
                onDimmingToggled = onDimmingToggled,
                onWhileLockedToggled = onWhileLockedToggled,
                onPermissionCardClicked = onPermissionCardClicked,
                onAboutClicked = onAboutClicked,
                onPrivacyClicked = onPrivacyClicked,
                notificationPermissionLauncher = notificationPermissionLauncher
            )
        }
    }
}

/** Primary content shown when not in picture-in-picture mode. */
@Composable
private fun MainContent(
    context: Context,
    app: CaffeinateApplication,
    prefs: SharedPrefsManager,
    serviceStatus: ServiceStatus,
    isAllPermissionsGranted: Boolean,
    isPictureInPictureSupported: Boolean,
    onToggleClicked: () -> Unit,
    onToggleLongClicked: () -> Unit,
    onRestartClicked: () -> Unit,
    onTimeoutsChosen: () -> Unit,
    onThemeSelected: (SharedPrefsManager.Theme) -> Unit,
    onContrastSelected: (SharedPrefsManager.ContrastLevel) -> Unit,
    onMaterialYouToggled: (Boolean) -> Unit,
    onQuickTileStatusToggled: (Boolean) -> Unit,
    onOverlayToggled: (Boolean) -> Unit,
    onPictureInPictureToggled: (Boolean) -> Unit,
    onDimmingToggled: (Boolean) -> Unit,
    onWhileLockedToggled: (Boolean) -> Unit,
    onPermissionCardClicked: (PermissionCardType) -> Unit,
    onAboutClicked: () -> Unit,
    onPrivacyClicked: () -> Unit,
    notificationPermissionLauncher: ActivityResultLauncher<Array<String>>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Permissions section
        SectionTitle(text = stringResource(id = R.string.permissions_card_title))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
            Column {
                val permissions = listOf(
                    PermissionCardType.BATTERY_OPTIMIZATION to checkBatteryOptimization(context),
                    PermissionCardType.BACKGROUND_OPTIMIZATION to checkBackgroundOptimization(),
                    PermissionCardType.NOTIFICATION to checkNotificationPermission(context),
                    PermissionCardType.OVERLAY to checkOverlayPermission(context)
                )
                permissions.forEachIndexed { index, (type, isGranted) ->
                    PermissionCardRow(
                        type = type,
                        isGranted = isGranted,
                        context = context,
                        app = app,
                        onClick = { if (!isGranted) onPermissionCardClicked(type) }
                    )
                    if (index != permissions.lastIndex) Divider()
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Settings section
        SectionTitle(text = stringResource(id = R.string.settings_card_title))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
            Column {
                SettingCardRow(
                    title = stringResource(id = R.string.app_theme_pref),
                    icon = painterResource(id = R.drawable.pref_theme_icon),
                    isEnabled = isAllPermissionsGranted,
                    trailing = ThemeToggleRow(selectedTheme = prefs.theme, onThemeSelected = onThemeSelected),
                    onClick = { Unit }
                )
                Divider()

                SettingCardRow(
                    title = stringResource(id = R.string.contrast),
                    icon = painterResource(id = R.drawable.pref_contrast_icon),
                    isEnabled = isAllPermissionsGranted,
                    trailing = ContrastToggleRow(selectedContrast = prefs.contrastLevel, onContrastSelected = onContrastSelected),
                    onClick = { Unit }
                )
                Divider()

                SettingCardRow(
                    title = stringResource(id = R.string.material_you_pref),
                    icon = painterResource(id = R.drawable.pref_material_you_icon),
                    subtitle = if (DynamicColors.isDynamicColorAvailable())
                        stringResource(id = R.string.material_you_pref_description)
                    else stringResource(id = R.string.material_you_pref_not_available),
                    isEnabled = isAllPermissionsGranted && DynamicColors.isDynamicColorAvailable(),
                    trailing = Switch(
                        checked = prefs.isMaterialYouEnabled && prefs.contrastLevel == SharedPrefsManager.ContrastLevel.STANDARD,
                        onCheckedChange = onMaterialYouToggled,
                        enabled = isAllPermissionsGranted && DynamicColors.isDynamicColorAvailable()
                    ),
                    onClick = { onMaterialYouToggled(!prefs.isMaterialYouEnabled) }
                )
                Divider()

                SettingCardRow(
                    title = stringResource(id = R.string.quick_tile_title_pref),
                    icon = painterResource(id = R.drawable.prefs_quick_tile_icon),
                    subtitle = stringResource(id = R.string.quick_tile_title_pref_description),
                    isEnabled = isAllPermissionsGranted,
                    trailing = Switch(
                        checked = prefs.isShowStatusInQuickTileTitleEnabled,
                        onCheckedChange = onQuickTileStatusToggled,
                        enabled = isAllPermissionsGranted
                    ),
                    onClick = { onQuickTileStatusToggled(!prefs.isShowStatusInQuickTileTitleEnabled) }
                )
                Divider()

                SettingCardRow(
                    title = stringResource(id = R.string.show_overlay_pref),
                    icon = painterResource(id = R.drawable.pref_overlay_icon),
                    subtitle = stringResource(id = R.string.show_overlay_pref_description),
                    isEnabled = isAllPermissionsGranted && checkOverlayPermission(context),
                    trailing = Switch(
                        checked = prefs.isOverlayEnabled,
                        onCheckedChange = onOverlayToggled,
                        enabled = isAllPermissionsGranted && checkOverlayPermission(context)
                    ),
                    onClick = { onOverlayToggled(!prefs.isOverlayEnabled) }
                )
                Divider()

                SettingCardRow(
                    title = stringResource(id = R.string.picture_in_picture_pref),
                    icon = painterResource(id = R.drawable.pref_picture_in_picture_icon),
                    subtitle = stringResource(id = R.string.picture_in_picture_pref_description),
                    isEnabled = isAllPermissionsGranted,
                    trailing = Switch(
                        checked = prefs.isPictureInPictureEnabled,
                        onCheckedChange = onPictureInPictureToggled,
                        enabled = isAllPermissionsGranted
                    ),
                    onClick = { onPictureInPictureToggled(!prefs.isPictureInPictureEnabled) }
                )
                Divider()

                SettingCardRow(
                    title = stringResource(id = R.string.allow_dimming_pref),
                    icon = painterResource(id = R.drawable.pref_dim_icon),
                    subtitle = stringResource(id = R.string.allow_dimming_pref_description),
                    isEnabled = isAllPermissionsGranted,
                    trailing = Switch(
                        checked = prefs.isDimmingEnabled,
                        onCheckedChange = onDimmingToggled,
                        enabled = isAllPermissionsGranted
                    ),
                    onClick = { onDimmingToggled(!prefs.isDimmingEnabled) }
                )
                Divider()

                SettingCardRow(
                    title = stringResource(id = R.string.allow_while_locked_pref),
                    icon = painterResource(id = R.drawable.pref_lock_icon),
                    subtitle = stringResource(id = R.string.allow_while_locked_pref_description),
                    isEnabled = isAllPermissionsGranted,
                    trailing = Switch(
                        checked = prefs.isWhileLockedEnabled,
                        onCheckedChange = onWhileLockedToggled,
                        enabled = isAllPermissionsGranted
                    ),
                    onClick = { onWhileLockedToggled(!prefs.isWhileLockedEnabled) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Timeouts section
        SectionTitle(text = stringResource(id = R.string.timeouts_pref))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = prefs.timeoutCheckBoxes.enabledDurations,
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

        // Toggle button
        val remainingText = when (serviceStatus) {
            is ServiceStatus.Stopped -> context.getString(R.string.caffeinate_button_off)
            is ServiceStatus.Running -> serviceStatus.remaining.toLocalizedFormattedTime(context)
        }

        Button(
            onClick = onToggleClicked,
            onLongClick = onToggleLongClicked,
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

        // Restart button
        val isRestartButtonEnabled = (serviceStatus as? ServiceStatus.Running)?.run {
            (!isRestarted && isCountingDown) || isRestarted
        } ?: false

        if (isRestartButtonEnabled) {
            Button(
                onClick = onRestartClicked,
                enabled = isAllPermissionsGranted,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Row(horizontalArrangement = Arrangement.Center) {
                    Icon(imageVector = Icons.Default.Restart, contentDescription = null)
                    Text(text = stringResource(id = R.string.action_restart_timeout))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** Picture-in-picture content. */
@Composable
private fun PictureInPictureContent(serviceStatus: ServiceStatus, context: Context) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            progress = when {
                serviceStatus is ServiceStatus.Stopped -> 0f
                serviceStatus is ServiceStatus.Running && serviceStatus.remaining.isInfinite() -> 1f
                else -> {
                    val start = (serviceStatus as ServiceStatus.Running).startTimeout
                    if (start > 0) 1f - (serviceStatus.remaining / start).coerceIn(0f, 1f) else 0f
                }
            },
            modifier = Modifier.size(200.dp)
        )
        Text(
            text = when {
                serviceStatus is ServiceStatus.Stopped -> context.getString(R.string.caffeinate_button_off)
                else -> serviceStatus.remaining.toLocalizedFormattedTime(context)
            },
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 24.dp)
        )
    }
}

/** Section title. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 15.dp, start = 10.dp)
    )
}

/** Permission card row. */
@Composable
private fun PermissionCardRow(
    type: PermissionCardType,
    isGranted: Boolean,
    context: Context,
    app: CaffeinateApplication,
    onClick: () -> Unit
) {
    val (iconRes, title, grantedText, notGrantedText) = when (type) {
        PermissionCardType.BATTERY_OPTIMIZATION -> R.drawable.permission_battery_optimization_icon to
            stringResource(id = R.string.battery_optimization_granted) to
            stringResource(id = R.string.battery_optimization_granted) to
            stringResource(id = R.string.battery_optimization_not_granted)
        PermissionCardType.BACKGROUND_OPTIMIZATION -> R.drawable.permission_background_optimization_icon to
            stringResource(id = R.string.background_optimization_granted) to
            stringResource(id = R.string.background_optimization_granted) to
            stringResource(id = R.string.background_optimization_not_granted)
        PermissionCardType.NOTIFICATION -> R.drawable.permission_notifications_icon to
            stringResource(id = R.string.notifications_permission_granted) to
            stringResource(id = R.string.notifications_permission_granted) to
            stringResource(id = R.string.notifications_permission_not_granted)
        PermissionCardType.OVERLAY -> R.drawable.permission_draw_on_top_icon to
            stringResource(id = R.string.overlay_permission_granted) to
            stringResource(id = R.string.overlay_permission_granted) to
            stringResource(id = R.string.overlay_permission_not_granted)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable(onClick = onClick)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp).tint(if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = if (isGranted) grantedText else notGrantedText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Off,
            contentDescription = null,
            tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}

/** Setting card row with trailing content. */
@Composable
private fun SettingCardRow(
    title: String,
    icon: androidx.compose.ui.graphics.painter.Painter,
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
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp).tint(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.alpha(if (isEnabled) 1f else 0.5f)
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alpha(if (isEnabled && subtitle.isNotEmpty()) 1f else 0.5f)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        trailing()
    }
}

/** Theme toggle row (system/light/dark buttons). */
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
        themes.forEachIndexed { index, (theme, label) ->
            Button(
                onClick = { onThemeSelected(theme) },
                selected = selectedTheme == theme,
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Text(text = label)
            }
        }
    }
}

/** Contrast toggle row (standard/medium/high buttons). */
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
        contrasts.forEachIndexed { index, (contrast, label) ->
            Button(
                onClick = { onContrastSelected(contrast) },
                selected = selectedContrast == contrast,
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Text(text = label)
            }
        }
    }
}

/** Types of permission cards displayed on the main screen. */
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

private fun checkBackgroundOptimization(): Boolean {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
            !(LocalContext.current.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isBackgroundRestricted
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
