package com.hifnawy.caffeinate.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.ServiceStatus
import com.hifnawy.caffeinate.ServiceStatusObserver
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toFormattedTime
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toLocalizedFormattedTime
import com.hifnawy.caffeinate.utils.MutableListExtensionFunctions.addObserver
import com.hifnawy.caffeinate.utils.NotificationUtils
import com.hifnawy.caffeinate.utils.SharedPrefsManager
import com.hifnawy.caffeinate.utils.WakeLockExtensionFunctions.releaseSafely
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
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
class KeepAwakeService : Service(), SharedPrefsManager.SharedPrefsChangedListener, ServiceStatusObserver {

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
     * A flag indicating whether the [LocaleChangeReceiver] is registered.
     *
     * This flag is used to track whether the [LocaleChangeReceiver] is currently registered to receive
     * [Intent.ACTION_LOCALE_CHANGED] broadcasts, which are sent when the system locale changes.
     *
     * @see LocaleChangeReceiver
     * @see Intent.ACTION_LOCALE_CHANGED
     */
    private var isLocaleChangeReceiverRegistered = false

    /**
     * A flag indicating whether the [ScreenLockReceiver] is registered.
     *
     * This flag is used to track whether the [ScreenLockReceiver] is currently registered to receive
     * [Intent.ACTION_SCREEN_OFF] broadcasts, which are sent when the screen is turned off.
     *
     * @see ScreenLockReceiver
     * @see Intent.ACTION_SCREEN_OFF
     */
    private var isScreenLockReceiverRegistered = false

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
     * A [PowerManager.WakeLock] that is used to keep the device awake while this service is running.
     *
     * This wake lock is used to prevent the device from going to sleep while the service is running.
     * It is acquired when the service is started and released when the service is stopped.
     *
     * @see startService
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
    private var caffeineTimeoutJob: TimeoutJob? = null

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
     * @param intent [Intent] The [Intent] supplied to [startService], as given.
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

        intent ?: return START_NOT_STICKY
        val keepAwakeServiceAction = intent.action?.let { action -> KeepAwakeServiceAction.valueOfOrNull(action) } ?: let {
            Log.wtf("intent.action: ${intent.action} cannot be parsed to ${KeepAwakeServiceAction::class.qualifiedName}!")
            KeepAwakeServiceAction.STOP
        }

        Log.d("serviceAction: $keepAwakeServiceAction")

        isDimmingEnabled = sharedPreferences.isDimmingEnabled

        when (keepAwakeServiceAction) {
            KeepAwakeServiceAction.START                  -> startService()
            KeepAwakeServiceAction.STOP                   -> stopSelf()
            KeepAwakeServiceAction.CHANGE_TIMEOUT         -> startNextTimeout(caffeinateApplication, debounce = false)
            KeepAwakeServiceAction.CHANGE_DIMMING_ENABLED -> sharedPreferences.isDimmingEnabled = !sharedPreferences.isDimmingEnabled
        }

        return START_NOT_STICKY
    }

    /**
     * Called by the system to notify a Service that it is no longer used and is being removed.
     * The service should clean up any resources it has acquired, such as threads, registered
     * receivers, etc. This is the last call the service receives.
     *
     * @see [onStartCommand]
     * @see [onLowMemory]
     * @see [onTrimMemory]
     * @see [onTaskRemoved]
     */
    override fun onDestroy() {
        super.onDestroy()

        Log.d("stopping ${caffeinateApplication.localizedApplicationContext.getString(R.string.app_name)}...")
        stopCaffeine()
    }

    /**
     * Called when the "All Permissions Granted" preference changes.
     *
     * This is a part of the [SharedPrefsChangedListener][com.hifnawy.caffeinate.utils.SharedPrefsManager.SharedPrefsChangedListener] interface.
     * This method is called when the "All Permissions Granted" preference changes. The service will try to acquire a wake lock with the new value of
     * [isDimmingEnabled].
     *
     * This method is a no-op as the service does not need to react to this change.
     *
     * @param isAllPermissionsGranted [Boolean] `true` if all permissions are granted, `false` otherwise.
     */
    override fun onIsAllPermissionsGrantedChanged(isAllPermissionsGranted: Boolean) = Unit

    /**
     * Called when the "Dimming Enabled" preference changes.
     *
     * This is a part of the [SharedPrefsChangedListener][com.hifnawy.caffeinate.utils.SharedPrefsManager.SharedPrefsChangedListener] interface.
     * This method is called when the "Dimming Enabled" preference changes. The service will try to acquire a wake lock with the new value of
     * [isDimmingEnabled].
     *
     * @param isDimmingEnabled [Boolean] `true` if dimming is enabled, `false` otherwise.
     */
    override fun onIsDimmingEnabledChanged(isDimmingEnabled: Boolean) {
        Log.d("isDimmingEnabled: $isDimmingEnabled")

        when (val status = caffeinateApplication.lastStatusUpdate) {
            is ServiceStatus.Running -> {
                this.isDimmingEnabled = isDimmingEnabled
                acquireWakeLock(status.remaining)
                onServiceStatusUpdate(status)
            }

            is ServiceStatus.Stopped -> Unit
        }
    }

    /**
     * Called when the "While Locked Enabled" preference changes.
     *
     * This is a part of the [SharedPrefsChangedListener][com.hifnawy.caffeinate.utils.SharedPrefsManager.SharedPrefsChangedListener] interface.
     * This method is called when the "While Locked Enabled" preference changes. The service will try to acquire a wake lock with the new value of
     * [isWhileLockedEnabled].
     *
     * @param isWhileLockedEnabled [Boolean] `true` if the "While Locked" feature is enabled, `false` otherwise.
     */
    override fun onIsWhileLockedEnabledChanged(isWhileLockedEnabled: Boolean) {
        Log.d("isWhileLockedEnabled: $isWhileLockedEnabled")

        when (caffeinateApplication.lastStatusUpdate) {
            is ServiceStatus.Running -> if (isWhileLockedEnabled) unregisterScreenLockReceiver() else registerScreenLockReceiver()
            is ServiceStatus.Stopped -> Unit
        }
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
    override fun onServiceStatusUpdate(status: ServiceStatus) {
        when (status) {
            is ServiceStatus.Running -> {
                Log.d("duration: ${status.remaining.toFormattedTime()}, status: $status, isIndefinite: ${status.remaining == Duration.INFINITE}")
                notificationManager.notify(NOTIFICATION_ID, buildForegroundNotification(status))
            }

            else                     -> stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    /**
     * Starts the foreground service.
     *
     * This method is called when the start intent is received.
     * It acquires a wake lock and starts the foreground service
     * with the notification that displays the remaining time.
     *
     * @see [startForeground]
     * @see [acquireWakeLock]
     */
    private fun startService() {
        val status = caffeinateApplication.lastStatusUpdate as ServiceStatus.Running
        Log.d("status: $status, selectedDuration: ${status.remaining.toFormattedTime()}")

        Log.d("sending foreground notification...")
        startForeground(NOTIFICATION_ID, buildForegroundNotification(status))
        Log.d("foreground notification sent!")

        Log.d("adding ${this::class.simpleName} to ${CaffeinateApplication::keepAwakeServiceObservers.name}...")
        caffeinateApplication.keepAwakeServiceObservers.addObserver(caffeinateApplication::keepAwakeServiceObservers.name, this)
        Log.d("${this::class.simpleName} added to ${CaffeinateApplication::keepAwakeServiceObservers.name}!")

        Log.d("adding ${this::class.simpleName} to ${CaffeinateApplication::sharedPrefsObservers.name}...")
        caffeinateApplication.sharedPrefsObservers.addObserver(caffeinateApplication::sharedPrefsObservers.name, this)
        Log.d("${this::class.simpleName} added to ${CaffeinateApplication::sharedPrefsObservers.name}!")

        registerLocaleChangeReceiver()
        if (!sharedPreferences.isWhileLockedEnabled) registerScreenLockReceiver()

        startCaffeine(status.remaining)
    }

    /**
     * Registers the [localeChangeReceiver] to receive broadcasts when the system locale is changed.
     *
     * This method is used to register the [localeChangeReceiver] to receive broadcasts when the system locale is changed. The
     * receiver is registered with the [Intent.ACTION_LOCALE_CHANGED] intent filter, which is sent when the system locale is
     * changed. The receiver is also exported to allow other applications to send broadcasts to it.
     *
     * @see [localeChangeReceiver]
     * @see [Intent.ACTION_LOCALE_CHANGED]
     * @see [registerReceiver]
     */
    private fun registerLocaleChangeReceiver() {
        if (!isLocaleChangeReceiverRegistered) {
            Log.d("registering ${this::localeChangeReceiver.name}...")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(localeChangeReceiver, IntentFilter(Intent.ACTION_LOCALE_CHANGED), RECEIVER_EXPORTED)
            } else {
                registerReceiver(localeChangeReceiver, IntentFilter(Intent.ACTION_LOCALE_CHANGED))
            }

            isLocaleChangeReceiverRegistered = true

            Log.d("${this::localeChangeReceiver.name} registered!")
        }
    }

    /**
     * Unregisters the [localeChangeReceiver] from receiving broadcasts when the system locale is changed.
     *
     * This method is used to unregister the [localeChangeReceiver] so that it no longer receives broadcasts when the
     * system locale is changed. It ensures that the receiver is unregistered only if it is currently registered.
     *
     * @see [localeChangeReceiver]
     * @see [unregisterReceiver]
     */
    private fun unregisterLocaleChangeReceiver() {
        if (isLocaleChangeReceiverRegistered) {
            Log.d("unregistering ${this::localeChangeReceiver.name}...")
            unregisterReceiver(localeChangeReceiver)
            isLocaleChangeReceiverRegistered = false
            Log.d("${this::localeChangeReceiver.name} unregistered!")
        }
    }

    /**
     * Registers the [screenLockReceiver] to receive broadcasts when the screen is turned off.
     *
     * This method is used to register the [screenLockReceiver] so that it can receive broadcasts when the screen is turned
     * off. It ensures that the receiver is registered only if it is currently not registered.
     *
     * @see [screenLockReceiver]
     * @see [registerReceiver]
     * @see [Intent.ACTION_SCREEN_OFF]
     */
    private fun registerScreenLockReceiver() {
        if (!isScreenLockReceiverRegistered) {
            Log.d("registering ${this::screenLockReceiver.name}...")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenLockReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_EXPORTED)
            } else {
                registerReceiver(screenLockReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            }

            isScreenLockReceiverRegistered = true

            Log.d("${this::screenLockReceiver.name} registered!")
        }
    }

    /**
     * Unregisters the [screenLockReceiver] from receiving broadcasts when the screen is turned off.
     *
     * This method is used to unregister the [screenLockReceiver] so that it can no longer receive broadcasts when
     * the screen is turned off. It ensures that the receiver is unregistered only if it is currently registered.
     *
     * @see [screenLockReceiver]
     * @see [unregisterReceiver]
     */
    private fun unregisterScreenLockReceiver() {
        if (isScreenLockReceiverRegistered) {
            Log.d("unregistering ${this::screenLockReceiver.name}...")
            unregisterReceiver(screenLockReceiver)
            isScreenLockReceiverRegistered = false
            Log.d("${this::screenLockReceiver.name} unregistered!")
        }
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
    private fun buildForegroundNotification(status: ServiceStatus): Notification {
        caffeinateApplication.run {
            val notificationStopIntent =
                    NotificationUtils.getPendingIntent(localizedApplicationContext, KeepAwakeService::class.java, KeepAwakeServiceAction.STOP.name, 0)
            val notificationActionNextTimeoutStr = localizedApplicationContext.getString(R.string.foreground_notification_action_next_timeout)
            val notificationActionDimmingEnabledStr = localizedApplicationContext.getString(R.string.foreground_notification_action_disable_dimming)
            val notificationActionDimmingDisabledStr = localizedApplicationContext.getString(R.string.foreground_notification_action_enable_dimming)
            val notificationKeepAwakeServiceActionNextTimeout = NotificationUtils.getNotificationAction(
                    localizedApplicationContext,
                    KeepAwakeService::class.java,
                    KeepAwakeServiceAction.CHANGE_TIMEOUT.name,
                    R.drawable.baseline_coffee_24,
                    notificationActionNextTimeoutStr,
                    NotificationActionRequestCode.NEXT_TIMEOUT.ordinal
            )
            val notificationKeepAwakeServiceActionToggleDimming = NotificationUtils.getNotificationAction(
                    this,
                    KeepAwakeService::class.java,
                    KeepAwakeServiceAction.CHANGE_DIMMING_ENABLED.name,
                    R.drawable.baseline_coffee_24,
                    if (isDimmingEnabled) notificationActionDimmingEnabledStr else notificationActionDimmingDisabledStr,
                    NotificationActionRequestCode.TOGGLE_DIMMING.ordinal
            )
            val notificationBuilder = NotificationCompat.Builder(localizedApplicationContext, notificationChannelID)
            var durationStr: String? = null
            var contentTitle: String? = null

            if (status is ServiceStatus.Running) {
                durationStr = status.remaining.toLocalizedFormattedTime(localizedApplicationContext)
                contentTitle = when (status.remaining) {
                    Duration.INFINITE -> localizedApplicationContext.getString(R.string.foreground_notification_title_duration_indefinite)
                    else              -> localizedApplicationContext.getString(R.string.foreground_notification_title_duration_definite, durationStr)
                }
            }
            notificationBuilder
                .setSilent(true)
                .setOngoing(true)
                .setSubText(durationStr)
                .setContentTitle(contentTitle)
                .setContentIntent(notificationStopIntent)
                .setSmallIcon(R.drawable.baseline_coffee_24)
                .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
                .addAction(notificationKeepAwakeServiceActionNextTimeout)
                .addAction(notificationKeepAwakeServiceActionToggleDimming)
                .setContentInfo(localizedApplicationContext.getString(R.string.app_name))
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentText(localizedApplicationContext.getString(R.string.foreground_notification_tap_to_turn_off))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notificationChannel?.let { channel -> notificationBuilder.setChannelId(channel.id) }

            return notificationBuilder.build()
        }
    }

    /**
     * Acquires a wake lock with the specified duration.
     *
     * This method is used to acquire a wake lock with the specified [duration] that prevents the device from going to sleep.
     * It uses the [PowerManager] to acquire the wake lock, and logs information about the wake lock acquisition.
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
     *
     * @param duration [Duration] the duration of the wake lock.
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
        } or PowerManager.ACQUIRE_CAUSES_WAKEUP

        wakeLock = powerManager.newWakeLock(
                wakeLockLevel,
                "${caffeinateApplication.localizedApplicationContext.getString(R.string.app_name)}:wakelockTag"
        ).apply {
            Log.d("acquiring ${this@KeepAwakeService::wakeLock.name}, isDimmingAllowed: $isDimmingEnabled...")
            setReferenceCounted(false)
            if (duration == Duration.INFINITE) acquire() else acquire(duration.inWholeMilliseconds)
            Log.d("${this@KeepAwakeService::wakeLock.name} acquired, isDimmingAllowed: $isDimmingEnabled!")
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
     */
    private fun startCaffeine(duration: Duration) {
        val isIndefinite = duration == Duration.INFINITE
        Log.d("starting ${caffeinateApplication.localizedApplicationContext.getString(R.string.app_name)} with duration: ${duration.toFormattedTime()}, isIndefinite: $isIndefinite")

        acquireWakeLock(duration)

        caffeineTimeoutJob?.apply {
            Log.d("cancelling ${this@KeepAwakeService::caffeineTimeoutJob.name}...")
            cancel()
            Log.d("${this@KeepAwakeService::caffeineTimeoutJob.name} cancelled!")
        }

        Log.d("creating ${this::caffeineTimeoutJob.name}...")
        caffeineTimeoutJob = TimeoutJob(caffeinateApplication)
        Log.d("${this::caffeineTimeoutJob.name} created!")

        Log.d("starting ${this::caffeineTimeoutJob.name}...")
        caffeineTimeoutJob?.start(duration)
        Log.d("${this::caffeineTimeoutJob.name} started!")
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
    private fun stopCaffeine() {
        caffeinateApplication.run {
            Log.d("stopping ${localizedApplicationContext.getString(R.string.app_name)}...")

            wakeLock?.apply {
                Log.d("releasing ${this@KeepAwakeService::wakeLock.name}...")
                releaseSafely(::wakeLock.name)
                Log.d("${this@KeepAwakeService::wakeLock.name} released!")
            } ?: Log.d("wakeLock is not held!")

            caffeineTimeoutJob?.apply {
                Log.d("cancelling ${this@KeepAwakeService::caffeineTimeoutJob.name}...")
                cancel()
                Log.d("${this@KeepAwakeService::caffeineTimeoutJob.name} cancelled!")
            }

            unregisterLocaleChangeReceiver()
            unregisterScreenLockReceiver()

            Log.d("removing from ${CaffeinateApplication::keepAwakeServiceObservers.name}...")
            keepAwakeServiceObservers.remove(this@KeepAwakeService)
            Log.d("removed from ${CaffeinateApplication::keepAwakeServiceObservers.name}!")

            Log.d("removing from ${CaffeinateApplication::sharedPrefsObservers.name}...")
            sharedPrefsObservers.remove(this@KeepAwakeService)
            Log.d("removed from ${CaffeinateApplication::sharedPrefsObservers.name}!")

            Log.d("notifying observers...")
            caffeinateApplication.lastStatusUpdate = ServiceStatus.Stopped
            Log.d("observers notified!")

            Log.d("${localizedApplicationContext.getString(R.string.app_name)} stopped!")
        }
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
         * @property START The service is running and keeping the device awake.
         * @property STOP The service is stopped and the device is not being kept awake.
         * @property TOGGLE The service is being toggled. If the service is currently running, it will be stopped. If the service is currently
         * stopped, it will be started.
         *
         * @author AbdAlMoniem AlHifnawy
         */
        enum class KeepAwakeServiceState {

            /**
             * The service is running and keeping the device awake.
             */
            START,

            /**
             * The service is stopped and the device is not being kept awake.
             */
            STOP,

            /**
             * The service is being toggled. If the service is currently running, it will be stopped. If the service is currently stopped, it will be
             * started.
             */
            TOGGLE
        }

        /**
         * The possible actions that can be sent to the [KeepAwakeService].
         *
         * This enum defines the possible actions that can be sent to the [KeepAwakeService] through an [Intent].
         *
         * @property START The service is being started.
         * @property STOP The service is being stopped.
         * @property CHANGE_TIMEOUT The timeout duration is being changed.
         * @property CHANGE_DIMMING_ENABLED The dimming feature is being toggled.
         *
         * @author AbdAlMoniem AlHifnawy
         */
        private enum class KeepAwakeServiceAction {

            /**
             * Start the service to keep the device awake.
             */
            START,

            /**
             * Stop the service, allowing the device to sleep.
             */
            STOP,

            /**
             * Change the timeout duration for keeping the device awake.
             */
            CHANGE_TIMEOUT,

            /**
             * Toggle the dimming feature on or off.
             */
            CHANGE_DIMMING_ENABLED;

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
         * @property NEXT_TIMEOUT The request code for the "Next timeout" notification action.
         * @property TOGGLE_DIMMING The request code for the "Toggle dimming" notification action.
         *
         * @author AbdAlMoniem AlHifnawy
         */
        private enum class NotificationActionRequestCode {

            /**
             * The request code for the "Next timeout" notification action.
             */
            NEXT_TIMEOUT,

            /**
             * The request code for the "Toggle dimming" notification action.
             */
            TOGGLE_DIMMING
        }

        /**
         * The duration of time to wait before debouncing the next timeout.
         *
         * When the user requests a new timeout, the service will wait for this duration of time before
         * starting the new timeout. This allows the user to quickly switch between different timeouts
         * without the service immediately switching to the new timeout.
         *
         * @see KeepAwakeServiceState.START
         * @see KeepAwakeServiceState.STOP
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
                    lastTimeout -> KeepAwakeServiceState.STOP
                    else        -> KeepAwakeServiceState.START
                }
            }

            val debounceOffset = timeout - DEBOUNCE_DURATION / 2
            val keepAwakeServiceState = when {
                status.remaining <= debounceOffset -> KeepAwakeServiceState.STOP
                else                               -> nextTimeout()
            }

            toggleState(this, keepAwakeServiceState)
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
            timeout = nextTimeout
            toggleState(this, KeepAwakeServiceState.START)
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
                is ServiceStatus.Stopped -> toggleState(this, KeepAwakeServiceState.START)
                is ServiceStatus.Running -> debounce(status, this)
            }
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
                is ServiceStatus.Running -> toggleState(this, KeepAwakeServiceState.STOP)
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
            toggleState(this, KeepAwakeServiceState.START)
        }

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
        ) {
            Log.d("newState: $newKeepAwakeServiceState")

            caffeinateApplication.apply {
                val start = when (newKeepAwakeServiceState) {
                    KeepAwakeServiceState.START  -> true
                    KeepAwakeServiceState.STOP   -> false
                    KeepAwakeServiceState.TOGGLE -> lastStatusUpdate is ServiceStatus.Stopped
                }
                val status = when {
                    start -> ServiceStatus.Running(startTimeout ?: timeout)
                    else  -> ServiceStatus.Stopped
                }

                lastStatusUpdate = status
                val intent = Intent(localizedApplicationContext, KeepAwakeService::class.java).apply {
                    action = when {
                        start -> KeepAwakeServiceAction.START.name
                        else  -> KeepAwakeServiceAction.STOP.name
                    }
                }

                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> localizedApplicationContext.startForegroundService(intent)
                    else                                           -> localizedApplicationContext.startService(intent)
                }
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