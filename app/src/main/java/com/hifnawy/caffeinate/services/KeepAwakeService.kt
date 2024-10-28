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

class KeepAwakeService : Service(), SharedPrefsManager.SharedPrefsChangedListener, ServiceStatusObserver {

    override fun onBind(intent: Intent): IBinder? = null

    private val caffeinateApplication by lazy { application as CaffeinateApplication }
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val sharedPreferences by lazy { SharedPrefsManager(caffeinateApplication) }
    private val localeChangeReceiver by lazy { LocaleChangeReceiver(caffeinateApplication) }
    private val screenLockReceiver by lazy { ScreenLockReceiver(caffeinateApplication) }
    private val notificationChannelID by lazy { caffeinateApplication.localizedApplicationContext.getString(R.string.app_name) }
    private val notificationChannel by lazy {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> notificationManager.getNotificationChannel(notificationChannelID)
                                                              ?: let {
                                                                  NotificationChannel(notificationChannelID, notificationChannelID, NotificationManager.IMPORTANCE_HIGH)
                                                                      .also { channel -> notificationManager.createNotificationChannel(channel) }
                                                              }

            else                                           -> null
        }
    }
    private var isLocaleChangeReceiverRegistered = false
    private var isScreenLockReceiverRegistered = false
    private var isDimmingEnabled = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var caffeineTimeoutJob: TimeoutJob? = null

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

    override fun onDestroy() {
        super.onDestroy()

        Log.d("stopping ${caffeinateApplication.localizedApplicationContext.getString(R.string.app_name)}...")
        stopCaffeine()
    }

    override fun onIsAllPermissionsGrantedChanged(isAllPermissionsGranted: Boolean) = Unit

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

    override fun onIsWhileLockedEnabledChanged(isWhileLockedEnabled: Boolean) {
        Log.d("isWhileLockedEnabled: $isWhileLockedEnabled")

        when (caffeinateApplication.lastStatusUpdate) {
            is ServiceStatus.Running -> if (isWhileLockedEnabled) unregisterScreenLockReceiver() else registerScreenLockReceiver()
            is ServiceStatus.Stopped -> Unit
        }
    }

    override fun onServiceStatusUpdate(status: ServiceStatus) {
        when (status) {
            is ServiceStatus.Running -> {
                Log.d("duration: ${status.remaining.toFormattedTime()}, status: $status, isIndefinite: ${status.remaining == Duration.INFINITE}")
                notificationManager.notify(NOTIFICATION_ID, buildForegroundNotification(status))
            }

            else                     -> stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

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

    private fun unregisterLocaleChangeReceiver() {
        if (isLocaleChangeReceiverRegistered) {
            Log.d("unregistering ${this::localeChangeReceiver.name}...")
            unregisterReceiver(localeChangeReceiver)
            isLocaleChangeReceiverRegistered = false
            Log.d("${this::localeChangeReceiver.name} unregistered!")
        }
    }

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

    private fun unregisterScreenLockReceiver() {
        if (isScreenLockReceiverRegistered) {
            Log.d("unregistering ${this::screenLockReceiver.name}...")
            unregisterReceiver(screenLockReceiver)
            isScreenLockReceiverRegistered = false
            Log.d("${this::screenLockReceiver.name} unregistered!")
        }
    }

    private fun buildForegroundNotification(status: ServiceStatus): Notification {
        caffeinateApplication.run {
            val notificationStopIntent = NotificationUtils.getPendingIntent(localizedApplicationContext, KeepAwakeService::class.java, KeepAwakeServiceAction.STOP.name, 0)
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

        wakeLock = powerManager.newWakeLock(wakeLockLevel, "${caffeinateApplication.localizedApplicationContext.getString(R.string.app_name)}:wakelockTag").apply {
            Log.d("acquiring ${this@KeepAwakeService::wakeLock.name}, isDimmingAllowed: $isDimmingEnabled...")
            setReferenceCounted(false)
            if (duration == Duration.INFINITE) acquire() else acquire(duration.inWholeMilliseconds)
            Log.d("${this@KeepAwakeService::wakeLock.name} acquired, isDimmingAllowed: $isDimmingEnabled!")
        }
    }

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

            Log.d("${::stopCaffeine.name}: removing from ${CaffeinateApplication::keepAwakeServiceObservers.name}...")
            keepAwakeServiceObservers.remove(this@KeepAwakeService)
            Log.d("removed from ${CaffeinateApplication::keepAwakeServiceObservers.name}!")

            Log.d("${::stopCaffeine.name}: removing from ${CaffeinateApplication::sharedPrefsObservers.name}...")
            sharedPrefsObservers.remove(this@KeepAwakeService)
            Log.d("removed from ${CaffeinateApplication::sharedPrefsObservers.name}!")

            Log.d("notifying observers...")
            caffeinateApplication.lastStatusUpdate = ServiceStatus.Stopped
            Log.d("observers notified!")

            Log.d("${localizedApplicationContext.getString(R.string.app_name)} stopped!")
        }
    }

    companion object {
        enum class KeepAwakeServiceState {
            START,
            STOP,
            TOGGLE
        }

        private enum class KeepAwakeServiceAction {
            START,
            STOP,
            CHANGE_TIMEOUT,
            CHANGE_DIMMING_ENABLED;

            companion object {

                inline fun <reified ObjectType> valueOfOrNull(value: ObjectType) = entries.find { entry -> entry.name == value }
            }
        }

        private enum class NotificationActionRequestCode {
            NEXT_TIMEOUT,
            TOGGLE_DIMMING
        }

        private val DEBOUNCE_DURATION = 1.seconds

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

        private fun startWithoutDebounce(caffeinateApplication: CaffeinateApplication) = caffeinateApplication.run {
            timeout = nextTimeout
            toggleState(this, KeepAwakeServiceState.START)
        }

        private fun startWithDebounce(caffeinateApplication: CaffeinateApplication) = caffeinateApplication.run {
            when (val status = lastStatusUpdate) {
                is ServiceStatus.Stopped -> toggleState(this, KeepAwakeServiceState.START)
                is ServiceStatus.Running -> debounce(status, this)
            }
        }

        private fun startSingleTimeout(caffeinateApplication: CaffeinateApplication) = caffeinateApplication.run {
            when (lastStatusUpdate) {
                is ServiceStatus.Stopped -> startWithoutDebounce(this)
                is ServiceStatus.Running -> toggleState(this, KeepAwakeServiceState.STOP)
            }
        }

        fun startNextTimeout(caffeinateApplication: CaffeinateApplication, debounce: Boolean = true) = caffeinateApplication.run {
            when {
                timeoutCheckBoxes.size == 1 -> startSingleTimeout(this)
                debounce                    -> startWithDebounce(this)
                else                        -> startWithoutDebounce(this)
            }
        }

        fun startIndefinitely(caffeinateApplication: CaffeinateApplication) = caffeinateApplication.run {
            timeout = Duration.INFINITE
            toggleState(this, KeepAwakeServiceState.START)
        }

        fun toggleState(caffeinateApplication: CaffeinateApplication, newKeepAwakeServiceState: KeepAwakeServiceState) {
            Log.d("newState: $newKeepAwakeServiceState")

            caffeinateApplication.apply {
                val start = when (newKeepAwakeServiceState) {
                    KeepAwakeServiceState.START  -> true
                    KeepAwakeServiceState.STOP   -> false
                    KeepAwakeServiceState.TOGGLE -> lastStatusUpdate is ServiceStatus.Stopped
                }
                val status = when {
                    start -> ServiceStatus.Running(timeout)
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

        private const val NOTIFICATION_ID = 23
    }
}