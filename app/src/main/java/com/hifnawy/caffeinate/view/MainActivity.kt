package com.hifnawy.caffeinate.view

import android.Manifest
import android.app.ActivityManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Rational
import android.view.HapticFeedbackConstants
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.controller.KeepAwakeService
import com.hifnawy.caffeinate.controller.PiPAction
import com.hifnawy.caffeinate.controller.PictureInPictureActionsReceiver
import com.hifnawy.caffeinate.controller.ServiceStatus
import com.hifnawy.caffeinate.controller.ServiceStatusObserver
import com.hifnawy.caffeinate.controller.SharedPrefsManager
import com.hifnawy.caffeinate.controller.SharedPrefsObserver
import com.hifnawy.caffeinate.ui.theme.CaffeinateTheme
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toLocalizedFormattedTime
import com.hifnawy.caffeinate.utils.MutableListExtensionFunctions.addObserver
import com.hifnawy.caffeinate.utils.MutableListExtensionFunctions.removeObserver
import com.hifnawy.caffeinate.viewModel.MainActivityViewModel
import timber.log.Timber as Log

/**
 * The main activity of the Caffeinate app, rewritten with Jetpack Compose.
 *
 * Core behaviour is unchanged from the previous Views-based implementation:
 * permission cards, theme / contrast / Material You / quick-tile / overlay /
 * picture-in-picture / dimming / while-locked preference cards, the timeout
 * chooser, the toggle / restart buttons, and picture-in-picture support are all
 * preserved. Only the UI toolkit changed (Views → Compose).
 */
class MainActivity : FragmentActivity(), ServiceStatusObserver, SharedPrefsObserver {

    private val viewModel: MainActivityViewModel by viewModels()
    private val caffeinateApplication by lazy { application as CaffeinateApplication }
    private val sharedPreferences by lazy { SharedPrefsManager(caffeinateApplication) }

    private val isPictureInPictureSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private val pipActionReceiver by lazy { PictureInPictureActionsReceiver(caffeinateApplication) }
    private var isInPictureInPictureActive = false

    /** Notification permission launcher (replaces assent's askForPermissions). */
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<Array<String>>

    /** Observable UI state. Updated by the observer callbacks to trigger recomposition. */
    private var serviceStatus: ServiceStatus by mutableStateOf(ServiceStatus.Stopped)
    private var isAllPermissionsGranted by mutableStateOf(false)

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = when {
            isPictureInPictureSupported && !isInPictureInPictureMode -> {
                onUserLeaveHint()
                isEnabled = !isInPictureInPictureActive
                onBackPressedDispatcher.onBackPressed()
            }
            else -> {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            val isGranted = granted.values.all { it }
            sharedPreferences.isAllPermissionsGranted = isGranted
        }

        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(onBackPressedCallback)

        setContent {
            val context = LocalContext.current
            val useDarkTheme = when (sharedPreferences.theme) {
                SharedPrefsManager.Theme.DARK -> true
                SharedPrefsManager.Theme.LIGHT -> false
                else -> isSystemInDarkTheme()
            }
            val dynamicColor = sharedPreferences.isMaterialYouEnabled &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            CaffeinateTheme(
                darkTheme = useDarkTheme,
                dynamicColor = dynamicColor,
                contrastLevel = sharedPreferences.contrastLevel
            ) {
                MainScreen(
                    modifier = Modifier.fillMaxSize(),
                    context = this@MainActivity,
                    app = caffeinateApplication,
                    prefs = sharedPreferences,
                    serviceStatus = serviceStatus,
                    isAllPermissionsGranted = isAllPermissionsGranted,
                    isPictureInPictureSupported = isPictureInPictureSupported,
                    isInPictureInPictureMode = isInPictureInPictureMode,
                    onToggleClicked = { onToggleClicked() },
                    onToggleLongClicked = { onToggleLongClicked() },
                    onRestartClicked = { onRestartClicked() },
                    onTimeoutsChosen = { onTimeoutsChosen() },
                    onThemeSelected = { theme ->
                        changeThemeAndContrast(theme, sharedPreferences.contrastLevel, sharedPreferences.isMaterialYouEnabled)
                    },
                    onContrastSelected = { contrast ->
                        changeThemeAndContrast(sharedPreferences.theme, contrast, sharedPreferences.isMaterialYouEnabled)
                    },
                    onMaterialYouToggled = { checked ->
                        changeThemeAndContrast(sharedPreferences.theme, sharedPreferences.contrastLevel, checked)
                    },
                    onQuickTileStatusToggled = { checked ->
                        sharedPreferences.isShowStatusInQuickTileTitleEnabled = checked
                    },
                    onOverlayToggled = { checked ->
                        sharedPreferences.isOverlayEnabled = checked
                    },
                    onPictureInPictureToggled = { checked ->
                        sharedPreferences.isPictureInPictureEnabled = checked
                    },
                    onDimmingToggled = { checked ->
                        sharedPreferences.isDimmingEnabled = checked
                    },
                    onWhileLockedToggled = { checked ->
                        sharedPreferences.isWhileLockedEnabled = checked
                    },
                    onPermissionCardClicked = { type -> onPermissionCardClicked(type) },
                    onAboutClicked = { onAboutClicked() },
                    onPrivacyClicked = { onPrivacyClicked() },
                    notificationPermissionLauncher = notificationPermissionLauncher
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()

        caffeinateApplication.run {
            applyLocaleConfiguration()
            checkAllPermissions { isGranted -> onIsAllPermissionsGrantedUpdated(isGranted) }
            keepAwakeServiceObservers.addObserver(this@MainActivity)
            sharedPrefsObservers.addObserver(this@MainActivity)
            onServiceStatusUpdated(lastStatusUpdate)
        }
    }

    override fun onPause() {
        super.onPause()
        if (!sharedPreferences.isPictureInPictureEnabled) {
            caffeinateApplication.run {
                keepAwakeServiceObservers.removeObserver(this@MainActivity)
                sharedPrefsObservers.removeObserver(this@MainActivity)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!sharedPreferences.isPictureInPictureEnabled || caffeinateApplication.lastStatusUpdate !is ServiceStatus.Running) return

        enterPictureInPictureMode(buildPictureInPictureParams())
        isInPictureInPictureActive = true
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        this.isInPictureInPictureActive = isInPictureInPictureMode
        pipActionReceiver.isRegistered = isInPictureInPictureMode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pipActionReceiver.onActionClickListener = PictureInPictureActionsReceiver.OnActionClickListener { action ->
                Log.d("PiP Action Clicked, Action: $action")
                when (action) {
                    PiPAction.TOGGLE -> when (caffeinateApplication.lastStatusUpdate) {
                        is ServiceStatus.Stopped -> KeepAwakeService.startNextTimeout(caffeinateApplication)
                        is ServiceStatus.Running -> KeepAwakeService.toggleState(
                            caffeinateApplication,
                            com.hifnawy.caffeinate.controller.KeepAwakeService.Companion.KeepAwakeServiceState.STATE_STOP
                        )
                    }
                    PiPAction.NEXT_TIMEOUT -> KeepAwakeService.startNextTimeout(caffeinateApplication, wrapAround = true)
                    PiPAction.RESTART -> KeepAwakeService.restart(caffeinateApplication)
                }
            }
            setPictureInPictureParams(buildPictureInPictureParams())
        }
    }

    override fun onPictureInPictureUiStateChanged(state: android.app.PictureInPictureUiState) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        if (!state.isTransitioningToPip) return
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            R.id.about -> onAboutClicked()
            R.id.privacy_policy -> onPrivacyClicked()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun onToggleClicked() {
        if (!sharedPreferences.isAllPermissionsGranted) return
        KeepAwakeService.startNextTimeout(caffeinateApplication)
    }

    fun onToggleLongClicked() {
        if (!sharedPreferences.isAllPermissionsGranted) return
        when (caffeinateApplication.lastStatusUpdate) {
            is ServiceStatus.Stopped -> KeepAwakeService.startIndefinitely(caffeinateApplication)
            is ServiceStatus.Running -> KeepAwakeService.toggleState(
                caffeinateApplication,
                com.hifnawy.caffeinate.controller.KeepAwakeService.Companion.KeepAwakeServiceState.STATE_STOP
            )
        }
    }

    private fun onRestartClicked() {
        if (!sharedPreferences.isAllPermissionsGranted) return
        KeepAwakeService.restart(caffeinateApplication)
    }

    private fun onTimeoutsChosen() {
        if (!sharedPreferences.isAllPermissionsGranted) return
        TimeoutsSelectionFragment.newInstance.apply {
            selectionCallback = { checkBoxItems ->
                caffeinateApplication.run {
                    timeoutCheckBoxes.clear()
                    timeoutCheckBoxes.addAll(checkBoxItems)
                    sharedPreferences.timeoutCheckBoxes.clear()
                    sharedPreferences.timeoutCheckBoxes.addAll(checkBoxItems)
                }
            }
        }.show(supportFragmentManager, TimeoutsSelectionFragment::class.simpleName)
    }

    private fun onPermissionCardClicked(type: PermissionCardType) {
        when (type) {
            PermissionCardType.BATTERY_OPTIMIZATION -> requestBatteryOptimizationPermission()
            PermissionCardType.BACKGROUND_OPTIMIZATION -> requestBatteryOptimizationPermission()
            PermissionCardType.NOTIFICATION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                }
            }
            PermissionCardType.OVERLAY -> {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${packageName}"))
                startActivity(intent)
            }
        }
    }

    private fun requestBatteryOptimizationPermission() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.battery_optimization_needed_title))
            .setIcon(R.drawable.coffee_icon)
            .setCancelable(false)
            .setMessage(getString(R.string.battery_optimization_needed_message))
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${caffeinateApplication.localizedApplicationContext.packageName}")
                    )
                )
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun onAboutClicked() {
        AboutFragment.newInstance.show(supportFragmentManager, AboutFragment::class.simpleName)
    }

    private fun onPrivacyClicked() {
        PrivacyPolicyFragment.newInstance.show(supportFragmentManager, PrivacyPolicyFragment::class.simpleName)
    }

    private fun changeThemeAndContrast(
        selectedTheme: SharedPrefsManager.Theme,
        selectedContrastLevel: SharedPrefsManager.ContrastLevel,
        selectedIsMaterialYouEnabled: Boolean
    ) = sharedPreferences.run {
        val isToRecreate = sharedPreferences.theme == selectedTheme
        val newContrastLevel =
            ((selectedContrastLevel.isNotStandard && !selectedIsMaterialYouEnabled) ||
             (selectedContrastLevel.isNotStandard && sharedPreferences.contrastLevel.isStandard && sharedPreferences.isMaterialYouEnabled)).let { isNotStandard ->
                when {
                    isNotStandard -> selectedContrastLevel
                    else          -> SharedPrefsManager.ContrastLevel.STANDARD
                }
            }
        val newIsMaterialYouEnabled =
            (selectedIsMaterialYouEnabled && selectedContrastLevel.isStandard) ||
            (selectedIsMaterialYouEnabled && !sharedPreferences.isMaterialYouEnabled && sharedPreferences.contrastLevel.isNotStandard)

        sharedPreferences.theme = selectedTheme
        sharedPreferences.contrastLevel = newContrastLevel
        sharedPreferences.isMaterialYouEnabled = newIsMaterialYouEnabled

        when {
            isToRecreate -> recreate()
            else         -> {
                // Compose theme is reactive; no night-mode delegate needed
            }
        }
    }

    private fun checkAllPermissions(callback: (Boolean) -> Unit) = listOf(
        checkBatteryOptimization(),
        checkBackgroundOptimization(),
        checkNotificationPermission()
    ).all { it }.let { isGranted ->
        callback(isGranted)
        sharedPreferences.isAllPermissionsGranted = isGranted
    }

    private fun checkBatteryOptimization(): Boolean {
        return (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
    }

    private fun checkBackgroundOptimization(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
                !(getSystemService(ACTIVITY_SERVICE) as ActivityManager).isBackgroundRestricted
            else -> true
        }
    }

    private fun checkNotificationPermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            else -> true
        }
    }

    private fun checkOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    private fun buildPictureInPictureParams(autoEnter: Boolean = false): PictureInPictureParams {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            PictureInPictureParams.Builder().build()
        } else {
            PictureInPictureParams.Builder().apply {
                setSourceRectHint(Rect())
                setAspectRatio(Rational(1, 1))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setSeamlessResizeEnabled(false)
                    setAutoEnterEnabled(autoEnter)
                }
            }.build()
        }
    }

    // -- ServiceStatusObserver --
    override fun onServiceStatusUpdated(status: ServiceStatus) {
        serviceStatus = status
        onBackPressedCallback.isEnabled = status is ServiceStatus.Running
        val autoEnterPiP = status is ServiceStatus.Running && sharedPreferences.isPictureInPictureEnabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setPictureInPictureParams(buildPictureInPictureParams(autoEnterPiP))
        }
    }

    // -- SharedPrefsObserver --
    override fun onIsAllPermissionsGrantedUpdated(isGranted: Boolean) {
        isAllPermissionsGranted = isGranted
    }

    override fun onIsOverlayEnabledUpdated(enabled: Boolean) = Unit
    override fun onIsDimmingEnabledUpdated(enabled: Boolean) = Unit
    override fun onIsWhileLockedEnabledUpdated(enabled: Boolean) = Unit
    override fun onIsPictureInPictureEnabledUpdated(enabled: Boolean) = Unit
    override fun onIsShowQuickTileStatusInTitleEnabledUpdated(enabled: Boolean) = Unit
}
