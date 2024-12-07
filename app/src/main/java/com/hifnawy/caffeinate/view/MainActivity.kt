package com.hifnawy.caffeinate.view

import android.Manifest
import android.app.ActivityManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.PictureInPictureUiState
import android.app.RemoteAction
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Rational
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import com.afollestad.assent.Permission
import com.afollestad.assent.askForPermissions
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.controller.KeepAwakeService
import com.hifnawy.caffeinate.controller.KeepAwakeService.Companion.KeepAwakeServiceState
import com.hifnawy.caffeinate.controller.PiPAction
import com.hifnawy.caffeinate.controller.PictureInPictureActionsReceiver
import com.hifnawy.caffeinate.controller.ServiceStatus
import com.hifnawy.caffeinate.controller.ServiceStatusObserver
import com.hifnawy.caffeinate.controller.SharedPrefsManager
import com.hifnawy.caffeinate.controller.SharedPrefsObserver
import com.hifnawy.caffeinate.databinding.ActivityMainBinding
import com.hifnawy.caffeinate.utils.ActivityExtensionFunctions.setActivityTheme
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toLocalizedFormattedTime
import com.hifnawy.caffeinate.utils.MutableListExtensionFunctions.addObserver
import com.hifnawy.caffeinate.utils.MutableListExtensionFunctions.removeObserver
import com.hifnawy.caffeinate.utils.ViewExtensionFunctions.isVisible
import com.hifnawy.caffeinate.utils.ViewExtensionFunctions.onSizeChange
import com.hifnawy.caffeinate.viewModel.MainActivityViewModel
import kotlin.math.abs
import kotlin.math.min
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
     * Lazily initializes an instance of [PictureInPictureActionsReceiver] for handling PiP actions.
     *
     * This property creates and caches a [PictureInPictureActionsReceiver] using the application's context.
     * The receiver is responsible for managing picture-in-picture mode actions within the application.
     *
     * @return [PictureInPictureActionsReceiver] the instance of the receiver for PiP actions.
     */
    private val pipActionReceiver by lazy { PictureInPictureActionsReceiver(caffeinateApplication) }

    /**
     * Provides a list of actions available in picture-in-picture mode.
     *
     * This property returns a list of [RemoteAction] objects that define
     * the actions available to the user when the application is in
     * picture-in-picture mode. The actions include restarting the service,
     * switching to the next timeout, and toggling the service state.
     *
     * @return [List] A list of [RemoteAction] objects representing PiP actions.
     */
    private val pipActions
        get() = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O -> emptyList()
            else                                          -> mutableListOf<RemoteAction>().apply {
                val toggleActionIcon = when (caffeinateApplication.lastStatusUpdate) {
                    is ServiceStatus.Stopped -> Icon.createWithResource(this@MainActivity, R.drawable.start_icon)
                    is ServiceStatus.Running -> Icon.createWithResource(this@MainActivity, R.drawable.stop_icon)
                }
                val toggleActionTitle = when (caffeinateApplication.lastStatusUpdate) {
                    is ServiceStatus.Stopped -> caffeinateApplication.localizedApplicationContext.getString(R.string.action_start_timeout)
                    is ServiceStatus.Running -> caffeinateApplication.localizedApplicationContext.getString(R.string.action_stop_timeout)
                }
                val toggleActionPendingIntent = PendingIntent.getBroadcast(
                        this@MainActivity,
                        1,
                        Intent(PiPAction.TOGGLE.name).apply { `package` = packageName },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val nextTimeoutActionIcon = Icon.createWithResource(this@MainActivity, R.drawable.next_icon)
                val nextTimeoutActionTitle = caffeinateApplication.localizedApplicationContext.getString(R.string.action_next_timeout)
                val nextTimeoutActionPendingIntent = PendingIntent.getBroadcast(
                        this@MainActivity,
                        2,
                        Intent(PiPAction.NEXT_TIMEOUT.name).apply { `package` = packageName },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val restartActionIcon = Icon.createWithResource(this@MainActivity, R.drawable.restart_icon)
                val restartActionTitle = caffeinateApplication.localizedApplicationContext.getString(R.string.action_restart_timeout)
                val restartActionPendingIntent = PendingIntent.getBroadcast(
                        this@MainActivity,
                        3,
                        Intent(PiPAction.RESTART.name).apply { `package` = packageName },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                add(RemoteAction(toggleActionIcon, toggleActionTitle, toggleActionTitle, toggleActionPendingIntent))
                add(RemoteAction(nextTimeoutActionIcon, nextTimeoutActionTitle, nextTimeoutActionTitle, nextTimeoutActionPendingIntent))

                if (caffeinateApplication.lastStatusUpdate is ServiceStatus.Stopped) return@apply

                add(0, RemoteAction(restartActionIcon, restartActionTitle, restartActionTitle, restartActionPendingIntent))
            }
        }

    /**
     * A lazily initialized instance of [PictureInPictureParams.Builder] that is used to enter picture-in-picture mode.
     *
     * This field is used to store the [PictureInPictureParams.Builder] that is used to enter picture-in-picture mode.
     * It is initialized lazily when it is first accessed. The instance is created using the
     * [PictureInPictureParams.Builder] constructor and the [Rational] constructor.
     * The aspect ratio of the builder is set to 1:1 to ensure that the activity is displayed in a square aspect ratio.
     *
     * @return [PictureInPictureParams.Builder] the instance of [PictureInPictureParams.Builder] that is used to enter picture-in-picture mode.
     */
    private val pipParamsBuilder by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@lazy null

        PictureInPictureParams.Builder().apply {
            pipSourceRectHint = Rect()
            binding.root.getGlobalVisibleRect(pipSourceRectHint)

            setSourceRectHint(pipSourceRectHint)
            setAspectRatio(Rational(1, 1))
            setActions(pipActions)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@apply
            setSeamlessResizeEnabled(false)
        }
    }

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
     * The [ActivityResultLauncher] used to launch the overlay permission intent.
     *
     * This launcher is used to launch the overlay permission intent when the user clicks on the
     * overlay permission card. The result of the launcher is not used in this activity.
     */
    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>

    /**
     * The source rectangle hint to use when entering picture-in-picture mode.
     *
     * This field is used to store the source rectangle hint that is passed to the
     * [PictureInPictureParams.Builder] when entering picture-in-picture mode.
     * It is used to specify the region of the screen that the activity is currently using.
     *
     * @see PictureInPictureParams.Builder.setSourceRectHint
     */
    private lateinit var pipSourceRectHint: Rect

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

        sharedPreferences.run { setActivityTheme(contrastLevel, theme.mode, isMaterialYouEnabled) }

        enableEdgeToEdge()
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

        with(binding) {
            pipActionReceiver.onActionClickListener = PictureInPictureActionsReceiver.OnActionClickListener { action ->
                Log.d("PiP Action Clicked, Action: $action")
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@OnActionClickListener

                pipParamsBuilder?.run {
                    setActions(pipActions)
                    setPictureInPictureParams(build())
                }
            }

            pipParamsBuilder?.run { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setPictureInPictureParams(build()) }

            with(appBar) {
                post {
                    viewModel.appBarVerticalOffset.observe(this@MainActivity) { verticalOffset ->
                        val params = layoutParams as CoordinatorLayout.LayoutParams
                        val behavior = params.behavior as AppBarLayout.Behavior
                        behavior.topAndBottomOffset = verticalOffset
                        requestLayout()
                    }
                }

                addOnOffsetChangedListener { appBarLayout, verticalOffset ->
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

            with(restartButton) {
                viewModel.isRestartButtonEnabled.observe(this@MainActivity) { isButtonEnabled ->
                    if (isButtonEnabled && isEnabled) return@observe
                    val animationDuration = 300L
                    val rotationFrom = if (isButtonEnabled) -360f else 360f
                    val rotationTo = if (isButtonEnabled) 360f else -360f
                    val scaleFrom = if (isButtonEnabled) 0f else 1f
                    val scaleTo = if (isButtonEnabled) 1f else 0f

                    rotation = rotationFrom
                    scaleX = scaleFrom
                    scaleY = scaleFrom

                    animate()
                        .rotation(rotationTo)
                        .scaleX(scaleTo)
                        .scaleY(scaleTo)
                        .setDuration(animationDuration)
                        .withStartAction {
                            if (isButtonEnabled && !isEnabled) {
                                isEnabled = true
                                isVisible = true
                            }
                        }.withEndAction {
                            if (!isButtonEnabled && isEnabled) {
                                isEnabled = false
                                isVisible = false
                            }
                        }
                        .start()
                }
            }

            addOnPictureInPictureModeChangedListener { pipModeInfo ->
                coordinatorLayout.isVisible = !pipModeInfo.isInPictureInPictureMode
                pipLayout.root.isVisible = pipModeInfo.isInPictureInPictureMode

                pipActionReceiver.isRegistered = pipModeInfo.isInPictureInPictureMode

                pipParamsBuilder?.run {
                    setActions(pipActions)
                    setPictureInPictureParams(build())
                }

                updatePictureInPictureView(caffeinateApplication.lastStatusUpdate)
            }

            root.onSizeChange { _, newWidth, newHeight, _, _ ->
                Log.d("root layout size changed, newWidth: $newWidth, newHeight: $newHeight")

                with(pipLayout.progressIndicator) {
                    indicatorSize = min(newWidth, newHeight) - paddingStart - paddingEnd
                    indicatorsTrackThickness = min(paddingStart, paddingEnd) / 2
                }

                pipParamsBuilder?.run {
                    val length = min(newWidth, newHeight) / 2
                    pipSourceRectHint = Rect(0, 0, length, length)

                    setAspectRatio(Rational(1, 1))
                    setSourceRectHint(pipSourceRectHint)
                    setPictureInPictureParams(build())
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
            coordinatorLayout.isVisible = !isInPictureInPictureMode
            pipLayout.root.isVisible = isInPictureInPictureMode

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

        if (sharedPreferences.isPictureInPictureEnabled) return

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
        binding.root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

        when (item.itemId) {
            R.id.about -> AboutBottomSheetFragment.newInstance.show(supportFragmentManager, "about")
        }

        return when (item.itemId) {
            in listOf(R.id.about) -> true
            else                  -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Called when the user is giving a hint that they are leaving your activity.
     *
     * This is called when the user presses the home button, or when the user starts navigating away from your activity.
     * This is used by the system to determine whether to enter picture-in-picture mode.
     *
     * @see [onPictureInPictureModeChanged]
     * @see [isInPictureInPictureMode]
     * @see [enterPictureInPictureMode]
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!sharedPreferences.isPictureInPictureEnabled || caffeinateApplication.lastStatusUpdate !is ServiceStatus.Running) return

        pipParamsBuilder?.run { enterPictureInPictureMode(build()) }
    }

    /**
     * Called when the picture in picture mode's UI state has changed.
     *
     * @param pipState [PictureInPictureUiState] The current state of the picture in picture mode's UI.
     *
     * @see PictureInPictureUiState
     */
    override fun onPictureInPictureUiStateChanged(pipState: PictureInPictureUiState) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        if (!pipState.isTransitioningToPip) return

        binding.coordinatorLayout.isVisible = false
        binding.pipLayout.root.isVisible = true
    }

    /**
     * Called when there is a change in the permission state indicating whether all necessary permissions have been granted.
     *
     * @param isAllPermissionsGranted [Boolean] `true` if all necessary permissions have been granted, `false` otherwise.
     */
    override fun onIsAllPermissionsGrantedUpdated(isAllPermissionsGranted: Boolean) {
        binding.toggleButton.isEnabled = isAllPermissionsGranted

        changeOverlayPreferences(isAllPermissionsGranted && checkOverlayPermission())
        changePictureInPicturePreferences(isAllPermissionsGranted)
        changeDimmingPreferences(isAllPermissionsGranted)
        changeWhileLockedPreferences(isAllPermissionsGranted)
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
     * Called when the "Picture in Picture Enabled" preference changes.
     *
     * @param isPictureInPictureEnabled [Boolean] `true` if the "Picture in Picture" feature is enabled, `false` otherwise.
     */
    override fun onIsPictureInPictureEnabledUpdated(isPictureInPictureEnabled: Boolean) {
        binding.pictureInPictureSwitch.isChecked = isPictureInPictureEnabled
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

            val autoEnterPiP = status is ServiceStatus.Running && sharedPreferences.isPictureInPictureEnabled
            pipParamsBuilder?.run {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@run

                setAutoEnterEnabled(autoEnterPiP)
                setPictureInPictureParams(build())
            }

            viewModel.isRestartButtonEnabled.value =
                    (status as? ServiceStatus.Running)?.run { (!isRestarted && isCountingDown) || isRestarted } ?: false

            val remainingText = when (status) {
                is ServiceStatus.Stopped -> getString(R.string.caffeinate_button_off)
                is ServiceStatus.Running -> status.remaining.toLocalizedFormattedTime(root.context)
            }

            toggleButton.text = remainingText

            if (!isInPictureInPictureMode) return

            pipParamsBuilder?.run {
                setActions(pipActions)
                setPictureInPictureParams(build())
            }
            updatePictureInPictureView(status, remainingText)
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
     * 4. A [MaterialSwitch][com.google.android.material.materialswitch.MaterialSwitch] that toggles the Material You preferences..
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

                materialYouSubTextTextView.isEnabled = isChecked
                materialYouSubTextTextView.isVisible = isChecked

                sharedPreferences.run { changeThemeAndContrast(theme, contrastLevel, isChecked) }
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
     * @param isEnabled [Boolean] `true` if the overlay preferences should be enabled, `false` otherwise.
     */
    private fun changeOverlayPreferences(isEnabled: Boolean) {
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
     * Enables the picture in picture preferences.
     *
     * The picture in picture preferences are:
     * 1. A [MaterialCardView][com.google.android.material.card.MaterialCardView] that shows the picture in picture preferences.
     * 2. A [TextView][android.widget.TextView] that shows the picture in picture preferences title.
     * 3. A [TextView][android.widget.TextView] that shows the picture in picture preferences subtitle.
     * 4. A [MaterialSwitch][com.google.android.material.materialswitch.MaterialSwitch] that toggles the picture in picture preferences.
     *
     * @param isEnabled [Boolean] `true` if the picture in picture preferences should be enabled, `false` otherwise.
     */
    private fun changePictureInPicturePreferences(isEnabled: Boolean) {
        with(binding) {
            pictureInPictureCard.isEnabled = isEnabled
            pictureInPictureTextView.isEnabled = isEnabled
            pictureInPictureSubTextTextView.isEnabled = isEnabled && sharedPreferences.isPictureInPictureEnabled
            pictureInPictureSubTextTextView.isVisible = isEnabled && sharedPreferences.isPictureInPictureEnabled
            pictureInPictureSwitch.isEnabled = isEnabled
            pictureInPictureSwitch.isChecked = sharedPreferences.isPictureInPictureEnabled

            pictureInPictureCard.setOnClickListener { pictureInPictureSwitch.isChecked = !sharedPreferences.isPictureInPictureEnabled }
            pictureInPictureSwitch.setOnCheckedChangeListener { switch, isChecked ->
                switch.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                pictureInPictureSubTextTextView.isEnabled = isChecked
                pictureInPictureSubTextTextView.isVisible = isChecked
                sharedPreferences.isPictureInPictureEnabled = isChecked
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
     * @param isEnabled [Boolean] `true` if the allow dimming preferences should be enabled, `false` otherwise.
     */
    private fun changeDimmingPreferences(isEnabled: Boolean) {
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
     * @param isEnabled [Boolean] `true` if the allow while locked preferences should be enabled, `false` otherwise.
     */
    private fun changeWhileLockedPreferences(isEnabled: Boolean) {
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
     * @param isEnabled [Boolean] `true` if all required permissions are granted, `false` otherwise.
     */
    private fun changeTimeoutsPreferences(isEnabled: Boolean) {
        with(binding) {
            val timeoutChoiceClickListener = View.OnClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                TimeoutsSelectionFragment.getInstance(caffeinateApplication) { checkBoxItems ->
                    caffeinateApplication.run {
                        timeoutCheckBoxes.clear()

                        checkBoxItems.forEach { checkBoxItem -> timeoutCheckBoxes.add(checkBoxItem.copy()) }

                        timeoutChoiceSubTextTextView.text = timeoutCheckBoxes.enabledDurations

                        checkBoxItems.find { checkBoxItem -> checkBoxItem.duration == timeout && !checkBoxItem.isChecked }?.let {
                            when (lastStatusUpdate) {
                                is ServiceStatus.Running -> KeepAwakeService.startNextTimeout(this, debounce = false)
                                else                     -> timeout = checkBoxItems.first { checkBoxItem -> checkBoxItem.isChecked }.duration
                            }
                        } ?: when (lastStatusUpdate) {
                            is ServiceStatus.Stopped -> timeout = checkBoxItems.first { checkBoxItem -> checkBoxItem.isChecked }.duration
                            else                     -> Unit // do nothing if the service is running
                        }

                        sharedPreferences.timeoutCheckBoxes = timeoutCheckBoxes
                    }
                }.show(supportFragmentManager, "about")
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
     * Updates the UI of the picture-in-picture view according to the given service status.
     *
     * @param status [ServiceStatus] The current service status.
     * @param remainingString [String] The formatted remaining time string.
     */
    private fun updatePictureInPictureView(status: ServiceStatus, remainingString: String? = null) = with(binding) {
        val remainingText = remainingString ?: when (status) {
            is ServiceStatus.Stopped -> getString(R.string.caffeinate_button_off)
            is ServiceStatus.Running -> status.remaining.toLocalizedFormattedTime(root.context)
        }

        pipLayout.progressIndicator.text = remainingText

        pipLayout.progressIndicator.hoursIndicatorMax = when (status) {
            is ServiceStatus.Stopped -> 0
            is ServiceStatus.Running -> status.startTimeout.toComponents { hours, _, _, _ -> hours }.toInt()
        }

        pipLayout.progressIndicator.progress = when {
            status is ServiceStatus.Stopped                                                 -> 0.seconds

            status is ServiceStatus.Running && status.remaining.isInfinite() ||
            status is ServiceStatus.Running && !status.isRestarted && status.isCountingDown -> status.remaining

            else                                                                            -> 0.seconds
        }
    }
}