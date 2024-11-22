package com.hifnawy.caffeinate.ui

import android.Manifest
import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.animation.addListener
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.stephenvinouze.materialnumberpickercore.MaterialNumberPicker
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.databinding.ActivityMainBinding
import com.hifnawy.caffeinate.databinding.DialogChooseTimeoutsBinding
import com.hifnawy.caffeinate.databinding.DialogSetCustomTimeoutBinding
import com.hifnawy.caffeinate.services.KeepAwakeService
import com.hifnawy.caffeinate.services.KeepAwakeService.Companion.KeepAwakeServiceState
import com.hifnawy.caffeinate.services.ServiceStatus
import com.hifnawy.caffeinate.services.ServiceStatusObserver
import com.hifnawy.caffeinate.utils.ActivityExtensionFunctions.setActivityTheme
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toLocalizedFormattedTime
import com.hifnawy.caffeinate.utils.MutableListExtensionFunctions.addObserver
import com.hifnawy.caffeinate.utils.MutableListExtensionFunctions.removeObserver
import com.hifnawy.caffeinate.utils.SharedPrefsManager
import com.hifnawy.caffeinate.utils.SharedPrefsObserver
import kotlin.math.abs
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import timber.log.Timber as Log

/**
 * The main activity of the application, which is responsible for displaying the list of timeouts that can be used to keep the screen on. It also
 * handles the logic of starting and stopping the [KeepAwakeService].
 *
 * @author AbdAlMoniem AlHifnawy
 *
 * @see KeepAwakeService
 * @see SharedPrefsManager
 */
class MainActivity : AppCompatActivity(), SharedPrefsObserver, ServiceStatusObserver {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val caffeinateApplication by lazy { application as CaffeinateApplication }
    private var appBarVerticalOffset = 0
    private val sharedPreferences by lazy { SharedPrefsManager(caffeinateApplication) }
    private val grantedDrawable by lazy { AppCompatResources.getDrawable(binding.root.context, R.drawable.ok_icon_circle) }
    private val notGrantedDrawable by lazy { AppCompatResources.getDrawable(binding.root.context, R.drawable.nok_icon_circle) }
    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>
    private val Iterable<CheckBoxItem>.enabledDurations: CharSequence
        get() =
            filter { checkBoxItem -> checkBoxItem.isChecked }.joinToString(
                    separator = ", ",
                    limit = 10,
                    truncated = "..."
            ) { checkBoxItem -> checkBoxItem.duration.toLocalizedFormattedTime(binding.root.context) }

    /**
     * Called to retrieve per-instance state from an activity before being
     * killed so that the state can be restored in [onRestoreInstanceState]
     * or [onCreate] (the [Parcelable][android.os.Parcelable] returned here
     * will be available in the Bundle returned to you in the aforementioned methods).
     *
     * @param outState [Bundle] a bundle to save the per-instance state of the activity
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putInt(::appBarVerticalOffset.name, appBarVerticalOffset)
    }

    /**
     * Restores the state of the activity from a [Bundle] containing the information
     * previously saved by [onSaveInstanceState].
     *
     * @param savedInstanceState [Bundle] a bundle to restore the per-instance state
     * of the activity
     */
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        appBarVerticalOffset = savedInstanceState.getInt(::appBarVerticalOffset.name)
        binding.appBar.setExpanded(appBarVerticalOffset == 0)
    }

    /**
     * Called when the activity is starting.
     *
     * @param savedInstanceState [Bundle] If the activity is being re-initialized after
     *     previously being shut down then this Bundle contains the data it most
     *     recently supplied in [onSaveInstanceState].
     *     Note: Otherwise it is null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences.run { setActivityTheme(theme.mode, isMaterialYouEnabled) }

        enableEdgeToEdge()
        setContentView(binding.root)

        with(binding) {
            appBar.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
                appBarVerticalOffset = verticalOffset
                val totalScrollRange = appBarLayout.totalScrollRange
                val collapseFactor = (1f - abs(verticalOffset / totalScrollRange.toFloat())).coerceAtLeast(0.5f)

                toolbar.navigationIcon = when (caffeinateApplication.lastStatusUpdate) {
                    is ServiceStatus.Stopped -> AppCompatResources.getDrawable(root.context, R.drawable.toolbar_icon_off)
                    is ServiceStatus.Running -> AppCompatResources.getDrawable(root.context, R.drawable.toolbar_icon_on)
                }?.run {
                    val bitmap = toBitmap((intrinsicWidth * collapseFactor).toInt(), (intrinsicHeight * collapseFactor).toInt())
                    bitmap.toDrawable(resources)
                }
            }

            appThemeSelectionView.run {
                appThemeToggleGroup.run {
                    when (sharedPreferences.theme.mode) {
                        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> check(R.id.appThemeSystemDefaultButton)
                        AppCompatDelegate.MODE_NIGHT_NO            -> check(R.id.appThemeSystemLightButton)
                        AppCompatDelegate.MODE_NIGHT_YES           -> check(R.id.appThemeSystemDarkButton)
                    }

                    addOnButtonCheckedListener { group, checkedId, isChecked ->
                        if (isChecked) {
                            group.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            val newTheme = when (checkedId) {
                                R.id.appThemeSystemDefaultButton -> SharedPrefsManager.Theme.SYSTEM_DEFAULT
                                R.id.appThemeSystemLightButton   -> SharedPrefsManager.Theme.LIGHT
                                R.id.appThemeSystemDarkButton    -> SharedPrefsManager.Theme.DARK
                                else                             -> sharedPreferences.theme // Fallback to the current theme
                            }

                            if (newTheme != sharedPreferences.theme) {
                                sharedPreferences.theme = newTheme
                                AppCompatDelegate.setDefaultNightMode(newTheme.mode)

                                recreate()
                            }
                        }
                    }
                }
            }

            registerForOverlayPermission()
            if (DynamicColors.isDynamicColorAvailable()) enableMaterialYouPreferences()

            caffeineButton.setOnClickListener { buttonView ->
                if (!sharedPreferences.isAllPermissionsGranted) return@setOnClickListener

                buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                KeepAwakeService.startNextTimeout(caffeinateApplication)
            }

            caffeineButton.setOnLongClickListener {
                if (!sharedPreferences.isAllPermissionsGranted) return@setOnLongClickListener false

                when (caffeinateApplication.lastStatusUpdate) {
                    is ServiceStatus.Stopped -> KeepAwakeService.startIndefinitely(caffeinateApplication)
                    is ServiceStatus.Running -> KeepAwakeService.toggleState(caffeinateApplication, KeepAwakeServiceState.STATE_STOP)
                }

                return@setOnLongClickListener true
            }
        }
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
    override fun onResume() {
        super.onResume()

        caffeinateApplication.run {
            applyLocaleConfiguration()

            checkOverlayPermission()
            if (isAllPermissionsGranted()) onIsAllPermissionsGrantedUpdated(true)

            keepAwakeServiceObservers.addObserver(this@MainActivity)
            sharedPrefsObservers.addObserver(this@MainActivity)

            onServiceStatusUpdated(lastStatusUpdate)
        }
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
    override fun onPause() {
        super.onPause()

        caffeinateApplication.run {
            keepAwakeServiceObservers.removeObserver(this@MainActivity)
            sharedPrefsObservers.removeObserver(this@MainActivity)
        }
    }

    /**
     * Callback for the result from requesting permissions.
     *
     * This method is invoked for every call on [requestPermissions(String[], int)][requestPermissions].
     *
     * @param requestCode [Int] The request code passed in [requestPermissions(String[], int)][requestPermissions].
     * @param permissions [Array] The requested permissions. Never null.
     * @param grantResults [IntArray] The grant results for the corresponding permissions which is either
     * [PERMISSION_GRANTED][PackageManager.PERMISSION_GRANTED] or [PERMISSION_DENIED][PackageManager.PERMISSION_DENIED]. Never null.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 93 && grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            val snackbar = Snackbar.make(binding.root, getString(R.string.notifications_permission_required), Snackbar.LENGTH_INDEFINITE)

            snackbar.setAction(getString(R.string.go_to_settings)) {
                try {
                    // Open the specific App Info page:
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:$packageName") })
                } catch (e: ActivityNotFoundException) {
                    // Open the generic Apps page:
                    val intent = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                    startActivity(intent)
                }
            }

            snackbar.show()
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    /**
     * Called when there is a change in the permission state indicating whether all necessary permissions have been granted.
     *
     * @param isAllPermissionsGranted [Boolean] `true` if all necessary permissions have been granted, `false` otherwise.
     */
    override fun onIsAllPermissionsGrantedUpdated(isAllPermissionsGranted: Boolean) {
        binding.caffeineButton.isEnabled = isAllPermissionsGranted

        changeAllowDimmingPreferences(isAllPermissionsGranted)
        changeAllowWhileLockedPreferences(isAllPermissionsGranted)
        binding.caffeineButton.isEnabled = isAllPermissionsGranted

        changeAllowDimmingPreferences(isAllPermissionsGranted)
        changeAllowWhileLockedPreferences(isAllPermissionsGranted)
        changeTimeoutsPreferences(isAllPermissionsGranted)
    }

    /**
     * Called when the "Overlay Enabled" preference changes.
     *
     * @param isOverlayEnabled [Boolean] `true` if the overlay is enabled, `false` otherwise.
     */
    override fun onIsOverlayEnabledUpdated(isOverlayEnabled: Boolean) {
        binding.overlaySwitch.isChecked = isOverlayEnabled
    }

    /**
     * Called when the user has changed the preference of whether the screen should be dimmed while it is being kept awake.
     *
     * @param isDimmingEnabled [Boolean] `true` if the screen should be dimmed while it is being kept awake, `false` otherwise.
     */
    override fun onIsDimmingEnabledUpdated(isDimmingEnabled: Boolean) {
        binding.allowDimmingSwitch.isChecked = isDimmingEnabled
    }

    /**
     * Called when the user has changed the preference of whether the keep awake screen should be enabled while the screen is locked.
     *
     * @param isWhileLockedEnabled [Boolean] `true` if the keep awake screen should be enabled while the screen is locked, `false` otherwise.
     */
    override fun onIsWhileLockedEnabledUpdated(isWhileLockedEnabled: Boolean) {
        binding.allowWhileLockedSwitch.isChecked = isWhileLockedEnabled
    }

    /**
     * Updates the UI to match the given service status.
     *
     * @param status [ServiceStatus] the service status to update the UI for
     */
    override fun onServiceStatusUpdated(status: ServiceStatus) {
        with(binding) {
            Log.d("Status Changed: $status")

            when (status) {
                is ServiceStatus.Stopped -> {
                    restartButton.animateVisibility = false

                    caffeineButton.text = getString(R.string.caffeinate_button_off)
                }

                is ServiceStatus.Running -> {
                    when {
                        status.isRestarted -> Unit
                        status.isCountingDown -> restartButton.animateVisibility = true
                        else -> restartButton.animateVisibility = false
                    }

                    caffeineButton.text = status.remaining.toLocalizedFormattedTime(root.context)
                }
            }
        }
    }

    /**
     * Enables the Material You preferences.
     *
     * The Material You preferences are:
     * 1. A [MaterialCardView][com.google.android.material.card.MaterialCardView] that shows the Material You preferences.
     * 2. A [TextView][android.widget.TextView] that shows the Material You preferences title.
     * 3. A [TextView][android.widget.TextView] that shows the Material You preferences subtitle.
     * 4. A [MaterialSwitch][com.google.android.material.materialswitch.MaterialSwitch] that toggles the Material You preferences.
     *
     * The Material You preferences are enabled when the user has granted the [POST_NOTIFICATIONS][Manifest.permission.POST_NOTIFICATIONS] permission.
     */
    private fun enableMaterialYouPreferences() {
        with(binding) {
            materialYouCard.isEnabled = true
            materialYouTextView.isEnabled = true
            materialYouSubTextTextView.visibility = View.GONE
            materialYouSwitch.isEnabled = true
            materialYouSwitch.isChecked = sharedPreferences.isMaterialYouEnabled

            materialYouCard.setOnClickListener { materialYouSwitch.isChecked = !sharedPreferences.isMaterialYouEnabled }
            materialYouSwitch.setOnCheckedChangeListener { switch, isChecked ->
                switch.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                sharedPreferences.isMaterialYouEnabled = isChecked
                recreate()
            }
        }
    }

    private fun changeShowOverlayPreferences(isEnabled: Boolean) {
        with(binding) {
            overlayCard.isEnabled = isEnabled
            overlayTextView.isEnabled = isEnabled
            overlaySubTextTextView.isEnabled = isEnabled
            overlaySubTextTextView.visibility = if (isEnabled) View.VISIBLE else View.GONE
            overlaySwitch.isEnabled = isEnabled
            overlaySwitch.isChecked = sharedPreferences.isOverlayEnabled

            overlayCard.setOnClickListener { overlaySwitch.isChecked = !sharedPreferences.isOverlayEnabled }
            overlaySwitch.setOnCheckedChangeListener { switch, isChecked ->
                switch.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                sharedPreferences.isOverlayEnabled = isChecked
            }
        }
    }

    /**
     * Enable or disable the [allowWhileLockedCard][com.google.android.material.card.MaterialCardView],
     * [allowWhileLockedTextView][android.widget.TextView], [allowWhileLockedSubTextTextView][android.widget.TextView],
     * and [allowWhileLockedSwitch][com.google.android.material.materialswitch.MaterialSwitch] based on whether the user has granted all necessary
     * permissions.
     *
     * @param isAllPermissionsGranted [Boolean] `true` if all necessary permissions have been granted, `false` otherwise.
     */
    private fun changeAllowWhileLockedPreferences(isAllPermissionsGranted: Boolean) {
        with(binding) {
            allowWhileLockedCard.isEnabled = isAllPermissionsGranted
            allowWhileLockedTextView.isEnabled = isAllPermissionsGranted
            allowWhileLockedSubTextTextView.visibility = if (isAllPermissionsGranted) View.VISIBLE else View.GONE
            allowWhileLockedSwitch.isEnabled = isAllPermissionsGranted
            allowWhileLockedSwitch.isChecked = sharedPreferences.isWhileLockedEnabled

            allowWhileLockedCard.setOnClickListener { allowWhileLockedSwitch.isChecked = !sharedPreferences.isWhileLockedEnabled }
            allowWhileLockedSwitch.setOnCheckedChangeListener { switch, isChecked ->
                switch.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                sharedPreferences.isWhileLockedEnabled = isChecked
            }
        }
    }

    /**
     * Enable or disable the [allowDimmingCard][com.google.android.material.card.MaterialCardView], [allowDimmingTextView][android.widget.TextView],
     * [allowDimmingSubTextTextView][android.widget.TextView], and [allowDimmingSwitch][com.google.android.material.materialswitch.MaterialSwitch]
     * based on whether the user has granted all necessary permissions.
     *
     * @param isAllPermissionsGranted [Boolean] `true` if all necessary permissions have been granted, `false` otherwise.
     */
    private fun changeAllowDimmingPreferences(isAllPermissionsGranted: Boolean) {
        with(binding) {
            allowDimmingCard.isEnabled = isAllPermissionsGranted
            allowDimmingTextView.isEnabled = isAllPermissionsGranted
            allowDimmingSubTextTextView.visibility = if (isAllPermissionsGranted) View.VISIBLE else View.GONE
            allowDimmingSwitch.isEnabled = isAllPermissionsGranted
            allowDimmingSwitch.isChecked = sharedPreferences.isDimmingEnabled

            allowDimmingCard.setOnClickListener { allowDimmingSwitch.isChecked = !sharedPreferences.isDimmingEnabled }
            allowDimmingSwitch.setOnCheckedChangeListener { switch, isChecked ->
                switch.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                sharedPreferences.isDimmingEnabled = isChecked
            }
        }
    }

    /**
     * Enable or disable the [timeoutChoiceCard][com.google.android.material.card.MaterialCardView], [timeoutChoiceTextView][android.widget.TextView],
     * [timeoutChoiceSubTextTextView][android.widget.TextView], and [timeoutChoiceButton][com.google.android.material.materialswitch.MaterialSwitch]
     * based on whether the user has granted all necessary permissions.
     *
     * @param isAllPermissionsGranted [Boolean] `true` if all necessary permissions have been granted, `false` otherwise.
     */
    private fun changeTimeoutsPreferences(isAllPermissionsGranted: Boolean) {
        with(binding) {
            val timeoutChoiceClickListener = View.OnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                showChooseTimeoutDialog()
            }

            timeoutChoiceCard.isEnabled = isAllPermissionsGranted
            timeoutChoiceTextView.isEnabled = isAllPermissionsGranted
            timeoutChoiceSubTextTextView.isEnabled = true
            timeoutChoiceSubTextTextView.visibility = if (isAllPermissionsGranted) View.VISIBLE else View.GONE
            timeoutChoiceSubTextTextView.text = sharedPreferences.timeoutCheckBoxes.enabledDurations
            timeoutChoiceButton.isEnabled = isAllPermissionsGranted

            timeoutChoiceCard.setOnClickListener(timeoutChoiceClickListener)
            timeoutChoiceButton.setOnClickListener(timeoutChoiceClickListener)
        }
    }

    /**
     * An extension property for [MaterialButton] that is used to set the visibility of the button.
     *
     * Sets the visibility of the receiver [MaterialButton] and animates it in/out by scaling it.
     *
     * - When the visibility is set to `true`, the button will be enabled and its visibility will be set to [View.VISIBLE].
     * - When the visibility is set to `false`, the button will be disabled and its visibility will be set to [View.GONE].
     *
     * The animation duration is set to 100 milliseconds.
     *
     * @receiver [MaterialButton] The button to set the visibility of.
     *
     * @see MaterialButton
     * @see MaterialButton.isEnabled
     * @see MaterialButton.getVisibility
     * @see MaterialButton.setVisibility
     * @see MaterialButton.animate
     * @see MaterialButton.setScaleX
     * @see MaterialButton.setScaleY
     * @see MaterialButton.getScaleX
     * @see MaterialButton.getScaleY
     */
    private var MaterialButton.animateVisibility: Boolean
        get() = visibility == View.VISIBLE
        set(value) {
            val animationDuration = 100L

            when (value) {
                true -> {
                    isEnabled = true
                    visibility = View.VISIBLE

                    if (!hasOnClickListeners()) {
                        scaleX = 0f
                        scaleY = 0f
                        animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(animationDuration)
                            .start()

                        setOnClickListener { buttonView ->
                            buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                            KeepAwakeService.restart(caffeinateApplication)
                        }
                    }
                }

                else -> {
                    if (hasOnClickListeners()) {
                        scaleX = 1f
                        scaleY = 1f
                        animate()
                            .scaleX(0f)
                            .scaleY(0f)
                            .setDuration(animationDuration)
                            .withEndAction {
                                isEnabled = false
                                visibility = View.GONE

                                setOnClickListener(null)
                            }
                            .start()
                    } else {
                        isEnabled = false
                        visibility = View.GONE
                    }
                }
            }
        }

    /**
     * Returns true if all necessary permissions are granted.
     *
     * @return [Boolean] `true` if all permissions are granted, `false` otherwise.
     */
    @SuppressLint("BatteryLife")
    private fun isAllPermissionsGranted(): Boolean {
        with(binding) {
            var isAllPermissionsGranted = sharedPreferences.isAllPermissionsGranted

            batteryOptimizationTextView.text = getString(R.string.battery_optimization_granted)
            batteryOptimizationImageView.setImageDrawable(grantedDrawable)
            batteryOptimizationImageView.setColorFilter(Color.argb(255, 0, 255, 0))

            backgroundOptimizationTextView.text = getString(R.string.background_optimization_granted)
            backgroundOptimizationImageView.setImageDrawable(grantedDrawable)
            backgroundOptimizationImageView.setColorFilter(Color.argb(255, 0, 255, 0))

            notificationPermissionTextView.text = getString(R.string.notifications_permission_granted)
            notificationPermissionImageView.setImageDrawable(grantedDrawable)
            notificationPermissionImageView.setColorFilter(Color.argb(255, 0, 255, 0))

            if (isAllPermissionsGranted) return true
            val requiredPermissions = listOf(checkBatteryOptimization(), checkBackgroundOptimization(), checkNotificationPermission())

            isAllPermissionsGranted = requiredPermissions.all { it }

            sharedPreferences.isAllPermissionsGranted = isAllPermissionsGranted
            return isAllPermissionsGranted
        }
    }

    /**
     * Checks if the battery optimization is granted.
     *
     * @return [Boolean] `true` if the battery optimization is granted, `false` otherwise.
     */
    @SuppressLint("BatteryLife")
    private fun checkBatteryOptimization(): Boolean {
        with(binding) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            return if (!powerManager.isIgnoringBatteryOptimizations(caffeinateApplication.localizedApplicationContext.packageName)) {
                batteryOptimizationCard.setOnClickListener {
                    startActivity(Intent().apply {
                        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        data = Uri.parse("package:${caffeinateApplication.localizedApplicationContext.packageName}")
                    })
                    requestBatteryOptimizationPermission()
                }
                batteryOptimizationTextView.text = getString(R.string.battery_optimization_not_granted)
                batteryOptimizationImageView.setImageDrawable(notGrantedDrawable)
                batteryOptimizationImageView.setColorFilter(Color.argb(255, 255, 0, 0))

                false
            } else {
                true
            }
        }
    }

    /**
     * Checks if the background optimization is granted.
     *
     * @return [Boolean] `true` if the background optimization is granted, `false` otherwise.
     */
    private fun checkBackgroundOptimization(): Boolean {
        with(binding) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                if (activityManager.isBackgroundRestricted) {
                    backgroundOptimizationCard.setOnClickListener {
                        requestBatteryOptimizationPermission()
                    }
                    backgroundOptimizationTextView.text = getString(R.string.background_optimization_not_granted)
                    backgroundOptimizationImageView.setImageDrawable(notGrantedDrawable)
                    backgroundOptimizationImageView.setColorFilter(Color.argb(255, 255, 0, 0))
                    return false
                } else {
                    return true
                }
            } else {
                return true
            }
        }
    }

    /**
     * Checks if the app has permission to post notifications.
     *
     * @return [Boolean] `true` if permission is granted, `false` otherwise
     */
    private fun checkNotificationPermission(): Boolean {
        with(binding) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermissionCard.setOnClickListener { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 93) }
                    notificationPermissionTextView.text = getString(R.string.notifications_permission_not_granted)
                    notificationPermissionImageView.setImageDrawable(notGrantedDrawable)
                    notificationPermissionImageView.setColorFilter(Color.argb(255, 255, 0, 0))

                    return false
                } else {
                    return true
                }
            } else {
                return true
            }
        }
    }

    private fun checkOverlayPermission() {
        with(binding) {
            val canDrawOverlays = Settings.canDrawOverlays(this@MainActivity)

            changeShowOverlayPreferences(canDrawOverlays)

            if (!canDrawOverlays) {
                overlayPermissionCard.setOnClickListener {
                    overlayPermissionLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = Uri.parse("package:${packageName}")
                    })
                }
                overlayPermissionTextView.text = getString(R.string.overlay_permission_not_granted)
                overlayPermissionImageView.setImageDrawable(notGrantedDrawable)
                overlayPermissionImageView.setColorFilter(Color.argb(255, 255, 0, 0))
            } else {
                overlayPermissionCard.setOnClickListener(null)
                overlayPermissionTextView.text = getString(R.string.overlay_permission_granted)
                overlayPermissionImageView.setImageDrawable(grantedDrawable)
                overlayPermissionImageView.setColorFilter(Color.argb(255, 0, 255, 0))
            }
        }
    }

    /**
     * Requests the user to grant the battery optimization permission.
     *
     * This will start the [ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS][android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS]
     * intent which will open the "Battery optimization" settings screen.
     *
     * This method does nothing if the Android Version is less than [M][android.os.Build.VERSION_CODES.M].
     *
     * @see checkBatteryOptimization
     */
    private fun requestBatteryOptimizationPermission() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_battery_optimization_needed_title))
            .setIcon(R.drawable.coffee_icon)
            .setCancelable(false)
            .setMessage(getString(R.string.dialog_battery_optimization_needed_message))
            .setPositiveButton(getString(R.string.dialog_button_ok)) { _, _ ->
                startActivity(
                        Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${caffeinateApplication.localizedApplicationContext.packageName}")
                        )
                )
            }
            .setNegativeButton(getString(R.string.dialog_button_cancel), null)
            .show()
    }

    private fun registerForOverlayPermission() {
        with(binding) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply { data = Uri.parse("package:${packageName}") }

            overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                val canDrawOverlays = Settings.canDrawOverlays(this@MainActivity)

                changeShowOverlayPreferences(canDrawOverlays)

                if (!canDrawOverlays) {
                    overlayPermissionCard.setOnClickListener { overlayPermissionLauncher.launch(intent) }
                    overlayPermissionTextView.text = getString(R.string.overlay_permission_not_granted)
                    overlayPermissionImageView.setImageDrawable(notGrantedDrawable)
                    overlayPermissionImageView.setColorFilter(Color.argb(255, 255, 0, 0))
                    val snackbar = Snackbar.make(binding.root, getString(R.string.overlay_permission_required), Snackbar.LENGTH_INDEFINITE)
                    snackbar.setAction(getString(R.string.go_to_settings)) {
                        overlayPermissionLauncher.launch(intent)
                    }
                    snackbar.show()
                } else {
                    overlayPermissionCard.setOnClickListener(null)
                    overlayPermissionTextView.text = getString(R.string.overlay_permission_granted)
                    overlayPermissionImageView.setImageDrawable(grantedDrawable)
                    overlayPermissionImageView.setColorFilter(Color.argb(255, 0, 255, 0))
                }
            }
        }
    }

    /**
     * Shows a dialog to allow the user to choose a timeout duration.
     *
     * This dialog will contain a list of checkboxes, each representing a different timeout duration that the user can select. The list of durations
     * is stored in the app's SharedPreferences.
     *
     * When the dialog is shown, the currently selected timeout duration is checked in the list. When the user selects a new timeout duration, the app
     * will start a new [TimeoutJob][com.hifnawy.caffeinate.services.TimeoutJob] with the selected duration.
     *
     * If the user has not selected any timeout durations before, the dialog will be empty and the user will be prompted to select at least one
     * duration.
     *
     * This method is called when the user clicks the "Choose timeout" button in the app's UI.
     */
    private fun showChooseTimeoutDialog() {
        with(binding) {
            timeoutChoiceCard.isClickable = false
            timeoutChoiceButton.isClickable = false
            val dialogBinding = DialogChooseTimeoutsBinding.inflate(LayoutInflater.from(root.context))
            val dialog = MaterialAlertDialogBuilder(root.context)
                .setView(dialogBinding.root)
                .create()
                .apply {
                    setCancelable(false)
                    window?.setLayout(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

            with(dialogBinding) {
                val checkBoxAdapter = CheckBoxAdapter(caffeinateApplication.timeoutCheckBoxes) { checkBoxItems ->
                    checkBoxItems.isEmpty().let { isEmpty ->
                        dialogButtonOk.isEnabled = !isEmpty
                        dialogButtonRemoveTimeout.isEnabled = !isEmpty
                    }
                }

                timeoutsRecyclerView.layoutManager = LinearLayoutManager(root.context)
                timeoutsRecyclerView.adapter = checkBoxAdapter

                dialogButtonRemoveTimeout.setOnClickListener { buttonView ->
                    buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                    checkBoxAdapter.checkBoxItems
                        .filter { checkBoxItem -> checkBoxItem.isChecked }
                        .forEach { checkBoxItem -> checkBoxAdapter.removeCheckBox(checkBoxItem) }
                }

                dialogButtonAddTimeout.setOnClickListener { buttonView ->
                    buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                    showSetCustomTimeoutDialog(
                            valueSetCallback = { timeout ->
                                checkBoxAdapter.addCheckBox(
                                        CheckBoxItem(
                                                text = timeout.toLocalizedFormattedTime(caffeinateApplication.localizedApplicationContext),
                                                isChecked = true,
                                                isEnabled = true,
                                                duration = timeout
                                        )
                                )
                            },
                            onDialogStart = { _, _ -> dialogButtonAddTimeout.isClickable = false },
                            onDialogDismiss = { _, _ -> dialogButtonAddTimeout.isClickable = true }
                    )
                }

                dialogButtonCancel.setOnClickListener { buttonView ->
                    buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    dialog.dismiss()
                    timeoutChoiceCard.isClickable = true
                    timeoutChoiceButton.isClickable = true
                }

                dialogButtonOk.setOnClickListener { buttonView ->
                    buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                    caffeinateApplication.run {
                        timeoutCheckBoxes.clear()
                        checkBoxAdapter.checkBoxItems.forEach { checkBoxItem -> timeoutCheckBoxes.add(checkBoxItem.copy()) }

                        timeoutChoiceSubTextTextView.text = timeoutCheckBoxes.enabledDurations

                        checkBoxAdapter.checkBoxItems
                            .find { checkBoxItem -> checkBoxItem.duration == timeout && !checkBoxItem.isChecked }
                            ?.let {
                                when (lastStatusUpdate) {
                                    is ServiceStatus.Running -> KeepAwakeService.startNextTimeout(this, debounce = false)
                                    else                     -> timeout =
                                            checkBoxAdapter.checkBoxItems.first { checkBoxItem -> checkBoxItem.isChecked }.duration
                                }
                            }
                        ?: when (lastStatusUpdate) {
                            is ServiceStatus.Stopped -> timeout =
                                    checkBoxAdapter.checkBoxItems.first { checkBoxItem -> checkBoxItem.isChecked }.duration

                            else                     -> Unit // do nothing if the service is running
                        }

                        sharedPreferences.timeoutCheckBoxes = timeoutCheckBoxes

                        dialog.dismiss()
                        timeoutChoiceCard.isClickable = true
                        timeoutChoiceButton.isClickable = true
                    }
                }
            }

            dialog.show()
        }
    }

    /**
     * Shows a dialog to the user to let them choose a custom timeout.
     *
     * @param valueSetCallback [(timeout: Duration) -> Unit][valueSetCallback] a callback that will be called when the user sets a value; the callback
     * will be passed the number of hours, minutes and
     * seconds that the user has chosen
     * @param onDialogStart [(dialog: AlertDialog, dialogBinding: DialogSetCustomTimeoutBinding) -> Unit][onDialogStart] a callback that will be
     * called when the dialog is shown
     * @param onDialogDismiss [(dialog: AlertDialog, dialogBinding: DialogSetCustomTimeoutBinding) -> Unit][onDialogDismiss] a callback that will be
     * called when the dialog is dismissed
     */
    private fun showSetCustomTimeoutDialog(
            valueSetCallback: (timeout: Duration) -> Unit,
            onDialogStart: ((dialog: AlertDialog, dialogBinding: DialogSetCustomTimeoutBinding) -> Unit)? = null,
            onDialogDismiss: ((dialog: AlertDialog, dialogBinding: DialogSetCustomTimeoutBinding) -> Unit)? = null,
    ) {
        with(binding) {
            val dialogBinding = DialogSetCustomTimeoutBinding.inflate(LayoutInflater.from(root.context))
            val dialog = MaterialAlertDialogBuilder(root.context)
                .setView(dialogBinding.root)
                .create()
                .apply {
                    setCancelable(false)
                    window?.setLayout(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

            onDialogStart?.invoke(dialog, dialogBinding)

            with(dialogBinding) {
                val onNumberPickerAnimationStart = { _: Animator -> dialogButtonRandomTimeout.isEnabled = false }
                val onNumberPickerAnimationEnd = { _: Animator -> dialogButtonRandomTimeout.isEnabled = true }

                hoursNumberPicker.setFormatter { value -> "%02d".format(value) }
                minutesNumberPicker.setFormatter { value -> "%02d".format(value) }
                secondsNumberPicker.setFormatter { value -> "%02d".format(value) }

                NumberPicker.OnValueChangeListener { numberPicker, _, _ ->
                    numberPicker.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }.run {
                    hoursNumberPicker.setOnValueChangedListener(this)
                    minutesNumberPicker.setOnValueChangedListener(this)
                    secondsNumberPicker.setOnValueChangedListener(this)
                }

                hoursNumberPicker.animateRandom(onAnimationStart = onNumberPickerAnimationStart, onAnimationEnd = onNumberPickerAnimationEnd)
                minutesNumberPicker.animateRandom(onAnimationStart = onNumberPickerAnimationStart, onAnimationEnd = onNumberPickerAnimationEnd)
                secondsNumberPicker.animateRandom(onAnimationStart = onNumberPickerAnimationStart, onAnimationEnd = onNumberPickerAnimationEnd)

                dialogButtonRandomTimeout.setOnClickListener { buttonView ->
                    buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                    hoursNumberPicker.animateFrom(
                            hoursNumberPicker.value,
                            onAnimationStart = onNumberPickerAnimationStart,
                            onAnimationEnd = onNumberPickerAnimationEnd
                    )
                    minutesNumberPicker.animateFrom(
                            minutesNumberPicker.value,
                            onAnimationStart = onNumberPickerAnimationStart,
                            onAnimationEnd = onNumberPickerAnimationEnd
                    )
                    secondsNumberPicker.animateFrom(
                            secondsNumberPicker.value,
                            onAnimationStart = onNumberPickerAnimationStart,
                            onAnimationEnd = onNumberPickerAnimationEnd
                    )
                }

                dialogButtonCancel.setOnClickListener { buttonView ->
                    buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                    dialog.dismiss()

                    onDialogDismiss?.invoke(dialog, dialogBinding)
                }

                dialogButtonOk.setOnClickListener { buttonView ->
                    buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    val timeout = when {
                        hoursNumberPicker.value + minutesNumberPicker.value + secondsNumberPicker.value == 0 -> Duration.INFINITE
                        else                                                                                 ->
                            hoursNumberPicker.value.hours + minutesNumberPicker.value.minutes + secondsNumberPicker.value.seconds
                    }

                    valueSetCallback(timeout)

                    dialog.dismiss()

                    onDialogDismiss?.invoke(dialog, dialogBinding)
                }
            }

            dialog.show()
        }
    }

    /**
     * Animates the value of this [MaterialNumberPicker] from the closest boundary (either [maxValue][MaterialNumberPicker.getMaxValue] or
     * [minValue][MaterialNumberPicker.getMinValue]) to a value chosen randomly between [minValue][MaterialNumberPicker.getMinValue] and
     * [maxValue][MaterialNumberPicker.getMaxValue]. if it is closer to that boundary.
     *
     * @param animationDuration [Long] The duration of the animation in milliseconds. Defaults to `1000L`.
     * @param onAnimationStart [(animator: Animator) -> Unit][onAnimationStart] A callback that will be called when the animation starts.
     * Defaults to an empty function.
     * @param onAnimationEnd [(animator: Animator) -> Unit][onAnimationEnd] A callback that will be called when the animation ends.
     * Defaults to an empty function.
     */
    private fun MaterialNumberPicker.animateRandom(
            animationDuration: Long = 1000L,
            onAnimationStart: (animator: Animator) -> Unit = {},
            onAnimationEnd: (animator: Animator) -> Unit = {},
    ) {
        val toValue = Random.nextInt(minValue, maxValue)

        val (startValue, endValue) = when {
            // fromValue is closer to minValue
            abs(toValue - minValue) < abs(toValue - maxValue) -> maxValue to toValue
            // fromValue is closer to maxValue
            else                                              -> minValue to toValue
        }

        ValueAnimator.ofInt(startValue, endValue).apply {
            addUpdateListener { animator ->
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                value = animator.animatedValue as Int
            }

            addListener(onStart = onAnimationStart, onEnd = onAnimationEnd)

            duration = animationDuration

            start()
        }
    }

    /**
     * Animates the value of this [MaterialNumberPicker] from the given [fromValue] to a random value that's at least at a distance of
     *  -\+(([minValue][MaterialNumberPicker.getMinValue] + [maxValue][MaterialNumberPicker.getMaxValue]) / 2) from the current
     * [value][MaterialNumberPicker.getValue].
     *
     * @param fromValue [Int] The starting value of the animation.
     * @param animationDuration [Long] The duration of the animation in milliseconds. Defaults to `1000L`.
     * @param onAnimationStart [(animator: Animator) -> Unit][onAnimationStart] A callback that will be called when the animation starts.
     * Defaults to an empty function.
     * @param onAnimationEnd [(animator: Animator) -> Unit][onAnimationEnd] A callback that will be called when the animation ends.
     * Defaults to an empty function.
     */
    private fun MaterialNumberPicker.animateFrom(
            fromValue: Int,
            animationDuration: Long = 1000L,
            onAnimationStart: (animator: Animator) -> Unit = {},
            onAnimationEnd: (animator: Animator) -> Unit = {},
    ) {
        val minDistance = (minValue + maxValue) / 2

        val (startValue, endValue) = when {
            // fromValue is closer to minValue
            abs(value - minValue) < abs(value - maxValue) -> fromValue to Random.nextInt(minDistance, maxValue)
            // fromValue is closer to maxValue
            else                                          -> fromValue to Random.nextInt(minValue, minDistance)
        }

        ValueAnimator.ofInt(startValue, endValue).apply {
            addUpdateListener { animator ->
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                value = animator.animatedValue as Int
            }

            addListener(onStart = onAnimationStart, onEnd = onAnimationEnd)

            duration = animationDuration

            start()
        }
    }
}