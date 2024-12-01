package com.hifnawy.caffeinate.controller

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.controller.KeepAwakeService.Companion.KeepAwakeServiceAction.ACTION_CHANGE_DIMMING_ENABLED
import com.hifnawy.caffeinate.controller.KeepAwakeService.Companion.KeepAwakeServiceAction.ACTION_NEXT_TIMEOUT
import com.hifnawy.caffeinate.controller.KeepAwakeService.Companion.KeepAwakeServiceAction.ACTION_RESTART
import com.hifnawy.caffeinate.controller.KeepAwakeService.Companion.KeepAwakeServiceAction.ACTION_START
import com.hifnawy.caffeinate.controller.KeepAwakeService.Companion.KeepAwakeServiceAction.ACTION_START_DELAYED
import com.hifnawy.caffeinate.controller.KeepAwakeService.Companion.KeepAwakeServiceAction.ACTION_STOP
import com.hifnawy.caffeinate.controller.KeepAwakeService.Companion.KeepAwakeServiceState.STATE_START
import com.hifnawy.caffeinate.controller.KeepAwakeService.Companion.KeepAwakeServiceState.STATE_START_DELAYED
import com.hifnawy.caffeinate.controller.KeepAwakeService.Companion.KeepAwakeServiceState.STATE_STOP
import com.hifnawy.caffeinate.controller.KeepAwakeService.Companion.KeepAwakeServiceState.STATE_TOGGLE
import com.hifnawy.caffeinate.controller.KeepAwakeService.Companion.NotificationActionRequestCode.REQUEST_CODE_NEXT_TIMEOUT
import com.hifnawy.caffeinate.controller.KeepAwakeService.Companion.NotificationActionRequestCode.REQUEST_CODE_RESTART_TIMEOUT
import com.hifnawy.caffeinate.controller.KeepAwakeService.Companion.NotificationActionRequestCode.REQUEST_CODE_TOGGLE_DIMMING
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toFormattedTime
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toLocalizedFormattedTime
import com.hifnawy.caffeinate.utils.MutableListExtensionFunctions.addObserver
import com.hifnawy.caffeinate.utils.MutableListExtensionFunctions.removeObserver
import com.hifnawy.caffeinate.utils.NotificationUtils
import com.hifnawy.caffeinate.utils.WakeLockExtensionFunctions.releaseSafely
import com.hifnawy.caffeinate.view.OverlayHandler
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import timber.log.Timber as Log

/**
 * A [Service] that keeps the device awake by managing wake locks and notifications.
 *
 * This service is responsible for maintaining a wake lock to prevent the device from going to sleep
 * while the Caffeinate application is active. It manages the foreground notification to ensure the
 * service is running in the foreground, complying with Android's background execution limits.
 *
 * The service observes changes in shared preferences and updates its behavior accordingly, including
 * responding to changes in the application's status and locale.
 *
 * @throws IllegalStateException if the application state is not properly initialized.
 *
 * @author AbdAlMoniem AlHifnawy
 *
 * @see Service
 * @see SharedPrefsManager
 * @see ServiceStatusObserver
 * @see LocaleChangeReceiver
 */
class KeepAwakeService : Service(), SharedPrefsObserver, ServiceStatusObserver {

    /**
     * A lazy delegate that provides a reference to the [CaffeinateApplication] instance that owns this service.
     *
     * This delegate is used to access properties and methods of the owning application, such as the
     * [CaffeinateApplication.sharedPreferences] and [CaffeinateApplication.applicationLocale].
     *
     * @return [CaffeinateApplication] the owning application instance.
     *
     * @see CaffeinateApplication
     */
    private val caffeinateApplication by lazy { application as CaffeinateApplication }

    /**
     * A lazy delegate that provides a reference to the [OverlayHandler] instance that is used
     * by this service to handle overlay-related operations.
     *
     * The [OverlayHandler] is responsible for managing the overlay that is displayed when the
     * service is running in the foreground. It provides methods to show, hide and update the
     * overlay, and is used by this service to update the overlay when the service's state changes.
     *
     * @return [OverlayHandler] the overlay handler instance.
     *
     * @see OverlayHandler
     */
    private val overlayHandler by lazy { OverlayHandler(caffeinateApplication) }

    /**
     * A lazy delegate that provides a reference to the [NotificationManager] instance that handles notifications
     * for this service.
     *
     * This delegate is used to access the notification manager's methods, such as [NotificationManager.notify] and
     * [NotificationManager.cancel].
     *
     * @return [NotificationManager] the notification manager instance.
     *
     * @see NotificationManager
     */
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    /**
     * A lazy delegate that provides an instance of [SharedPrefsManager] for managing shared preferences.
     *
     * This delegate is used to access and modify the application's shared preferences, allowing the service
     * to respond to changes in settings such as theme, permissions, and other user preferences.
     *
     * @return [SharedPrefsManager] the instance for handling shared preferences.
     *
     * @see SharedPrefsManager
     */
    private val sharedPreferences by lazy { SharedPrefsManager(caffeinateApplication) }

    /**
     * A lazy delegate that provides a reference to the [LocaleChangeReceiver] instance that handles Locale changes.
     *
     * This delegate is used to register and unregister a [LocaleChangeReceiver] instance with the system,
     * allowing the service to respond to changes in the system locale.
     *
     * @return [LocaleChangeReceiver] the instance handling Locale changes.
     *
     * @see LocaleChangeReceiver
     */
    private val localeChangeReceiver by lazy { LocaleChangeReceiver(caffeinateApplication) }

    /**
     * A lazy delegate that provides a reference to the [ScreenLockReceiver] instance that handles screen lock events.
     *
     * This delegate is used to register and unregister a [ScreenLockReceiver] instance with the system,
     * allowing the service to respond to changes in the device's screen state.
     *
     * @return [ScreenLockReceiver] the instance handling screen lock events.
     *
     * @see ScreenLockReceiver
     */
    private val screenLockReceiver by lazy { ScreenLockReceiver(caffeinateApplication) }

    /**
     * The frequency at which the notification should be updated.
     *
     * @see [Duration]
     * @see [buildForegroundNotification]
     * @see [onServiceStatusUpdated]
     */
    private val notificationPeriod by lazy { 1.seconds }

    /**
     * The time source used to measure the time elapsed since the last notification update.
     *
     * @see [TimeSource]
     * @see [buildForegroundNotification]
     * @see [onServiceStatusUpdated]
     */
    private val notificationTimeMarker by lazy { TimeSource.Monotonic }

    /**
     * A lazy delegate that provides the notification channel ID for the service's notifications.
     *
     * This delegate initializes the notification channel ID using the application's localized name,
     * which is used to create and manage notification channels.
     *
     * @return [String] the notification channel ID derived from the application's name.
     *
     * @see NotificationChannel
     */
    private val notificationChannelID by lazy { caffeinateApplication.localizedApplicationContext.getString(R.string.app_name) }

    /**
     * A lazy delegate that provides a reference to the [NotificationChannel] instance that represents the service's notification channel.
     *
     * This delegate is used to access the notification channel's properties and methods, allowing the service
     * to create and manage its notifications.
     *
     * The notification channel is created when the service is initialized and is used to specify the
     * notification channel's name, description, and importance level.
     *
     * @return [NotificationChannel] the notification channel instance.
     *
     * @see NotificationChannel
     */
    private val notificationChannel by lazy {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> notificationManager.getNotificationChannel(notificationChannelID)
                                                              ?: NotificationChannel(
                                                                      notificationChannelID,
                                                                      notificationChannelID,
                                                                      NotificationManager.IMPORTANCE_HIGH
                                                              ).also { channel -> notificationManager.createNotificationChannel(channel) }

            else                                           -> null
        }
    }

    /**
     * The time of the previous notification.
     *
     * @see [Duration]
     * @see [buildForegroundNotification]
     * @see [onServiceStatusUpdated]
     * @see [notificationPeriod]
     */
    private var previousNotificationTime = notificationTimeMarker.markNow()

    /**
     * A flag indicating whether the screen overlay is enabled.
     *
     * This flag is used to track whether the screen overlay is currently enabled.
     * The screen overlay is a floating window that is used to keep the screen awake.
     * The overlay is enabled when the user requests to keep the screen awake, and
     * disabled when the user requests to stop keeping the screen awake.
     *
     * This flag is initially set to the value that is stored in the shared preferences.
     * The value of this flag is updated whenever the preference is changed by the user.
     *
     */
    private var isOverlayEnabled = false
        get() = field && Settings.canDrawOverlays(this)

    /**
     * A flag indicating whether the screen should be dimmed while it is being kept awake.
     *
     * This flag is used to determine whether the screen should be dimmed while it is being kept awake.
     * When the flag is set to `true`, the screen is dimmed to a level that is specified by the system setting.
     * When the flag is set to `false`, the screen is not dimmed.
     *
     * This flag is initially set to the value that is stored in the shared preferences.
     * The value of this flag is updated whenever the preference is changed by the user.
     */
    private var isDimmingEnabled = false

    /**
     * A flag indicating whether the screen should be kept awake while the device is locked.
     *
     * This flag is used to determine whether the screen should be kept awake while the device is locked.
     * When the flag is set to `true`, the service will keep running while the device is locked.
     * When the flag is set to `false`, the service will stop running while the device is locked.
     *
     * This flag is initially set to the value that is stored in the shared preferences.
     * The value of this flag is updated whenever the preference is changed by the user.
     */
    private var isWhileLockedEnabled = false

    /**
     * A [PowerManager.WakeLock] that is used to keep the device awake while this service is running.
     *
     * This wake lock is used to prevent the device from going to sleep while the service is running.
     * It is acquired when the service is started and released when the service is stopped.
     *
     * @see prepareService
     * @see stopService
     */
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * A [TimeoutJob] that manages the timing of the caffeine session.
     *
     * This job is responsible for handling the countdown and updating the service status
     * as the timeout duration progresses. It is created when a caffeine session starts
     * and is cancelled when the session stops.
     *
     * @see startCaffeine
     * @see stopCaffeine
     */
    private val caffeineTimeoutJob by lazy { TimeoutJob(caffeinateApplication) }

    /**
     * This method is called when a client is binding to the service with bindService().
     * Currently, this service does not support binding, so it returns null.
     *
     * @param intent [Intent] The Intent that was used to bind to this service.
     *
     * @return [IBinder] `null` as this service does not support binding.
     */
    override fun onBind(intent: Intent): IBinder? = null

    /**
     * Called by the system when the service is first created. Do not call this method directly.
     *
     * For backwards compatibility, the default implementation calls [onStart] and returns either
     * [START_STICKY][Service.START_STICKY] or [START_STICKY_COMPATIBILITY][Service.START_STICKY_COMPATIBILITY].
     *
     * If you need your application to run on platform versions prior to API level 5, you can
     * override this method to handle the older [Intent] parameter. Or, if you can target API level 5
     * or later, you can override [onStartCommand], which is the preferred implementation.
     *
     * All implementations must be thread-safe.
     *
     * @param intent [Intent] The [Intent] supplied to [prepareService], as given.
     * This may be `null` if the service is being restarted after its process has gone away, and
     * it had previously returned anything except [START_STICKY_COMPATIBILITY][Service.START_STICKY_COMPATIBILITY].
     * @param flags [Int] Additional data about this start request.
     * @param startId [Int] A unique integer representing this specific request to start.
     *
     * @return [Int] The result of the call described above. This must be one of the following
     * constants:
     * - [START_STICKY_COMPATIBILITY][Service.START_STICKY_COMPATIBILITY]: The service is not explicitly restarted if it is killed.
     * - [START_STICKY][Service.START_STICKY]: The service is restarted with a null intent if it is killed.
     * - [START_NOT_STICKY][Service.START_NOT_STICKY]: The service is not restarted after being killed.
     * - [START_REDELIVER_INTENT][Service.START_REDELIVER_INTENT]: The service is restarted with the original intent if it is killed.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildForegroundNotification(caffeinateApplication.lastStatusUpdate))

        Log.d("onStartCommand, lastStatusUpdate: ${caffeinateApplication.lastStatusUpdate}")

        intent ?: return START_NOT_STICKY
        val keepAwakeServiceAction = intent.action?.let { action -> KeepAwakeServiceAction.valueOfOrNull(action) } ?: let {
            Log.wtf("intent.action: ${intent.action} cannot be parsed to ${KeepAwakeServiceAction::class.qualifiedName}!")
            ACTION_STOP
        }

        Log.d("serviceAction: $keepAwakeServiceAction")

        with(sharedPreferences) {
            this@KeepAwakeService.isOverlayEnabled = isOverlayEnabled
            this@KeepAwakeService.isDimmingEnabled = isDimmingEnabled
            this@KeepAwakeService.isWhileLockedEnabled = isWhileLockedEnabled
        }
        val restartDuration = when (val lastTimeout = intent.getLongExtra(Intent.EXTRA_INTENT, -1L)) {
            -1L  -> null
            else -> lastTimeout.seconds
        }
        when (keepAwakeServiceAction) {
            ACTION_START                  -> prepareService()
            ACTION_START_DELAYED          -> prepareService(DEBOUNCE_DURATION)
            ACTION_RESTART                -> restart(caffeinateApplication, restartDuration)
            ACTION_STOP                   -> stopCaffeine()
            ACTION_NEXT_TIMEOUT           -> startNextTimeout(caffeinateApplication, debounce = false)
            ACTION_CHANGE_DIMMING_ENABLED -> sharedPreferences.isDimmingEnabled = !sharedPreferences.isDimmingEnabled
        }

        return START_NOT_STICKY
    }

    /**
     * Called when the service is removed from the task manager.
     *
     * This method is triggered when the user swipes away the app from the recent apps list.
     * It is responsible for cleaning up resources and stopping the caffeine session.
     *
     *
     * > TODO: Fix service stopping when overlay is shown and the app is swiped away
     *         from the recent apps list on some chinese devices.
     *
     * > On some chinese devices, the service is stopped when the user swipes away the app
     *   from the recent apps list. but the service is not stopped gracefully, so we need
     *   to stop the service manually. This also seems to only happen when the overlay is
     *   shown; but if it is not, the service is not stopped for some reason.
     *
     * > For now there is no way around this limitation except checking the device model
     *   and gracefully stopping the service only if the overlay is shown. We use the
     *   isOverlayEnabled flag from the shared preferences to check if the overlay was
     *   shown or not before the service got killed, since the isOverlayEnabled flag will
     *   be reset after the service instance is killed.
     *
     * > For now we can restart the service manually when the user swipes away the app
     *   from the recent apps list passing the last remaining timeout to the service.
     *   This is a crude solution, and may put a strain on the device's storage since
     *   it will store the last remaining timeout in the shared preferences every time
     *   the service status is updated. which is every one second.
     *
     *
     * @param rootIntent [Intent?] The root Intent that was used to launch the task.
     * This may be `null` if the task was removed via the task manager.
     *
     * @see Service.onTaskRemoved
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        Log.d("${this::class.simpleName} service removed from task manager! restarting...")

        Intent(this, KeepAwakeService::class.java).apply {
            action = ACTION_RESTART.name
            putExtra(Intent.EXTRA_INTENT, sharedPreferences.lastRemainingTimeout)
            startService(this)
        }
    }

    /**
     * Called when the "Overlay Enabled" preference changes.
     *
     * @param isOverlayEnabled [Boolean] `true` if the overlay is enabled, `false` otherwise.
     */
    override fun onIsOverlayEnabledUpdated(isOverlayEnabled: Boolean) {
        Log.d("isOverlayEnabled: $isOverlayEnabled")

        this.isOverlayEnabled = isOverlayEnabled

        when (caffeinateApplication.lastStatusUpdate) {
            is ServiceStatus.Running -> if (isOverlayEnabled) overlayHandler.showOverlay() else overlayHandler.hideOverlay()
            is ServiceStatus.Stopped -> Unit
        }
    }

    /**
     * Called when the "Dimming Enabled" preference changes.
     *
     * @param isDimmingEnabled [Boolean] `true` if dimming is enabled, `false` otherwise.
     */
    override fun onIsDimmingEnabledUpdated(isDimmingEnabled: Boolean) {
        Log.d("isDimmingEnabled: $isDimmingEnabled")

        when (val status = caffeinateApplication.lastStatusUpdate) {
            is ServiceStatus.Running -> {
                this.isDimmingEnabled = isDimmingEnabled
                acquireWakeLock(status.remaining)
                onServiceStatusUpdated(status)
            }

            is ServiceStatus.Stopped -> Unit
        }
    }

    /**
     * Called when the "While Locked Enabled" preference changes.
     *
     * @param isWhileLockedEnabled [Boolean] `true` if the "While Locked" feature is enabled, `false` otherwise.
     */
    override fun onIsWhileLockedEnabledUpdated(isWhileLockedEnabled: Boolean) {
        Log.d("isWhileLockedEnabled: $isWhileLockedEnabled")

        screenLockReceiver.isRegistered = !isWhileLockedEnabled
    }

    /**
     * Updates the service based on the provided status.
     *
     * This method is called whenever the status of the service changes.
     * Depending on the status, it either updates the foreground notification
     * with the remaining time or stops the foreground service.
     *
     * @param status [ServiceStatus] the new status of the service
     */
    override fun onServiceStatusUpdated(status: ServiceStatus) {
        Log.d("Status Changed: $status")

        when (status) {
            is ServiceStatus.Running -> {
                if (isOverlayEnabled) overlayHandler.overlayText =
                        status.remaining.toLocalizedFormattedTime(caffeinateApplication.localizedApplicationContext)

                if (previousNotificationTime.elapsedNow() >= notificationPeriod) {
                    notificationManager.notify(NOTIFICATION_ID, buildForegroundNotification(status))

                    previousNotificationTime = notificationTimeMarker.markNow()
                }
            }

            is ServiceStatus.Stopped -> stopForeground(STOP_FOREGROUND_REMOVE)
        }
        /* TODO: Remove or find a way to infer the service status without using shared preferences
         *       because this will put a strain on the device's storage since it will store
         *       the last remaining timeout in the shared preferences every time the service
         *       status is updated. which is every one second.
         */
        sharedPreferences.isServiceRunning = status is ServiceStatus.Running
    }

    /**
     * Prepares the foreground service.
     *
     * This method is called when the start intent is received.
     * It acquires a wake lock and starts the foreground service
     * with the notification that displays the remaining time.
     *
     * @param startAfter [Duration] the duration to delay before starting the service
     *
     * @see [startForeground]
     * @see [acquireWakeLock]
     */
    private fun prepareService(startAfter: Duration? = null) = caffeinateApplication.run {
        Log.d("starting ${this@KeepAwakeService::class.simpleName} service...")
        val status = when (val status = lastStatusUpdate) {
            is ServiceStatus.Running -> status.apply { remaining = timeout }
            is ServiceStatus.Stopped -> ServiceStatus.Running(timeout)
        }

        lastStatusUpdate = status

        Log.d("status: $status, selectedDuration: ${status.remaining.toFormattedTime()}")

        Log.d("sending foreground notification...")
        startForeground(NOTIFICATION_ID, buildForegroundNotification(status))
        Log.d("foreground notification sent!")

        keepAwakeServiceObservers.addObserver(this@KeepAwakeService)
        sharedPrefsObservers.addObserver(this@KeepAwakeService)

        localeChangeReceiver.isRegistered = true
        onIsWhileLockedEnabledUpdated(isWhileLockedEnabled)

        startCaffeine(status.remaining, startAfter)

        Log.d("${this@KeepAwakeService::class.simpleName} service started!")
    }

    /**
     * Builds a [Notification] for the foreground service based on the provided [ServiceStatus].
     *
     * This method is used to build the foreground notification that is displayed when the service is running.
     * It takes a [ServiceStatus] object as a parameter, which contains information about the current status
     * of the service, such as whether it is running or stopped and the remaining time until the screen
     * turns off.
     *
     * The method returns a [Notification] object that is built using a [NotificationCompat.Builder].
     * The notification has a title and a content text that are set based on the provided
     * [ServiceStatus] object. It also has an action that is used to stop the service when the user
     * clicks on the notification.
     *
     * @param status [ServiceStatus] the current status of the service
     *
     * @return [Notification] the built notification
     *
     * @see [ServiceStatus]
     * @see [NotificationCompat.Builder]
     * @see [Notification]
     */
    private fun buildForegroundNotification(status: ServiceStatus): Notification = caffeinateApplication.run {
        val stopActionIntent =
                NotificationUtils.getPendingIntent(localizedApplicationContext, KeepAwakeService::class.java, ACTION_STOP.name, 0)
        val nextTimeoutActionTitle = localizedApplicationContext.getString(R.string.action_next_timeout)
        val dimmingEnabledActionTitle = localizedApplicationContext.getString(R.string.action_disable_dimming)
        val dimmingDisabledActionTitle = localizedApplicationContext.getString(R.string.action_enable_dimming)
        val nextTimeoutAction = NotificationUtils.getNotificationAction(
                localizedApplicationContext,
                KeepAwakeService::class.java,
                ACTION_NEXT_TIMEOUT.name,
                R.drawable.coffee_icon_on,
                nextTimeoutActionTitle,
                REQUEST_CODE_NEXT_TIMEOUT.ordinal
        )
        val restartTimeoutAction = when {
            status.run { this is ServiceStatus.Running && !isIndefinite } -> NotificationUtils.getNotificationAction(
                    this,
                    KeepAwakeService::class.java,
                    ACTION_RESTART.name,
                    R.drawable.coffee_icon_on,
                    getString(R.string.action_restart_timeout),
                    REQUEST_CODE_RESTART_TIMEOUT.ordinal
            )

            else                                                          -> null
        }
        val toggleDimmingAction = NotificationUtils.getNotificationAction(
                this,
                KeepAwakeService::class.java,
                ACTION_CHANGE_DIMMING_ENABLED.name,
                R.drawable.coffee_icon_on,
                if (isDimmingEnabled) dimmingEnabledActionTitle else dimmingDisabledActionTitle,
                REQUEST_CODE_TOGGLE_DIMMING.ordinal
        )
        val notificationBuilder = NotificationCompat.Builder(localizedApplicationContext, notificationChannelID)
        var durationStr: String? = null
        var contentTitle: String? = null

        if (status is ServiceStatus.Running) {
            durationStr = status.remaining.toLocalizedFormattedTime(localizedApplicationContext)
            contentTitle = when (status.remaining) {
                Duration.INFINITE -> localizedApplicationContext.getString(R.string.notification_title_indefinite_duration)
                else              -> localizedApplicationContext.getString(R.string.notification_title_definite_duration, durationStr)
            }
        }

        notificationBuilder
            .setSilent(true)
            .setOngoing(true)
            .setAutoCancel(true)
            .setSubText(durationStr)
            .setContentTitle(contentTitle)
            .setContentIntent(stopActionIntent)
            .setSmallIcon(R.drawable.coffee_icon_on)
            .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
            .addAction(nextTimeoutAction)
            .addAction(restartTimeoutAction)
            .addAction(toggleDimmingAction)
            .setContentInfo(localizedApplicationContext.getString(R.string.app_name))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentText(localizedApplicationContext.getString(R.string.stop_notification_action_title))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notificationChannel?.let { channel -> notificationBuilder.setChannelId(channel.id) }

        return notificationBuilder.build()
    }

    /**
     * Acquires a wake lock with the specified duration.
     *
     * This method is used to acquire a wake lock that prevents the device from going to sleep. It uses the [PowerManager] to
     * acquire the wake lock, and logs information about the wake lock acquisition.
     *
     * The method takes a [Duration] object as a parameter, which specifies the duration of the wake lock. If the duration is
     * [Duration.INFINITE], the wake lock will be acquired indefinitely, and the method will not automatically release the
     * wake lock. Otherwise, the wake lock will be acquired for the specified duration, and the method will automatically release
     * the wake lock after the specified duration has elapsed.
     *
     * The method also takes into account the [isDimmingEnabled] preference, and acquires a wake lock with the appropriate level
     * of screen brightness. If [isDimmingEnabled] is `true`, the method acquires a wake lock with [PowerManager.SCREEN_DIM_WAKE_LOCK],
     * which dims the screen but keeps it on. Otherwise, the method acquires a wake lock with [PowerManager.SCREEN_BRIGHT_WAKE_LOCK],
     * which keeps the screen at full brightness.
     */
    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock(duration: Duration) {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        wakeLock?.apply {
            Log.d("releasing ${this@KeepAwakeService::wakeLock.name}...")
            releaseSafely(::wakeLock.name)
            Log.d("${this@KeepAwakeService::wakeLock.name} released!")
        } ?: Log.d("wakeLock is not held!")
        @Suppress("DEPRECATION")
        val wakeLockLevel = when {
            isDimmingEnabled -> {
                Log.d("using ${PowerManager::SCREEN_DIM_WAKE_LOCK.name}")
                PowerManager.SCREEN_DIM_WAKE_LOCK
            }

            else             -> {
                Log.d("using ${PowerManager::SCREEN_BRIGHT_WAKE_LOCK.name}")
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK
            }
        } or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE

        wakeLock = powerManager.newWakeLock(
                wakeLockLevel,
                "${caffeinateApplication.localizedApplicationContext.getString(R.string.app_name)}:wakeLockTag"
        ).apply {
            Log.d("acquiring ${this@KeepAwakeService::wakeLock.name}, isDimmingEnabled: $isDimmingEnabled...")
            when {
                duration.isInfinite() -> acquire()
                else                  -> acquire(duration.inWholeMilliseconds)
            }
            Log.d("${this@KeepAwakeService::wakeLock.name} acquired, isDimmingEnabled: $isDimmingEnabled!")
        }
    }

    /**
     * Starts the caffeine timeout with the specified [duration].
     *
     * This method starts the caffeine timeout with the specified [duration]. If the duration is
     * indefinite, the method acquires a wake lock with the appropriate level of screen brightness
     * and keeps the screen on indefinitely. Otherwise, the method acquires a wake lock with the
     * specified [duration], and the screen will be kept on for the specified duration.
     *
     * @param duration [Duration] the duration of the wake lock.
     * @param startAfter [Duration] the duration to delay before starting the service
     */
    private fun startCaffeine(duration: Duration, startAfter: Duration? = null) = caffeinateApplication.run {
        Log.d("starting ${localizedApplicationContext.getString(R.string.app_name)} with duration: ${duration.toFormattedTime()}, isIndefinite: ${duration.isInfinite()}")

        acquireWakeLock(duration)

        if (isOverlayEnabled) overlayHandler.showOverlay()

        caffeineTimeoutJob.apply {
            Log.d("stopping ${this@KeepAwakeService::caffeineTimeoutJob.name}...")
            stop()
            Log.d("${this@KeepAwakeService::caffeineTimeoutJob.name} stopped!")
        }

        Log.d("starting ${this@KeepAwakeService::caffeineTimeoutJob.name}...")
        caffeineTimeoutJob.start(duration, startAfter)
        Log.d("${this@KeepAwakeService::caffeineTimeoutJob.name} started!")

        Log.d("${localizedApplicationContext.getString(R.string.app_name)} started!")

        sharedPreferences.isServiceRunning = true
    }

    /**
     * Stops the caffeine timeout and releases the wake lock.
     *
     * This method stops the caffeine timeout and releases the wake lock if it is held. If the wake lock is not held, the method does nothing.
     *
     * This method is called by the [onDestroy] method when the service is being stopped
     *
     * @see [onDestroy]
     * @see [startCaffeine]
     */
    private fun stopCaffeine() = caffeinateApplication.run {
        Log.d("stopping ${localizedApplicationContext.getString(R.string.app_name)}...")

        wakeLock?.apply {
            Log.d("releasing ${this@KeepAwakeService::wakeLock.name}...")
            releaseSafely(::wakeLock.name)
            Log.d("${this@KeepAwakeService::wakeLock.name} released!")
        } ?: Log.d("wakeLock is not held!")

        caffeineTimeoutJob.apply {
            Log.d("stopping ${this@KeepAwakeService::caffeineTimeoutJob.name}...")
            stop()
            Log.d("${this@KeepAwakeService::caffeineTimeoutJob.name} stopped!")

            Log.d("cancelling ${this@KeepAwakeService::caffeineTimeoutJob.name}...")
            cancel()
            Log.d("${this@KeepAwakeService::caffeineTimeoutJob.name} cancelled!")
        }

        localeChangeReceiver.isRegistered = false
        screenLockReceiver.isRegistered = false

        keepAwakeServiceObservers.removeObserver(this@KeepAwakeService)
        sharedPrefsObservers.removeObserver(this@KeepAwakeService)

        lastStatusUpdate = ServiceStatus.Stopped

        notificationManager.cancel(NOTIFICATION_ID)

        Log.d("${localizedApplicationContext.getString(R.string.app_name)} stopped!")

        if (isOverlayEnabled) overlayHandler.hideOverlay()

        sharedPreferences.isServiceRunning = false

        stopSelf()
    }

    /**
     * A companion object for [KeepAwakeService].
     *
     * This object contains public constants and methods that are related to the
     * [KeepAwakeService] class.
     *
     * @author AbdAlMoniem AlHifnawy
     */
    companion object {

        /**
         * The state of the [KeepAwakeService].
         *
         * This enum defines the possible states of the [KeepAwakeService].
         *
         * @property STATE_START The service is running and keeping the device awake.
         * @property STATE_STOP The service is stopped and the device is not being kept awake.
         * @property STATE_TOGGLE The service is being toggled. If the service is currently running, it will be stopped. If the service is currently
         * stopped, it will be started.
         *
         * @author AbdAlMoniem AlHifnawy
         */
        enum class KeepAwakeServiceState {

            /**
             * The service is running and keeping the device awake.
             */
            STATE_START,

            /**
             * The service will be running and keeping the device awake with after a delay.
             */
            STATE_START_DELAYED,

            /**
             * The service is stopped and the device is not being kept awake.
             */
            STATE_STOP,

            /**
             * The service is being toggled. If the service is currently running, it will be stopped. If the service is currently stopped, it will be
             * started.
             */
            STATE_TOGGLE
        }

        /**
         * The possible actions that can be sent to the [KeepAwakeService].
         *
         * This enum defines the possible actions that can be sent to the [KeepAwakeService] through an [Intent].
         *
         * @property ACTION_START The service is being started.
         * @property ACTION_RESTART The service is being restarted.
         * @property ACTION_STOP The service is being stopped.
         * @property ACTION_NEXT_TIMEOUT The timeout duration is being changed.
         * @property ACTION_CHANGE_DIMMING_ENABLED The dimming feature is being toggled.
         *
         * @author AbdAlMoniem AlHifnawy
         */
        private enum class KeepAwakeServiceAction {

            /**
             * Start the service to keep the device awake.
             */
            ACTION_START,

            /**
             * Start the service to keep the device awake with a delay.
             */
            ACTION_START_DELAYED,

            /**
             * Restart the service to keep the device awake.
             *
             * This action can be used to restart the service if it is currently running, or to start it if it is currently stopped.
             */
            ACTION_RESTART,

            /**
             * Stop the service, allowing the device to sleep.
             */
            ACTION_STOP,

            /**
             * Change the timeout duration to the next available option.
             *
             * This action triggers the service to switch the current timeout duration
             * to the next option in the list of configured timeouts.
             */
            ACTION_NEXT_TIMEOUT,

            /**
             * Toggle the dimming feature on or off.
             */
            ACTION_CHANGE_DIMMING_ENABLED;

            /**
             * A companion object for [KeepAwakeServiceAction].
             *
             * This object contains public constants and methods that are related to the
             * [KeepAwakeServiceAction] enum.
             *
             * @author AbdAlMoniem AlHifnawy
             */
            companion object {

                /**
                 * A method that returns the [KeepAwakeServiceAction] corresponding to the given [value].
                 *
                 * This method takes a [value] of type [ObjectType] and returns the [KeepAwakeServiceAction] enum constant
                 * that matches the given [value].
                 *
                 * @param value [ObjectType] The value to search for.
                 * @return [KeepAwakeServiceAction] The [KeepAwakeServiceAction] enum constant that matches the given [value], or null if no match is found.
                 */
                inline fun <reified ObjectType> valueOfOrNull(value: ObjectType) = entries.find { entry -> entry.name == value }
            }
        }

        /**
         * Enum constants representing the possible request codes for the notification actions.
         *
         * These enum constants are used to identify the request code of the notification action that was clicked.
         *
         * @property REQUEST_CODE_NEXT_TIMEOUT The request code for the "Next timeout" notification action.
         * @property REQUEST_CODE_RESTART_TIMEOUT The request code for the "Restart timeout" notification action.
         * @property REQUEST_CODE_TOGGLE_DIMMING The request code for the "Toggle dimming" notification action.
         *
         * @author AbdAlMoniem AlHifnawy
         */
        private enum class NotificationActionRequestCode {

            /**
             * The request code for the "Next timeout" notification action.
             */
            REQUEST_CODE_NEXT_TIMEOUT,

            /**
             * The request code for the "Restart timeout" notification action.
             */
            REQUEST_CODE_RESTART_TIMEOUT,

            /**
             * The request code for the "Toggle dimming" notification action.
             */
            REQUEST_CODE_TOGGLE_DIMMING
        }

        /**
         * The duration of time to wait before debouncing the next timeout.
         *
         * When the user requests a new timeout, the service will wait for this duration of time before
         * starting the new timeout. This allows the user to quickly switch between different timeouts
         * without the service immediately switching to the new timeout.
         *
         * @see KeepAwakeServiceState.STATE_START
         * @see KeepAwakeServiceState.STATE_STOP
         */
        private val DEBOUNCE_DURATION = 1.seconds

        /**
         * Debounces the next timeout.
         *
         * When the user requests a new timeout, the service will wait for [DEBOUNCE_DURATION] before starting the new timeout.
         * This allows the user to quickly switch between different timeouts without the service immediately switching to the new
         * timeout.
         *
         * @param status [ServiceStatus] The current status of the KeepAwakeService.
         * @param caffeinateApplication [CaffeinateApplication] The application context.
         *
         * @return [KeepAwakeServiceState] The next [KeepAwakeServiceState] to transition to.
         */
        private fun debounce(status: ServiceStatus.Running, caffeinateApplication: CaffeinateApplication) = caffeinateApplication.run {
            fun nextTimeout(): KeepAwakeServiceState {
                timeout = nextTimeout
                return when (prevTimeout) {
                    lastTimeout -> STATE_STOP
                    else        -> STATE_START_DELAYED
                }
            }

            val keepAwakeServiceState = when {
                status.isCountingDown -> STATE_STOP
                else                  -> nextTimeout()
            }

            toggleState(this, keepAwakeServiceState)
        }

        /**
         * Starts the KeepAwakeService with debouncing.
         *
         * If the service is not running, this function will start the service with the provided timeout.
         * If the service is already running, this function will debounce the next timeout by waiting for [DEBOUNCE_DURATION]
         * before starting the new timeout.
         *
         * @param caffeinateApplication [CaffeinateApplication] The application context.
         */
        private fun startWithDebounce(caffeinateApplication: CaffeinateApplication) = caffeinateApplication.run {
            when (val status = lastStatusUpdate) {
                is ServiceStatus.Stopped -> toggleState(this, STATE_START_DELAYED)
                is ServiceStatus.Running -> debounce(status, this)
            }
        }

        /**
         * Starts the KeepAwakeService without debouncing.
         *
         * If the service is not running, this function will immediately start the service with the provided
         * timeout. If the service is already running, this function will immediately stop the service and
         * start it again with the provided timeout.
         *
         * @param caffeinateApplication [CaffeinateApplication] The application context.
         */
        private fun startWithoutDebounce(caffeinateApplication: CaffeinateApplication) = caffeinateApplication.run {
            timeout = when (lastStatusUpdate) {
                is ServiceStatus.Stopped -> firstTimeout
                is ServiceStatus.Running -> nextTimeout
            }
            toggleState(this, STATE_START)
        }

        /**
         * Starts the KeepAwakeService with the single timeout duration.
         *
         * If the service is not running, this function will start the service with the single timeout duration.
         * If the service is already running, this function will debounce the next timeout by waiting for [DEBOUNCE_DURATION]
         * before starting the new timeout.
         *
         * @param caffeinateApplication [CaffeinateApplication] The application context.
         */
        private fun startSingleTimeout(caffeinateApplication: CaffeinateApplication) = caffeinateApplication.run {
            when (lastStatusUpdate) {
                is ServiceStatus.Stopped -> startWithoutDebounce(this)
                is ServiceStatus.Running -> toggleState(this, STATE_STOP)
            }
        }

        /**
         * Starts the KeepAwakeService with the next timeout duration from the list of timeouts.
         *
         * If the service is not running, this function will start the service with the next timeout duration.
         * If the service is already running, this function will debounce the next timeout by waiting for [DEBOUNCE_DURATION]
         * before starting the new timeout.
         *
         * @param caffeinateApplication [CaffeinateApplication] The application context.
         * @param debounce [Boolean] If `true`, the service will debounce the next timeout by waiting for [DEBOUNCE_DURATION]
         * before starting the new timeout. If `false`, the service will start the new timeout immediately.
         */
        fun startNextTimeout(caffeinateApplication: CaffeinateApplication, debounce: Boolean = true) = caffeinateApplication.run {
            when {
                timeoutCheckBoxes.size == 1 -> startSingleTimeout(this)
                debounce                    -> startWithDebounce(this)
                else                        -> startWithoutDebounce(this)
            }
        }

        /**
         * Starts the KeepAwakeService indefinitely.
         *
         * If the service is not running, this function will start the service with an infinite timeout.
         * If the service is already running, this function will debounce the next timeout by waiting for [DEBOUNCE_DURATION]
         * before starting the new timeout.
         *
         * @param caffeinateApplication [CaffeinateApplication] The application context.
         */
        fun startIndefinitely(caffeinateApplication: CaffeinateApplication) = caffeinateApplication.run {
            timeout = Duration.INFINITE
            toggleState(this, STATE_START)
        }

        /**
         * Restarts the KeepAwakeService.
         *
         * This method can be used to restart the KeepAwakeService. If the service is not running, this method will start the service with the
         * provided timeout. If the service is already running, this method will debounce the next timeout by waiting for [DEBOUNCE_DURATION]
         * before starting the new timeout.
         *
         * This is a handy method to use when the user wants to restart the KeepAwakeService. it is equivalent to calling:
         *
         * ```
         * toggleState(
         *       caffeinateApplication,
         *       newKeepAwakeServiceState = KeepAwakeServiceState.START,
         *       startTimeout = null
         * )
         * ```
         *
         * @param caffeinateApplication [CaffeinateApplication] The application context.
         * @param startTimeout [Duration] The timeout duration to use when starting the service. If `null`, the service will use the previously set timeout.
         */
        fun restart(caffeinateApplication: CaffeinateApplication, startTimeout: Duration? = null) =
                toggleState(caffeinateApplication, STATE_START_DELAYED, startTimeout)

        /**
         * Toggles the state of the KeepAwakeService.
         *
         * This method can be used to start, stop, or toggle the state of the KeepAwakeService.
         * If the service is not running, this method will start the service with the provided timeout.
         * If the service is already running, this method will debounce the next timeout by waiting for [DEBOUNCE_DURATION]
         * before starting the new timeout.
         *
         * @param caffeinateApplication [CaffeinateApplication] The application context.
         * @param newKeepAwakeServiceState [KeepAwakeServiceState] The new state to transition to.
         * @param startTimeout [Duration] The timeout duration to use when starting the service. If `null`, the service will use the previously set timeout.
         */
        fun toggleState(
                caffeinateApplication: CaffeinateApplication,
                newKeepAwakeServiceState: KeepAwakeServiceState,
                startTimeout: Duration? = null
        ): Unit = caffeinateApplication.run {
            Log.d("newState: $newKeepAwakeServiceState")
            val start = when (newKeepAwakeServiceState) {
                STATE_START         -> true
                STATE_START_DELAYED -> true
                STATE_STOP          -> false
                STATE_TOGGLE        -> lastStatusUpdate is ServiceStatus.Stopped
            }
            val intent = Intent(localizedApplicationContext, KeepAwakeService::class.java).apply {
                action = when {
                    start -> if (newKeepAwakeServiceState == STATE_START_DELAYED) ACTION_START_DELAYED.name else ACTION_START.name
                    else  -> ACTION_STOP.name
                }
            }

            startTimeout?.run { timeout = this }

            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> localizedApplicationContext.startForegroundService(intent)
                else                                           -> localizedApplicationContext.startService(intent)
            }
        }

        /**
         * The ID for the notification used by the KeepAwakeService.
         *
         * This ID is used to uniquely identify the notification that displays
         * the remaining time when the service is running in the foreground.
         */
        private const val NOTIFICATION_ID = 23
    }
}