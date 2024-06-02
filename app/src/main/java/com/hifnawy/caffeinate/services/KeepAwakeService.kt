package com.hifnawy.caffeinate.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.ServiceStatus
import com.hifnawy.caffeinate.ServiceStatusObserver
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toFormattedTime
import com.hifnawy.caffeinate.utils.MutableListExtensionFunctions.addObserver
import com.hifnawy.caffeinate.utils.NotificationUtils
import com.hifnawy.caffeinate.utils.SharedPrefsManager
import com.hifnawy.caffeinate.utils.WakeLockExtensionFunctions.releaseSafely
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class KeepAwakeService : Service(), SharedPrefsManager.SharedPrefsChangedListener, ServiceStatusObserver {

    override fun onBind(intent: Intent): IBinder? = null

    private val caffeinateApplication by lazy { application as CaffeinateApplication }
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val sharedPreferences by lazy { SharedPrefsManager(caffeinateApplication) }
    private val screenLockReceiver by lazy { ScreenLockStateReceiver() }
    private var isScreenLockReceiverRegistered = false
    private var isDimmingEnabled = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var caffeineTimer: Timer? = null
    private var caffeineTimerTask: TimerTask? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val status = caffeinateApplication.lastStatusUpdate as ServiceStatus.Running

        isDimmingEnabled = sharedPreferences.isDimmingEnabled

        Log.d(LOG_TAG, "${::onStartCommand.name}() -> intent.action: ${intent?.action}")
        when (intent?.action) {
            ACTION_STOP                   -> {
                toggleState(caffeinateApplication, STATE.STOP)
                return START_STICKY
            }

            ACTION_CHANGE_TIMEOUT         -> nextTimeout(caffeinateApplication)
            ACTION_CHANGE_DIMMING_ENABLED -> sharedPreferences.isDimmingEnabled = !sharedPreferences.isDimmingEnabled
        }

        Log.d(LOG_TAG, "${::onStartCommand.name}() -> status: $status, selectedDuration: ${status.remaining.toFormattedTime()}")

        Log.d(LOG_TAG, "${::onStartCommand.name}() -> sending foreground notification...")
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        Log.d(LOG_TAG, "${::onStartCommand.name}() -> foreground notification sent!")

        Log.d(LOG_TAG, "${::onStartCommand.name}() -> adding ${this::class.simpleName} to ${CaffeinateApplication::keepAwakeServiceObservers.name}...")
        caffeinateApplication.keepAwakeServiceObservers.addObserver(caffeinateApplication::keepAwakeServiceObservers.name, this)
        Log.d(LOG_TAG, "${::onStartCommand.name}() -> ${this::class.simpleName} added to ${CaffeinateApplication::keepAwakeServiceObservers.name}!")

        Log.d(LOG_TAG, "${::onStartCommand.name}() -> adding ${this::class.simpleName} to ${CaffeinateApplication::sharedPrefsObservers.name}...")
        caffeinateApplication.sharedPrefsObservers.addObserver(caffeinateApplication::sharedPrefsObservers.name, this)
        Log.d(LOG_TAG, "${::onStartCommand.name}() -> ${this::class.simpleName} added to ${CaffeinateApplication::sharedPrefsObservers.name}!")


        if (!sharedPreferences.isWhileLockedEnabled) registerScreenLockReceiver()
        startCaffeine(status.remaining)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.d(LOG_TAG, "${::onDestroy.name}() -> stopping ${getString(R.string.app_name)}...")
        stopCaffeine()
    }

    override fun onIsAllPermissionsGrantedChanged(isAllPermissionsGranted: Boolean) = Unit

    override fun onIsDimmingEnabledChanged(isDimmingEnabled: Boolean) {
        Log.d(LOG_TAG, "${::onIsDimmingEnabledChanged.name}() -> isDimmingEnabled: $isDimmingEnabled")

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
        Log.d(LOG_TAG, "${::onIsWhileLockedEnabledChanged.name}() -> isWhileLockedEnabled: $isWhileLockedEnabled")

        when (caffeinateApplication.lastStatusUpdate) {
            is ServiceStatus.Running -> if (isWhileLockedEnabled) unregisterScreenLockReceiver() else registerScreenLockReceiver()
            is ServiceStatus.Stopped -> Unit
        }
    }

    override fun onServiceStatusUpdate(status: ServiceStatus) {
        when (status) {
            is ServiceStatus.Running -> {
                Log.d(
                        LOG_TAG,
                        "${::onServiceStatusUpdate.name}() -> " +
                        "duration: ${status.remaining.toFormattedTime()}, status: $status, isIndefinite: ${status.remaining == Duration.INFINITE}"
                )
                notificationManager.notify(NOTIFICATION_ID, buildForegroundNotification())
            }

            else                     -> stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun registerScreenLockReceiver() {
        if (!isScreenLockReceiverRegistered) {
            Log.d(LOG_TAG, "${::registerScreenLockReceiver.name}() -> registering ${this::screenLockReceiver.name}...")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenLockReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_EXPORTED)
            } else {
                registerReceiver(screenLockReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            }

            isScreenLockReceiverRegistered = true

            Log.d(LOG_TAG, "${::registerScreenLockReceiver.name}() -> ${this::screenLockReceiver.name} registered!")
        }
    }

    private fun unregisterScreenLockReceiver() {
        if (isScreenLockReceiverRegistered) {
            Log.d(LOG_TAG, "${::unregisterScreenLockReceiver.name}() -> unregistering ${this::screenLockReceiver.name}...")
            unregisterReceiver(screenLockReceiver)
            isScreenLockReceiverRegistered = false
            Log.d(LOG_TAG, "${::unregisterScreenLockReceiver.name}() -> ${this::screenLockReceiver.name} unregistered!")
        }
    }

    private fun buildForegroundNotification(): Notification {
        val status = caffeinateApplication.lastStatusUpdate as ServiceStatus.Running
        val channelIdStr = "${getString(R.string.app_name)} Status"
        val durationStr = status.remaining.toFormattedTime()
        val notificationStopIntent = NotificationUtils.getPendingIntent(this, KeepAwakeService::class.java, ACTION_STOP, 0)
        val notificationActionNextTimeout = NotificationUtils.getNotificationAction(
                this,
                KeepAwakeService::class.java,
                ACTION_CHANGE_TIMEOUT,
                R.drawable.baseline_coffee_24,
                getString(R.string.foreground_notification_action_next_timeout),
                1
        )
        val notificationActionEnableDimming = NotificationUtils.getNotificationAction(
                this,
                KeepAwakeService::class.java,
                ACTION_CHANGE_DIMMING_ENABLED,
                R.drawable.baseline_coffee_24,
                if (isDimmingEnabled) getString(R.string.foreground_notification_action_disable_dimming) else getString(R.string.foreground_notification_action_enable_dimming),
                2
        )
        val notificationBuilder =
                NotificationCompat.Builder(this, channelIdStr)
                    .setSilent(true)
                    .setOngoing(true)
                    .setSubText(durationStr)
                    .setContentText(getString(R.string.foreground_notification_tap_to_turn_off))
                    .setWhen(System.currentTimeMillis())
                    .setContentIntent(notificationStopIntent)
                    .addAction(notificationActionNextTimeout)
                    .addAction(notificationActionEnableDimming)
                    .setSmallIcon(R.drawable.baseline_coffee_24)
                    .setContentInfo(getString(R.string.app_name))
                    .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
                    .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                    .setContentTitle(
                            when (status.remaining) {
                                Duration.INFINITE -> getString(R.string.foreground_notification_title_duration_indefinite)
                                else              -> getString(R.string.foreground_notification_title_duration_definite, durationStr)
                            }
                    )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelIdStr, channelIdStr, NotificationManager.IMPORTANCE_HIGH)
            notificationBuilder.setChannelId(channel.id)
            notificationManager.createNotificationChannel(channel)
        }

        return notificationBuilder.build()
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock(duration: Duration) {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        wakeLock?.apply {
            Log.d(LOG_TAG, "${::acquireWakeLock.name}() -> releasing ${this@KeepAwakeService::wakeLock.name}...")
            releaseSafely(::wakeLock.name)
            Log.d(LOG_TAG, "${::acquireWakeLock.name}() -> ${this@KeepAwakeService::wakeLock.name} released!")
        } ?: Log.d(LOG_TAG, "${::acquireWakeLock.name}() -> wakeLock is not held!")
        @Suppress("DEPRECATION")
        val wakeLockLevel = when {
            isDimmingEnabled -> {
                Log.d(LOG_TAG, "${::acquireWakeLock.name}() -> using ${PowerManager::SCREEN_DIM_WAKE_LOCK.name}")
                PowerManager.SCREEN_DIM_WAKE_LOCK
            }

            else             -> {
                Log.d(LOG_TAG, "${::acquireWakeLock.name}() -> using ${PowerManager::SCREEN_BRIGHT_WAKE_LOCK.name}")
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK
            }
        }

        wakeLock = powerManager.newWakeLock(wakeLockLevel, "${getString(R.string.app_name)}:wakelockTag").apply {
            Log.d(LOG_TAG, "${::acquireWakeLock.name}() -> acquiring ${this@KeepAwakeService::wakeLock.name}, isDimmingAllowed: $isDimmingEnabled...")
            setReferenceCounted(false)
            if (duration == Duration.INFINITE) acquire() else acquire(duration.inWholeMilliseconds)
            Log.d(LOG_TAG, "${::acquireWakeLock.name}() -> ${this@KeepAwakeService::wakeLock.name} acquired, isDimmingAllowed: $isDimmingEnabled!")
        }
    }

    private fun startCaffeine(duration: Duration) {
        val isIndefinite = duration == Duration.INFINITE
        Log.d(LOG_TAG, "${::acquireWakeLock.name}() -> starting ${getString(R.string.app_name)} with duration: ${duration.toFormattedTime()}, isIndefinite: $isIndefinite")

        acquireWakeLock(duration)

        caffeineTimer?.apply {
            Log.d(LOG_TAG, "${::startCaffeine.name}() -> cancelling ${this@KeepAwakeService::caffeineTimerTask.name}...")
            cancel()
            Log.d(LOG_TAG, "${::startCaffeine.name}() -> ${this@KeepAwakeService::caffeineTimerTask.name} cancelled!")
        }

        Log.d(LOG_TAG, "${::startCaffeine.name}() -> creating ${this::caffeineTimerTask.name}...")
        caffeineTimerTask = CaffeineTimerTask(duration)
        Log.d(LOG_TAG, "${::startCaffeine.name}() -> ${this::caffeineTimerTask.name} created!")

        caffeineTimer = Timer().apply {
            Log.d(LOG_TAG, "${::startCaffeine.name}() -> scheduling ${this@KeepAwakeService::caffeineTimerTask.name}...")
            schedule(caffeineTimerTask, DEBOUNCE_DURATION.inWholeMilliseconds, 1000.milliseconds.inWholeMilliseconds)
            Log.d(LOG_TAG, "${::startCaffeine.name}() -> ${this@KeepAwakeService::caffeineTimerTask.name} scheduled!")
        }
    }

    private fun stopCaffeine() {
        Log.d(LOG_TAG, "${::stopCaffeine.name}() -> stopping ${getString(R.string.app_name)}...")

        wakeLock?.apply {
            Log.d(LOG_TAG, "${::stopCaffeine.name}() -> releasing ${this@KeepAwakeService::wakeLock.name}...")
            releaseSafely(::wakeLock.name)
            Log.d(LOG_TAG, "${::stopCaffeine.name}() -> ${this@KeepAwakeService::wakeLock.name} released!")
        } ?: Log.d(LOG_TAG, "${::stopCaffeine.name}() -> wakeLock is not held!")

        Log.d(LOG_TAG, "${::stopCaffeine.name}() -> cancelling ${this::caffeineTimerTask.name}...")
        caffeineTimerTask?.cancel()
        Log.d(LOG_TAG, "${::stopCaffeine.name}() -> ${this::caffeineTimerTask.name} cancelled!")

        unregisterScreenLockReceiver()

        with(caffeinateApplication) {
            Log.d(LOG_TAG, "${::stopCaffeine.name}: removing $LOG_TAG from ${CaffeinateApplication::keepAwakeServiceObservers.name}...")
            keepAwakeServiceObservers.remove(this@KeepAwakeService)
            Log.d(LOG_TAG, "${::stopCaffeine.name}() -> $LOG_TAG removed from ${CaffeinateApplication::keepAwakeServiceObservers.name}!")

            Log.d(LOG_TAG, "${::stopCaffeine.name}: removing $LOG_TAG from ${CaffeinateApplication::sharedPrefsObservers.name}...")
            sharedPrefsObservers.remove(this@KeepAwakeService)
            Log.d(LOG_TAG, "${::stopCaffeine.name}() -> $LOG_TAG removed from ${CaffeinateApplication::sharedPrefsObservers.name}!")

            Log.d(LOG_TAG, "${::stopCaffeine.name}() -> notifying observers...")
            caffeinateApplication.lastStatusUpdate = ServiceStatus.Stopped
            Log.d(LOG_TAG, "${::stopCaffeine.name}() -> observers notified!")
        }

        Log.d(LOG_TAG, "${::stopCaffeine.name}() -> ${getString(R.string.app_name)} stopped!")
    }

    private inner class CaffeineTimerTask(private var duration: Duration) : TimerTask() {

        @Suppress("PrivatePropertyName")
        private val LOG_TAG = CaffeineTimerTask::class.simpleName
        private val isIndefinite = duration == Duration.INFINITE

        init {
            Log.d(LOG_TAG, "$LOG_TAG::init: ${getString(R.string.app_name)} initialized with duration: ${duration.toFormattedTime()}, isIndefinite: $isIndefinite")
        }

        override fun run() {
            CoroutineScope(Dispatchers.Main).launch {
                caffeinateApplication.apply {
                    when (val status = lastStatusUpdate) {
                        is ServiceStatus.Running -> {
                            if (!isIndefinite) duration -= 1.seconds

                            Log.d(
                                    LOG_TAG,
                                    "$LOG_TAG::${this@CaffeineTimerTask::run.name}() -> " + "duration: ${status.remaining.toFormattedTime()}, status: $status, isIndefinite: $isIndefinite"
                            )

                            when {
                                duration <= 0.minutes -> toggleState(this, STATE.STOP)
                                else                  -> lastStatusUpdate = ServiceStatus.Running(duration)
                            }
                        }

                        else                     -> toggleState(this, STATE.STOP)
                    }
                }
            }
        }
    }

    private inner class ScreenLockStateReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            Log.d(LOG_TAG, "${this::class.simpleName}::${::onReceive.name}() -> Screen Locked, Stopping...")
            toggleState(caffeinateApplication, KeepAwakeService.Companion.STATE.STOP)
        }
    }

    companion object {
        private enum class STATE {
            START,
            STOP,
            TOGGLE
        }

        private const val ACTION_STOP = "stop_action"
        private const val ACTION_CHANGE_TIMEOUT = "change_timeout"
        private const val ACTION_CHANGE_DIMMING_ENABLED = "change_allow_dimming"
        private val DEBOUNCE_DURATION = 1.seconds
        private val LOG_TAG = KeepAwakeService::class.simpleName

        fun startIndefinitely(caffeinateApplication: CaffeinateApplication) {
            caffeinateApplication.apply {
                timeout = Duration.INFINITE
                toggleState(this, STATE.START)
            }
        }

        fun startNextDuration(caffeinateApplication: CaffeinateApplication) {
            caffeinateApplication.apply {
                when (val status = lastStatusUpdate) {
                    is ServiceStatus.Running -> debounceNextDuration(status, this)
                    else                     -> toggleState(this, STATE.START)
                }
            }
        }

        private fun debounceNextDuration(status: ServiceStatus.Running, caffeinateApplication: CaffeinateApplication) {
            caffeinateApplication.apply {
                val state = when (status.remaining) {
                    in 0.seconds..timeout - DEBOUNCE_DURATION / 2 -> STATE.STOP
                    else                                          -> {
                        timeout = nextTimeout
                        if (prevTimeout == Duration.INFINITE) STATE.STOP else STATE.START
                    }
                }

                toggleState(this, state)
            }
        }

        private fun nextTimeout(caffeinateApplication: CaffeinateApplication) {
            caffeinateApplication.apply {
                timeout = nextTimeout

                toggleState(this, STATE.START)
            }
        }

        private fun toggleState(caffeinateApplication: CaffeinateApplication, newState: STATE) {
            Log.d(LOG_TAG, "${::toggleState.name}() -> newState: $newState")

            caffeinateApplication.apply {
                val start = when (newState) {
                    STATE.START  -> true
                    STATE.STOP   -> false
                    STATE.TOGGLE -> lastStatusUpdate is ServiceStatus.Stopped
                }
                val intent = Intent(applicationContext, KeepAwakeService::class.java)
                val status = when {
                    start -> ServiceStatus.Running(timeout)
                    else  -> ServiceStatus.Stopped
                }

                lastStatusUpdate = status

                when {
                    start -> ContextCompat.startForegroundService(applicationContext, intent)
                    else  -> applicationContext.stopService(intent)
                }
            }
        }

        private const val NOTIFICATION_ID = 23
    }
}
