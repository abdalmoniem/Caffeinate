package com.hifnawy.caffeinate.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import com.hifnawy.caffeinate.ui.MainActivity
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toFormattedTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class KeepAwakeService : Service(), ServiceStatusObserver {

    override fun onBind(intent: Intent): IBinder? = null

    private val caffeinateApplication by lazy { application as CaffeinateApplication }
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val screenLockReceiver by lazy { ScreenLockStateReceiver() }
    private var isScreenLockReceiverRegistered = false
    private var caffeineTimer: Timer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var caffeineTimerTask: TimerTask? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val status = caffeinateApplication.lastStatusUpdate as ServiceStatus.Running

        Log.d(LOG_TAG, "onStartCommand(): status: $status, selectedDuration: ${status.remaining.toFormattedTime()}")

        Log.d(LOG_TAG, "adding ${this::class.simpleName} to observers...")
        caffeinateApplication.observers.add(this)
        Log.d(LOG_TAG, "${this::class.simpleName} added to observers!")

        registerScreenLockReceiver()
        startCaffeine(status.remaining)

        Log.d(LOG_TAG, "onStartCommand(): sending foreground notification...")
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        Log.d(LOG_TAG, "onStartCommand(): foreground notification sent!")

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.d(LOG_TAG, "onDestroy(): stopping ${getString(R.string.app_name)}...")
        stopCaffeine()
    }

    override fun onServiceStatusUpdate(status: ServiceStatus) {
        when (status) {
            is ServiceStatus.Running -> notificationManager.notify(NOTIFICATION_ID, buildForegroundNotification())
            else                     -> stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun registerScreenLockReceiver() {
        if (!isScreenLockReceiverRegistered) {
            Log.d(LOG_TAG, "registerScreenLockReceiver(): registering ${this::screenLockReceiver.name}...")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenLockReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_EXPORTED)
            } else {
                registerReceiver(screenLockReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            }

            isScreenLockReceiverRegistered = true

            Log.d(LOG_TAG, "registerScreenLockReceiver(): ${this::screenLockReceiver.name} registered!")
        }
    }

    private fun buildForegroundNotification(): Notification {
        val status = caffeinateApplication.lastStatusUpdate
        val channelIdStr = "${getString(R.string.app_name)} Status"
        val durationStr = if (status is ServiceStatus.Running) "Duration: ${status.remaining.toFormattedTime()}" else "Off"
        val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationBuilder =
                NotificationCompat.Builder(this, channelIdStr)
                    .setSilent(true)
                    .setOngoing(true)
                    .setSubText(durationStr)
                    .setContentText(durationStr)
                    .setContentIntent(pendingIntent)
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(R.drawable.baseline_coffee_24)
                    .setContentInfo(getString(R.string.app_name))
                    .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
                    .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelIdStr, channelIdStr, NotificationManager.IMPORTANCE_HIGH)
            notificationBuilder.setChannelId(channel.id)
            notificationManager.createNotificationChannel(channel)
        }

        return notificationBuilder.build()
    }

    @SuppressLint("WakelockTimeout")
    private fun startCaffeine(duration: Duration) {
        val isIndefinite = duration == Duration.INFINITE
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        Log.d(
                LOG_TAG,
                "startCaffeine(): starting ${getString(R.string.app_name)} with duration: ${duration.toFormattedTime()}, isIndefinite: $isIndefinite"
        )

        wakeLock?.apply {
            if (isHeld) {
                Log.d(LOG_TAG, "startCaffeine(): releasing ${this@KeepAwakeService::wakeLock.name}...")
                release()
                Log.d(LOG_TAG, "startCaffeine(): ${this@KeepAwakeService::wakeLock.name} released!")
            } else {
                Log.d(LOG_TAG, "startCaffeine(): ${this@KeepAwakeService::wakeLock.name} is already released!")
            }
        }

        Log.d(LOG_TAG, "startCaffeine(): acquiring ${this::wakeLock.name}...")
        @Suppress("DEPRECATION")
        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "${getString(R.string.app_name)}:wakelockTag")
            .apply { acquire(duration.inWholeMilliseconds) }
        Log.d(LOG_TAG, "startCaffeine(): ${this::wakeLock.name} acquired!")

        caffeineTimer?.apply {
            Log.d(LOG_TAG, "startCaffeine(): cancelling ${this@KeepAwakeService::caffeineTimerTask.name}...")
            cancel()
            Log.d(LOG_TAG, "startCaffeine(): ${this@KeepAwakeService::caffeineTimerTask.name} cancelled!")
        }

        Log.d(LOG_TAG, "creating ${this::caffeineTimerTask.name}...")
        caffeineTimerTask = CaffeineTimerTask(duration)
        Log.d(LOG_TAG, "${this::caffeineTimerTask.name} created!")

        caffeineTimer = Timer().apply {
            Log.d(LOG_TAG, "startCaffeine(): scheduling ${this@KeepAwakeService::caffeineTimerTask.name}...")
            schedule(caffeineTimerTask, DEBOUNCE_DURATION.inWholeMilliseconds, 1000.milliseconds.inWholeMilliseconds)
            Log.d(LOG_TAG, "startCaffeine(): ${this@KeepAwakeService::caffeineTimerTask.name} scheduled!")
        }
    }

    private fun stopCaffeine() {
        Log.d(LOG_TAG, "stopCaffeine(): stopping ${getString(R.string.app_name)}...")

        wakeLock?.apply {
            if (isHeld) {
                Log.d(LOG_TAG, "stopCaffeine(): releasing ${this@KeepAwakeService::wakeLock.name}...")
                release()
                Log.d(LOG_TAG, "stopCaffeine(): ${this@KeepAwakeService::wakeLock.name} released!")
            } else {
                Log.d(LOG_TAG, "startCaffeine(): ${this@KeepAwakeService::wakeLock.name} is already released!")
            }
        }

        Log.d(LOG_TAG, "stopCaffeine(): cancelling ${this::caffeineTimerTask.name}...")
        caffeineTimerTask?.cancel()
        Log.d(LOG_TAG, "stopCaffeine(): ${this::caffeineTimerTask.name} cancelled!")

        if (isScreenLockReceiverRegistered) {
            Log.d(LOG_TAG, "stopCaffeine(): unregistering ${this::screenLockReceiver.name}...")
            unregisterReceiver(screenLockReceiver)
            isScreenLockReceiverRegistered = false
            Log.d(LOG_TAG, "stopCaffeine(): ${this::screenLockReceiver.name} unregistered!")
        }

        with(caffeinateApplication) {
            Log.d(LOG_TAG, "removing ${this@KeepAwakeService::class.simpleName} from observers...")
            observers.remove(this@KeepAwakeService)
            Log.d(LOG_TAG, "${this@KeepAwakeService::class.simpleName} removed from observers!")

            Log.d(LOG_TAG, "stopCaffeine(): notifying observers...")
            notifyObservers(ServiceStatus.Stopped)
            Log.d(LOG_TAG, "stopCaffeine(): observers notified!")
        }

        Log.d(LOG_TAG, "stopCaffeine(): ${getString(R.string.app_name)} stopped!")
    }

    inner class CaffeineTimerTask(private var duration: Duration) : TimerTask() {

        private val isIndefinite = duration == Duration.INFINITE

        init {
            Log.d(
                    LOG_TAG,
                    "${this::class.simpleName}::init: ${getString(R.string.app_name)} initialized with duration: ${duration.toFormattedTime()}, isIndefinite: $isIndefinite"
            )
        }

        override fun run() {
            CoroutineScope(Dispatchers.Main).launch {
                when (val status = caffeinateApplication.lastStatusUpdate) {
                    is ServiceStatus.Running -> {
                        if (!isIndefinite) duration -= 1.seconds
                        when {
                            duration <= 0.minutes -> toggleState(this@KeepAwakeService, STATE.STOP)
                            else                  -> caffeinateApplication.notifyObservers(ServiceStatus.Running(duration))
                        }

                        Log.d(
                                LOG_TAG,
                                "CaffeineTimerTask::run(): duration: ${status.remaining.toFormattedTime()}, status: $status, isIndefinite: $isIndefinite"
                        )
                    }

                    else                     -> toggleState(this@KeepAwakeService, STATE.STOP)
                }
            }
        }
    }

    private class ScreenLockStateReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            Log.d(LOG_TAG, "screenLockReceiverOnReceive(): Screen Locked, Stopping...")
            toggleState(context, KeepAwakeService.Companion.STATE.STOP)
        }
    }

    companion object {
        private enum class STATE {
            START,
            STOP,
            TOGGLE
        }

        private val DEBOUNCE_DURATION = 1.seconds
        private val LOG_TAG = KeepAwakeService::class.simpleName

        fun startNextDuration(caffeinateApplication: CaffeinateApplication) {
            when (val status = caffeinateApplication.lastStatusUpdate) {
                is ServiceStatus.Running -> debounceNextDuration(status, caffeinateApplication)
                else                     -> toggleState(caffeinateApplication.applicationContext, STATE.START)
            }
        }

        private fun debounceNextDuration(status: ServiceStatus.Running, caffeinateApplication: CaffeinateApplication) {
            with(caffeinateApplication) {
                val state = when (status.remaining) {
                    in 0.seconds..timeout - DEBOUNCE_DURATION / 2 -> STATE.STOP
                    else                                          -> {
                        timeout = nextTimeout
                        if (prevTimeout == Duration.INFINITE) STATE.STOP else STATE.START
                    }
                }

                toggleState(applicationContext, state)
            }
        }

        private fun toggleState(context: Context, newState: STATE) {
            Log.d(LOG_TAG, "toggleState() : newState: $newState")
            val caffeinateApplication = context.applicationContext as CaffeinateApplication
            val start = when (newState) {
                STATE.START  -> true
                STATE.STOP   -> false
                STATE.TOGGLE -> caffeinateApplication.lastStatusUpdate is ServiceStatus.Stopped
            }
            val intent = Intent(context, KeepAwakeService::class.java)
            val status = when {
                start -> ServiceStatus.Running(caffeinateApplication.timeout)
                else  -> ServiceStatus.Stopped
            }

            caffeinateApplication.notifyObservers(status)

            when {
                start -> ContextCompat.startForegroundService(context, intent)
                else  -> context.stopService(intent)
            }
        }

        private const val NOTIFICATION_ID = 23
    }
}
