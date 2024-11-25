package com.hifnawy.caffeinate.view

import android.Manifest
import android.animation.Animator
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.animation.addListener
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.assent.Permission
import com.afollestad.assent.askForPermissions
import com.github.stephenvinouze.materialnumberpickercore.MaterialNumberPicker
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.controller.KeepAwakeService
import com.hifnawy.caffeinate.controller.KeepAwakeService.Companion.KeepAwakeServiceState
import com.hifnawy.caffeinate.controller.ServiceStatus
import com.hifnawy.caffeinate.controller.ServiceStatusObserver
import com.hifnawy.caffeinate.controller.SharedPrefsManager
import com.hifnawy.caffeinate.controller.SharedPrefsObserver
import com.hifnawy.caffeinate.databinding.ActivityMainBinding
import com.hifnawy.caffeinate.databinding.DialogChooseTimeoutsBinding
import com.hifnawy.caffeinate.databinding.DialogSetCustomTimeoutBinding
import com.hifnawy.caffeinate.utils.ActivityExtensionFunctions.setActivityTheme
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toLocalizedFormattedTime
import com.hifnawy.caffeinate.utils.MutableListExtensionFunctions.addObserver
import com.hifnawy.caffeinate.utils.MutableListExtensionFunctions.removeObserver
import com.hifnawy.caffeinate.utils.ViewExtensionFunctions.isVisible
import com.hifnawy.caffeinate.viewModel.MainActivityViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import com.google.android.material.R as materialR
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

    /**
     * Lazily retrieves the instance of [MainActivityViewModel] associated with this activity.
     *
     * This property uses the [viewModels] delegate to create and store an instance of
     * [MainActivityViewModel] that is associated with this activity and its lifecycle.
     *
     * @return [MainActivityViewModel] the instance of the view model associated with this activity.
     */
    private val viewModel: MainActivityViewModel by viewModels()

    /**
     * Lazily initializes the binding for the activity's layout using [ActivityMainBinding].
     *
     * This binding is used to access the views defined in the activity's layout XML file.
     * The layout is inflated using the [getLayoutInflater] provided by the activity.
     */
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    /**
     * Lazily retrieves the instance of [CaffeinateApplication] associated with this activity.
     *
     * This property casts the [getApplication] context to [CaffeinateApplication] and provides
     * access to application-wide resources and utilities.
     *
     * @return [CaffeinateApplication] the application instance associated with this activity.
     */
    private val caffeinateApplication by lazy { application as CaffeinateApplication }

    /**
     * Lazily initializes the shared preferences manager for the application using [SharedPrefsManager].
     *
     * This property provides access to the shared preferences of the application, which are used to store and retrieve values
     * related to the application's settings and state.
     *
     * @return [SharedPrefsManager] the shared preferences manager for the application.
     */
    private val sharedPreferences by lazy { SharedPrefsManager(caffeinateApplication) }

    /**
     * Lazily initializes the drawable resource for granted permissions.
     *
     * This drawable is used to visually represent granted permissions in the UI.
     * The drawable is fetched from the application resources using the context of the root view.
     *
     * @return [Drawable] the drawable resource for granted permissions.
     */
    private val grantedDrawable by lazy { AppCompatResources.getDrawable(binding.root.context, R.drawable.ok_icon_circle) }

    /**
     * Lazily initializes the not granted drawable used in the permission cards.
     *
     * This drawable is used in the permission cards to indicate that a permission is not granted.
     * It is initialized lazily when it is first accessed. The instance is created using the
     * [AppCompatResources.getDrawable] method and the resource identifier for the not granted
     * drawable.
     *
     * @return [Drawable] the not granted drawable.
     */
    private val notGrantedDrawable by lazy { AppCompatResources.getDrawable(binding.root.context, R.drawable.nok_icon_circle) }

    /**
     * Lazily initializes the color tint used for the granted permission cards.
     *
     * This color tint is used in the permission cards to indicate that a permission is granted.
     * It is initialized lazily when it is first accessed. The instance is created using the
     * [MaterialColors.getColor] method and the resource identifier for the primary color.
     *
     * @return [Int] the color tint used for the granted permission cards.
     */
    private val grantedDrawableTint by lazy { MaterialColors.getColor(binding.root, materialR.attr.colorPrimary) }

    /**
     * Lazily initializes the color tint used for the not granted permission cards.
     *
     * This color tint is used in the permission cards to indicate that a permission is not granted.
     * It is initialized lazily when it is first accessed. The instance is created using the
     * [MaterialColors.getColor] method and the resource identifier for the error color.
     *
     * @return [Int] the color tint used for the not granted permission cards.
     */
    private val notGrantedDrawableTint by lazy { MaterialColors.getColor(binding.root, materialR.attr.colorError) }

    /**
     * The [ActivityResultLauncher] used to launch the overlay permission intent.
     *
     * This launcher is used to launch the overlay permission intent when the user clicks on the
     * overlay permission card. The result of the launcher is not used in this activity.
     */
    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>

    /**
     * An extension property on [Iterable] to get the enabled durations.
     *
     * This property is an extension on [Iterable] to get the enabled durations. It is used to get
     * the durations of the enabled items in the iterable. The durations are joined together with
     * a separator and limited to 10 items. If the iterable has more than 10 items, the last item
     * will be "...".
     *
     * @return [CharSequence] the enabled durations.
     */
    private val Iterable<CheckBoxItem>.enabledDurations: CharSequence
        get() = filter { checkBoxItem -> checkBoxItem.isChecked }.joinToString(
                separator = ", ",
                limit = 10,
                truncated = "..."
        ) { checkBoxItem -> checkBoxItem.duration.toLocalizedFormattedTime(binding.root.context) }

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
        get() = isVisible
        set(value) {
            if (value && isEnabled) return
            val animationDuration = 300L
            val rotationFrom = if (value) -360f else 360f
            val rotationTo = if (value) 360f else -360f
            val scaleFrom = if (value) 0f else 1f
            val scaleTo = if (value) 1f else 0f

            rotation = rotationFrom
            scaleX = scaleFrom
            scaleY = scaleFrom

            animate()
                .rotation(rotationTo)
                .scaleX(scaleTo)
                .scaleY(scaleTo)
                .setDuration(animationDuration)
                .withStartAction { if (value && !isEnabled) viewModel.isRestartButtonEnabled.value = true }
                .withEndAction { if (!value && isEnabled) viewModel.isRestartButtonEnabled.value = false }
                .start()
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

        sharedPreferences.run {
            setActivityTheme(contrastLevel, theme.mode, isMaterialYouEnabled)
        }

        enableEdgeToEdge()
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}


        with(binding) {
            appBar.post {
                viewModel.run {
                    appBarVerticalOffset.observe(this@MainActivity) { verticalOffset ->
                        with(appBar) {
                            val params = layoutParams as CoordinatorLayout.LayoutParams
                            val behavior = params.behavior as AppBarLayout.Behavior
                            behavior.topAndBottomOffset = verticalOffset
                            requestLayout()
                        }
                    }

                    isRestartButtonEnabled.observe(this@MainActivity) { isButtonEnabled ->
                        with(restartButton) {
                            isEnabled = isButtonEnabled
                            isVisible = isButtonEnabled
                        }
                    }
                }
            }

            appBar.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
                viewModel.appBarVerticalOffset.value = verticalOffset
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

        changeMaterialYouPreferences(DynamicColors.isDynamicColorAvailable())

        with(binding) {
            appThemeSelectionView.run {
                val themeButtons = mapOf(
                        appThemeSystemDefaultButton to SharedPrefsManager.Theme.SYSTEM_DEFAULT,
                        appThemeSystemLightButton to SharedPrefsManager.Theme.LIGHT,
                        appThemeSystemDarkButton to SharedPrefsManager.Theme.DARK
                )
                val selectedThemeButtonId = when (sharedPreferences.theme) {
                    SharedPrefsManager.Theme.SYSTEM_DEFAULT -> appThemeSystemDefaultButton.id
                    SharedPrefsManager.Theme.LIGHT          -> appThemeSystemLightButton.id
                    SharedPrefsManager.Theme.DARK           -> appThemeSystemDarkButton.id
                }

                appThemeToggleGroup.check(selectedThemeButtonId)

                themeButtons.forEach { entry ->
                    entry.key.setOnClickListener { button ->
                        button.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        val theme = entry.value

                        if (theme == sharedPreferences.theme) return@setOnClickListener

                        sharedPreferences.run { changeThemeAndContrast(theme, contrastLevel, isMaterialYouEnabled) }
                    }
                }
            }

            appContrastSelectionView.run {
                val contrastButtons = mapOf(
                        appContrastStandardButton to SharedPrefsManager.ContrastLevel.STANDARD,
                        appContrastMediumButton to SharedPrefsManager.ContrastLevel.MEDIUM,
                        appContrastHighButton to SharedPrefsManager.ContrastLevel.HIGH
                )
                val selectedContrastButtonId = when (sharedPreferences.contrastLevel) {
                    SharedPrefsManager.ContrastLevel.STANDARD -> appContrastStandardButton.id
                    SharedPrefsManager.ContrastLevel.MEDIUM   -> appContrastMediumButton.id
                    SharedPrefsManager.ContrastLevel.HIGH     -> appContrastHighButton.id
                }

                appContrastToggleGroup.check(selectedContrastButtonId)

                contrastButtons.forEach { entry ->
                    entry.key.setOnClickListener { button ->
                        button.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        val contrastLevel = entry.value

                        if (contrastLevel == sharedPreferences.contrastLevel) return@setOnClickListener

                        sharedPreferences.run { changeThemeAndContrast(theme, contrastLevel, isMaterialYouEnabled) }
                    }
                }
            }

            toggleButton.run {
                setOnClickListener { buttonView ->
                    if (!sharedPreferences.isAllPermissionsGranted) return@setOnClickListener

                    buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    KeepAwakeService.startNextTimeout(caffeinateApplication)
                }

                setOnLongClickListener {
                    if (!sharedPreferences.isAllPermissionsGranted) return@setOnLongClickListener false

                    when (caffeinateApplication.lastStatusUpdate) {
                        is ServiceStatus.Stopped -> KeepAwakeService.startIndefinitely(caffeinateApplication)
                        is ServiceStatus.Running -> KeepAwakeService.toggleState(caffeinateApplication, KeepAwakeServiceState.STATE_STOP)
                    }

                    return@setOnLongClickListener true
                }
            }

            restartButton.run {
                isVisible = viewModel.isRestartButtonEnabled.value ?: false
                isEnabled = viewModel.isRestartButtonEnabled.value ?: false

                setOnClickListener { buttonView ->
                    if (!sharedPreferences.isAllPermissionsGranted) return@setOnClickListener

                    buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    KeepAwakeService.restart(caffeinateApplication)
                }
            }
        }

        caffeinateApplication.run {
            applyLocaleConfiguration()

            checkAllPermissions { isAllPermissionsGranted -> onIsAllPermissionsGrantedUpdated(isAllPermissionsGranted) }

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
     * Initializes the contents of the Activity's options menu.
     *
     * This method is called once when the menu is first created. It inflates the menu resource
     * and adds items to the menu. The menu will be displayed when the user presses the menu button.
     *
     * @param menu [Menu] The options menu in which items are placed.
     * @return `true` if the menu is to be displayed; `false` otherwise.
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    /**
     * Called when an item in the options menu is selected.
     *
     * This is called when an item in the options menu is selected. It is called after the menu is shown, but before the menu is hidden.
     *
     * @param item [MenuItem] The menu item that was selected.
     * @return `true` if the menu item was successfully handled, `false` otherwise.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.about -> AboutBottomSheetFragment.newInstance.show(supportFragmentManager, "about")
        }

        return when (item.itemId) {
            in listOf(R.id.about) -> true
            else                  -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Called when there is a change in the permission state indicating whether all necessary permissions have been granted.
     *
     * @param isAllPermissionsGranted [Boolean] `true` if all necessary permissions have been granted, `false` otherwise.
     */
    override fun onIsAllPermissionsGrantedUpdated(isAllPermissionsGranted: Boolean) {
        binding.toggleButton.isEnabled = isAllPermissionsGranted

        changeShowOverlayPreferences(isAllPermissionsGranted && checkOverlayPermission())
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

            restartButton.animateVisibility = status.run { this is ServiceStatus.Running && isCountingDown && !isRestarted }
            toggleButton.text = when (status) {
                is ServiceStatus.Stopped -> getString(R.string.caffeinate_button_off)
                is ServiceStatus.Running -> status.remaining.toLocalizedFormattedTime(root.context)
            }
        }
    }

    /**
     * Checks if all the necessary permissions have been granted.
     *
     * @param callback [((isAllPermissionsGranted: Boolean) -> Unit)[callback] The callback to be called when the check is complete.
     */
    private fun checkAllPermissions(callback: ((isAllPermissionsGranted: Boolean) -> Unit)? = null) = listOf(
            checkBatteryOptimization(),
            checkBackgroundOptimization(),
            checkNotificationPermission(),
    ).all { it }.let { isGranted ->
        callback?.invoke(isGranted)
        sharedPreferences.isAllPermissionsGranted = isGranted
    }

    /**
     * Checks if the battery optimization is granted.
     *
     * @return [Boolean] `true` if the battery optimization is granted, `false` otherwise.
     */
    private fun checkBatteryOptimization(): Boolean {
        val isGranted = (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        val permissionCardClickListener = when {
            isGranted -> null
            else      -> View.OnClickListener { requestBatteryOptimizationPermission() }
        }
        val permissionCardText = when {
            isGranted -> getString(R.string.battery_optimization_granted)
            else      -> getString(R.string.background_optimization_not_granted)
        }
        val permissionCardImage = when {
            isGranted -> grantedDrawable
            else      -> notGrantedDrawable
        }
        val permissionCardImageTint = when {
            isGranted -> grantedDrawableTint
            else      -> notGrantedDrawableTint
        }

        with(binding) {
            batteryOptimizationCard.setOnClickListener(permissionCardClickListener)
            batteryOptimizationTextView.text = permissionCardText
            batteryOptimizationImageView.setImageDrawable(permissionCardImage)
            batteryOptimizationImageView.setColorFilter(permissionCardImageTint)
        }

        return isGranted
    }

    /**
     * Checks if the background optimization is granted.
     *
     * @return [Boolean] `true` if the background optimization is granted, `false` otherwise.
     */
    private fun checkBackgroundOptimization(): Boolean {
        val isGranted = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> !(getSystemService(ACTIVITY_SERVICE) as ActivityManager).isBackgroundRestricted
            else                                           -> true
        }
        val permissionCardClickListener = when {
            isGranted -> null
            else      -> View.OnClickListener { requestBatteryOptimizationPermission() }
        }
        val permissionCardText = when {
            isGranted -> getString(R.string.background_optimization_granted)
            else      -> getString(R.string.background_optimization_not_granted)
        }
        val permissionCardImage = when {
            isGranted -> grantedDrawable
            else      -> notGrantedDrawable
        }
        val permissionCardImageTint = when {
            isGranted -> grantedDrawableTint
            else      -> notGrantedDrawableTint
        }

        with(binding) {
            backgroundOptimizationCard.setOnClickListener(permissionCardClickListener)
            backgroundOptimizationTextView.text = permissionCardText
            backgroundOptimizationImageView.setImageDrawable(permissionCardImage)
            backgroundOptimizationImageView.setColorFilter(permissionCardImageTint)
        }

        return isGranted
    }

    /**
     * Checks if the app has permission to post notifications.
     *
     * @return [Boolean] `true` if permission is granted, `false` otherwise
     */
    private fun checkNotificationPermission(): Boolean {
        val isGranted = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            else                                                  -> true
        }
        val permissionCardClickListener = when {
            isGranted -> null
            else      -> View.OnClickListener { askForPermissions(Permission.POST_NOTIFICATIONS) {} }
        }
        val permissionCardText = when {
            isGranted -> getString(R.string.notifications_permission_granted)
            else      -> getString(R.string.notifications_permission_not_granted)
        }
        val permissionCardImage = when {
            isGranted -> grantedDrawable
            else      -> notGrantedDrawable
        }
        val permissionCardImageTint = when {
            isGranted -> grantedDrawableTint
            else      -> notGrantedDrawableTint
        }

        with(binding) {
            notificationPermissionCard.setOnClickListener(permissionCardClickListener)
            notificationPermissionTextView.text = permissionCardText
            notificationPermissionImageView.setImageDrawable(permissionCardImage)
            notificationPermissionImageView.setColorFilter(permissionCardImageTint)
        }

        return isGranted
    }

    /**
     * Checks if the app has permission to draw over other apps.
     *
     * @return [Boolean] `true` if permission is granted, `false` otherwise
     */
    private fun checkOverlayPermission(): Boolean {
        val isGranted = Settings.canDrawOverlays(this)
        val permissionCardClickListener = when {
            isGranted -> null
            else      -> View.OnClickListener {
                overlayPermissionLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${packageName}"))
                )
            }
        }
        val permissionCardText = when {
            isGranted -> getString(R.string.overlay_permission_granted)
            else      -> getString(R.string.overlay_permission_not_granted)
        }
        val permissionCardImage = when {
            isGranted -> grantedDrawable
            else      -> notGrantedDrawable
        }
        val permissionCardImageTint = when {
            isGranted -> grantedDrawableTint
            else      -> notGrantedDrawableTint
        }

        with(binding) {
            overlayPermissionCard.setOnClickListener(permissionCardClickListener)
            overlayPermissionTextView.text = permissionCardText
            overlayPermissionImageView.setImageDrawable(permissionCardImage)
            overlayPermissionImageView.setColorFilter(permissionCardImageTint)
        }

        return isGranted
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

    /**
     * Changes the theme and contrast level of the application.
     *
     * This function takes the given [selectedTheme], [selectedContrastLevel], and
     * [selectedIsMaterialYouEnabled] values and applies them to the application.
     * It updates the [SharedPrefsManager.theme], [SharedPrefsManager.contrastLevel],
     * and [SharedPrefsManager.isMaterialYouEnabled] properties of the [sharedPreferences]
     * to reflect the new values.
     *
     * - If [selectedIsMaterialYouEnabled] is true and [SharedPrefsManager.contrastLevel] is not
     * [SharedPrefsManager.ContrastLevel.STANDARD], then the [SharedPrefsManager.contrastLevel]
     * is set to [SharedPrefsManager.ContrastLevel.STANDARD], otherwise it is set to
     * [selectedContrastLevel].
     * - If [SharedPrefsManager.contrastLevel] is [SharedPrefsManager.ContrastLevel.STANDARD] and
     * [selectedIsMaterialYouEnabled] is true, then the [SharedPrefsManager.isMaterialYouEnabled]
     * is set to true, otherwise it is set to false.
     *
     * @param selectedTheme The new theme to apply to the application.
     * @param selectedContrastLevel The new contrast level to apply to the application.
     * @param selectedIsMaterialYouEnabled Whether to enable Material You theme or not.
     *
     * @see SharedPrefsManager.Theme
     * @see SharedPrefsManager.ContrastLevel
     * @see SharedPrefsManager.theme
     * @see SharedPrefsManager.contrastLevel
     * @see SharedPrefsManager.isMaterialYouEnabled
     */
    private fun changeThemeAndContrast(
            selectedTheme: SharedPrefsManager.Theme,
            selectedContrastLevel: SharedPrefsManager.ContrastLevel,
            selectedIsMaterialYouEnabled: Boolean
    ) = sharedPreferences.run {
        val isToRecreate = theme == selectedTheme
        val newContrastLevel =
                ((selectedContrastLevel.isNotStandard && !selectedIsMaterialYouEnabled) ||
                 (selectedContrastLevel.isNotStandard && contrastLevel.isStandard && isMaterialYouEnabled)).let { isNotStandard ->
                    when {
                        isNotStandard -> selectedContrastLevel
                        else          -> SharedPrefsManager.ContrastLevel.STANDARD
                    }
                }
        val newIsMaterialYouEnabled =
                (selectedIsMaterialYouEnabled && selectedContrastLevel.isStandard) ||
                (selectedIsMaterialYouEnabled && !isMaterialYouEnabled && contrastLevel.isNotStandard)

        theme = selectedTheme
        contrastLevel = newContrastLevel
        isMaterialYouEnabled = newIsMaterialYouEnabled

        when {
            isToRecreate -> recreate()
            else         -> delegate.localNightMode = selectedTheme.mode
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
     *
     * @param isEnabled [Boolean] `true` if the Material You preferences should be enabled, `false` otherwise.
     */
    private fun changeMaterialYouPreferences(isEnabled: Boolean) {
        with(binding) {
            materialYouCard.isEnabled = isEnabled
            materialYouTextView.isEnabled = isEnabled
            materialYouSubTextTextView.isEnabled = isEnabled
            materialYouSubTextTextView.isVisible = !isEnabled || sharedPreferences.isMaterialYouEnabled
            materialYouSubTextTextView.text = when {
                isEnabled -> getString(R.string.material_you_pref_description)
                else      -> getString(R.string.material_you_pref_not_available)
            }
            materialYouSwitch.isEnabled = isEnabled
            materialYouSwitch.isChecked = sharedPreferences.run { isMaterialYouEnabled && contrastLevel.isStandard }

            materialYouCard.setOnClickListener { materialYouSwitch.isChecked = !sharedPreferences.isMaterialYouEnabled }
            materialYouSwitch.setOnCheckedChangeListener { switch, isChecked ->
                switch.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                lifecycleScope.launch {
                    materialYouSubTextTextView.isEnabled = isChecked
                    materialYouSubTextTextView.isVisible = isChecked

                    delay(300)

                    sharedPreferences.run { changeThemeAndContrast(theme, contrastLevel, isChecked) }
                }
            }
        }
    }

    /**
     * Enables the overlay preferences.
     *
     * The overlay preferences are:
     * 1. A [MaterialCardView][com.google.android.material.card.MaterialCardView] that shows the overlay preferences.
     * 2. A [TextView][android.widget.TextView] that shows the overlay preferences title.
     * 3. A [TextView][android.widget.TextView] that shows the overlay preferences subtitle.
     * 4. A [MaterialSwitch][com.google.android.material.materialswitch.MaterialSwitch] that toggles the overlay preferences.
     *
     * The overlay preferences are enabled when the user has granted the [SYSTEM_ALERT_WINDOW][Manifest.permission.SYSTEM_ALERT_WINDOW] permission.
     *
     * @param isEnabled [Boolean] `true` if the overlay preferences should be enabled, `false` otherwise.
     */
    private fun changeShowOverlayPreferences(isEnabled: Boolean) {
        with(binding) {
            overlayCard.isEnabled = isEnabled
            overlayTextView.isEnabled = isEnabled
            overlaySubTextTextView.isEnabled = isEnabled && sharedPreferences.isOverlayEnabled
            overlaySubTextTextView.isVisible = isEnabled && sharedPreferences.isOverlayEnabled
            overlaySwitch.isEnabled = isEnabled
            overlaySwitch.isChecked = sharedPreferences.isOverlayEnabled

            overlayCard.setOnClickListener { overlaySwitch.isChecked = !sharedPreferences.isOverlayEnabled }
            overlaySwitch.setOnCheckedChangeListener { switch, isChecked ->
                switch.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                overlaySubTextTextView.isEnabled = isChecked
                overlaySubTextTextView.isVisible = isChecked
                sharedPreferences.isOverlayEnabled = isChecked
            }
        }
    }

    /**
     * Enables the allow dimming preferences.
     *
     * The allow dimming preferences are:
     * 1. A [MaterialCardView][com.google.android.material.card.MaterialCardView] that shows the allow dimming preferences.
     * 2. A [TextView][android.widget.TextView] that shows the allow dimming preferences title.
     * 3. A [TextView][android.widget.TextView] that shows the allow dimming preferences subtitle.
     * 4. A [MaterialSwitch][com.google.android.material.materialswitch.MaterialSwitch] that toggles the allow dimming preferences.
     *
     * The allow dimming preferences are enabled when the user has granted the [SYSTEM_ALERT_WINDOW][Manifest.permission.SYSTEM_ALERT_WINDOW] permission.
     *
     * @param isEnabled [Boolean] `true` if the allow dimming preferences should be enabled, `false` otherwise.
     */
    private fun changeAllowDimmingPreferences(isEnabled: Boolean) {
        with(binding) {
            allowDimmingCard.isEnabled = isEnabled
            allowDimmingTextView.isEnabled = isEnabled
            allowDimmingSubTextTextView.isEnabled = isEnabled && sharedPreferences.isDimmingEnabled
            allowDimmingSubTextTextView.isVisible = isEnabled && sharedPreferences.isDimmingEnabled
            allowDimmingSwitch.isEnabled = isEnabled
            allowDimmingSwitch.isChecked = sharedPreferences.isDimmingEnabled

            allowDimmingCard.setOnClickListener { allowDimmingSwitch.isChecked = !sharedPreferences.isDimmingEnabled }
            allowDimmingSwitch.setOnCheckedChangeListener { switch, isChecked ->
                switch.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                allowDimmingSubTextTextView.isEnabled = isChecked
                allowDimmingSubTextTextView.isVisible = isChecked
                sharedPreferences.isDimmingEnabled = isChecked
            }
        }
    }

    /**
     * Enables the allow while locked preferences.
     *
     * The allow while locked preferences are:
     * 1. A [MaterialCardView][com.google.android.material.card.MaterialCardView] that shows the allow while locked preferences.
     * 2. A [TextView][android.widget.TextView] that shows the allow while locked preferences title.
     * 3. A [TextView][android.widget.TextView] that shows the allow while locked preferences subtitle.
     * 4. A [MaterialSwitch][com.google.android.material.materialswitch.MaterialSwitch] that toggles the allow while locked preferences.
     *
     * The allow while locked preferences are enabled when the user has granted the [SYSTEM_ALERT_WINDOW][Manifest.permission.SYSTEM_ALERT_WINDOW] permission.
     *
     * @param isEnabled [Boolean] `true` if the allow while locked preferences should be enabled, `false` otherwise.
     */
    private fun changeAllowWhileLockedPreferences(isEnabled: Boolean) {
        with(binding) {
            allowWhileLockedCard.isEnabled = isEnabled
            allowWhileLockedTextView.isEnabled = isEnabled
            allowWhileLockedSubTextTextView.isEnabled = isEnabled && sharedPreferences.isWhileLockedEnabled
            allowWhileLockedSubTextTextView.isVisible = isEnabled && sharedPreferences.isWhileLockedEnabled
            allowWhileLockedSwitch.isEnabled = isEnabled
            allowWhileLockedSwitch.isChecked = sharedPreferences.isWhileLockedEnabled

            allowWhileLockedCard.setOnClickListener { allowWhileLockedSwitch.isChecked = !sharedPreferences.isWhileLockedEnabled }
            allowWhileLockedSwitch.setOnCheckedChangeListener { switch, isChecked ->
                switch.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                allowWhileLockedSubTextTextView.isEnabled = isChecked
                allowWhileLockedSubTextTextView.isVisible = isChecked
                sharedPreferences.isWhileLockedEnabled = isChecked
            }
        }
    }

    /**
     * Enables the timeout preferences.
     *
     * The timeout preferences are:
     * 1. A [MaterialCardView][com.google.android.material.card.MaterialCardView] that shows the timeout preferences.
     * 2. A [TextView][android.widget.TextView] that shows the timeout preferences title.
     * 3. A [TextView][android.widget.TextView] that shows the timeout preferences subtitle.
     * 4. A [Button][android.widget.Button] that allows the user to choose a timeout duration.
     *
     * The timeout preferences are enabled when the user has granted the [SYSTEM_ALERT_WINDOW][Manifest.permission.SYSTEM_ALERT_WINDOW] permission.
     *
     * @param isEnabled [Boolean] `true` if all required permissions are granted, `false` otherwise.
     */
    private fun changeTimeoutsPreferences(isEnabled: Boolean) {
        with(binding) {
            val timeoutChoiceClickListener = View.OnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                showChooseTimeoutDialog()
            }

            timeoutChoiceCard.isEnabled = isEnabled
            timeoutChoiceTextView.isEnabled = isEnabled
            timeoutChoiceSubTextTextView.isEnabled = isEnabled
            timeoutChoiceSubTextTextView.isVisible = isEnabled
            timeoutChoiceSubTextTextView.text = sharedPreferences.timeoutCheckBoxes.enabledDurations
            timeoutChoiceButton.isEnabled = isEnabled

            timeoutChoiceCard.setOnClickListener(timeoutChoiceClickListener)
            timeoutChoiceButton.setOnClickListener(timeoutChoiceClickListener)
        }
    }

    /**
     * Shows a dialog to allow the user to choose a timeout duration.
     *
     * This dialog will contain a list of checkboxes, each representing a different timeout duration that the user can select. The list of durations
     * is stored in the app's SharedPreferences.
     *
     * When the dialog is shown, the currently selected timeout duration is checked in the list. When the user selects a new timeout duration, the app
     * will start a new [TimeoutJob][com.hifnawy.caffeinate.controller.TimeoutJob] with the selected duration.
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