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
    private val screenLockReceiver by lazy { ScreenLockReceiver(caffeinateApplication) }
    private var isScreenLockReceiverRegistered = false
    private var isDimmingEnabled = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var caffeineTimeoutJob: TimeoutJob? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildForegroundNotification(caffeinateApplication.lastStatusUpdate))
        val serviceAction = ACTION.valueOfOrNull(intent?.action as String) ?: run {
            Log.wtf("intent.action: ${intent.action} cannot be parsed to ${ACTION::class.qualifiedName}!")
            ACTION.STOP
        }
        Log.d("serviceAction: $serviceAction")

        isDimmingEnabled = sharedPreferences.isDimmingEnabled

        when (serviceAction) {
            ACTION.START                  -> startService()
            ACTION.STOP                   -> stopSelf()
            ACTION.CHANGE_TIMEOUT         -> startNextTimeout(caffeinateApplication, debounce = false)
            ACTION.CHANGE_DIMMING_ENABLED -> sharedPreferences.isDimmingEnabled = !sharedPreferences.isDimmingEnabled
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


        if (!sharedPreferences.isWhileLockedEnabled) registerScreenLockReceiver()
        startCaffeine(status.remaining)
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
            val channelIdStr = "${localizedApplicationContext.getString(R.string.app_name)} Status"
            val notificationStopIntent = NotificationUtils.getPendingIntent(localizedApplicationContext, KeepAwakeService::class.java, ACTION.STOP.name, 0)
            val notificationActionNextTimeoutStr = localizedApplicationContext.getString(R.string.foreground_notification_action_next_timeout)
            val notificationActionDimmingEnabledStr = localizedApplicationContext.getString(R.string.foreground_notification_action_enable_dimming)
            val notificationActionDimmingDisabledStr = localizedApplicationContext.getString(R.string.foreground_notification_action_disable_dimming)
            val notificationActionNextTimeout = NotificationUtils.getNotificationAction(
                    localizedApplicationContext,
                    KeepAwakeService::class.java,
                    ACTION.CHANGE_TIMEOUT.name,
                    R.drawable.baseline_coffee_24,
                    notificationActionNextTimeoutStr,
                    1
            )
            val notificationActionToggleDimming = NotificationUtils.getNotificationAction(
                    this,
                    KeepAwakeService::class.java,
                    ACTION.CHANGE_DIMMING_ENABLED.name,
                    R.drawable.baseline_coffee_24,
                    if (isDimmingEnabled) notificationActionDimmingEnabledStr else notificationActionDimmingDisabledStr,
                    2
            )
            val notificationBuilder = NotificationCompat.Builder(localizedApplicationContext, channelIdStr)
            var durationStr: String? = null
            var contentTitle: String? = null

            if (status is ServiceStatus.Running) {
                durationStr = status.remaining.toFormattedTime(localizedApplicationContext)
                contentTitle = when (status.remaining) {
                    Duration.INFINITE -> localizedApplicationContext.getString(R.string.foreground_notification_title_duration_indefinite)
                    else              -> localizedApplicationContext.getString(R.string.foreground_notification_title_duration_definite, durationStr)
                }
            }
            notificationBuilder
                .setSilent(true)
                .setOngoing(true)
                .setSubText(durationStr)
                .setContentText(localizedApplicationContext.getString(R.string.foreground_notification_tap_to_turn_off))
                .setWhen(System.currentTimeMillis())
                .setContentIntent(notificationStopIntent)
                .addAction(notificationActionNextTimeout)
                .addAction(notificationActionToggleDimming)
                .setSmallIcon(R.drawable.baseline_coffee_24)
                .setContentInfo(localizedApplicationContext.getString(R.string.app_name))
                .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentTitle(contentTitle)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelIdStr, channelIdStr, NotificationManager.IMPORTANCE_HIGH)
                notificationBuilder.setChannelId(channel.id)
                notificationManager.createNotificationChannel(channel)
            }

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
        }

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
        enum class STATE {
            START,
            STOP,
            TOGGLE
        }

        private enum class ACTION {
            START,
            STOP,
            CHANGE_TIMEOUT,
            CHANGE_DIMMING_ENABLED;

            companion object {

                inline fun <reified ObjectType> valueOfOrNull(value: ObjectType) = entries.find { entry -> entry.name == value }
            }
        }

        private val DEBOUNCE_DURATION = 1.seconds

        private fun debounce(status: ServiceStatus.Running, caffeinateApplication: CaffeinateApplication) = caffeinateApplication.run {
            val state = when (status.remaining) {
                in 0.seconds..timeout - DEBOUNCE_DURATION / 2 -> STATE.STOP
                else                                          -> {
                    timeout = nextTimeout
                    if (prevTimeout == lastTimeout) STATE.STOP else STATE.START
                }
            }

            toggleState(this, state)
        }

        private fun startWithDebounce(caffeinateApplication: CaffeinateApplication) = caffeinateApplication.run {
            when (val status = lastStatusUpdate) {
                is ServiceStatus.Stopped -> toggleState(this, STATE.START)
                is ServiceStatus.Running -> debounce(status, this)
            }
        }

        private fun startWithoutDebounce(caffeinateApplication: CaffeinateApplication) = caffeinateApplication.run {
            timeout = nextTimeout
            toggleState(this, STATE.START)
        }

        fun startNextTimeout(caffeinateApplication: CaffeinateApplication, debounce: Boolean = true) = when {
            caffeinateApplication.timeoutCheckBoxes.size == 1 -> when (caffeinateApplication.lastStatusUpdate) {
                is ServiceStatus.Stopped -> startWithoutDebounce(caffeinateApplication)
                is ServiceStatus.Running -> toggleState(caffeinateApplication, STATE.STOP)
            }

            debounce                                          -> startWithDebounce(caffeinateApplication)
            else                                              -> startWithoutDebounce(caffeinateApplication)
        }

        fun startIndefinitely(caffeinateApplication: CaffeinateApplication) = caffeinateApplication.run {
            timeout = Duration.INFINITE
            toggleState(this, STATE.START)
        }

        fun toggleState(caffeinateApplication: CaffeinateApplication, newState: STATE) {
            Log.d("newState: $newState")

            caffeinateApplication.apply {
                val start = when (newState) {
                    STATE.START  -> true
                    STATE.STOP   -> false
                    STATE.TOGGLE -> lastStatusUpdate is ServiceStatus.Stopped
                }
                val status = when {
                    start -> ServiceStatus.Running(timeout)
                    else  -> ServiceStatus.Stopped
                }

                lastStatusUpdate = status
                val intent = Intent(localizedApplicationContext, KeepAwakeService::class.java).apply {
                    action = when {
                        start -> ACTION.START.name
                        else  -> ACTION.STOP.name
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