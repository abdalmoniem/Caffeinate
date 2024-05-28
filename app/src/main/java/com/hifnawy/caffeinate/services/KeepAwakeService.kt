package com.hifnawy.caffeinate.services

import android.annotation.SuppressLint
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
import com.hifnawy.caffeinate.DurationExtensionFunctions.format
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.ui.MainActivity
import java.util.Timer
import java.util.TimerTask
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class KeepAwakeService : Service() {

    override fun onBind(intent: Intent): IBinder? = null

    companion object {

        const val KEEP_AWAKE_SERVICE_ACTION_START = "caffeinate.action"
        const val KEEP_AWAKE_SERVICE_ACTION_STOP = "caffeinate.stop"
        const val KEEP_AWAKE_SERVICE_INTENT_EXTRA_DURATION = "caffeinate.duration"
        const val SHARED_PREFS_IS_CAFFEINE_STARTED = "shared.prefs.is.caffeine.started"
        const val SHARED_PREFS_CAFFEINE_DURATION = "shared.prefs.caffeine.duration"
        private const val NOTIFICATION_ID = 23
    }

    @Suppress("PrivatePropertyName")
    private val LOG_TAG = this::class.simpleName
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val sharedPreferences by lazy { getSharedPreferences(packageName, Context.MODE_PRIVATE) }
    private val screenLockReceiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d(LOG_TAG, "screenLockReceiverOnReceive(), Screen Locked, Stopping...")

                stopCaffeine()
            }
        }
    }
    private var isScreenLockReceiverRegistered = false
    private var selectedDuration = 0.minutes
    private var isCaffeineStarted = false
    private var isServiceStarted = false
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var caffeineTimer: Timer
    private lateinit var caffeineTimerTask: TimerTask

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        if (intent.action == null) return START_NOT_STICKY

        showForegroundNotification()
        updateQuickTile()
        updateApp()

        Log.d(LOG_TAG, "onStartCommand(), action: ${intent.action}")

        when (intent.action) {
            KEEP_AWAKE_SERVICE_ACTION_START -> {
                registerScreenLockReceiver()
                val durationStr = intent.getStringExtra(KEEP_AWAKE_SERVICE_INTENT_EXTRA_DURATION) ?: return START_NOT_STICKY

                selectedDuration = Duration.parse(durationStr)

                startCaffeine(selectedDuration == Duration.INFINITE)
            }

            KEEP_AWAKE_SERVICE_ACTION_STOP  -> {
                stopCaffeine()
            }
        }

        Log.d(LOG_TAG, "onStartCommand(), isCaffeineStarted: $isCaffeineStarted, selectedDuration: ${selectedDuration.format()}")

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.d(LOG_TAG, "onDestroy(), stopping caffeine...")
        stopCaffeine()
    }

    private fun registerScreenLockReceiver() {
        if (!isScreenLockReceiverRegistered) {
            Log.d(LOG_TAG, "registerScreenLockReceiver(), registering ${this::screenLockReceiver.name}...")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenLockReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_EXPORTED)
            } else {
                registerReceiver(screenLockReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            }

            isScreenLockReceiverRegistered = true

            Log.d(LOG_TAG, "registerScreenLockReceiver(), ${this::screenLockReceiver.name} registered!")
        }
    }

    private fun showForegroundNotification() {
        val isActive = isCaffeineStarted || (selectedDuration > 0.minutes)
        val channelIdStr = "${getString(R.string.app_name)} Status"
        val durationStr = if (isActive) "Duration: ${selectedDuration.format()}" else "Off"
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
        if (!isServiceStarted) {
            startForeground(NOTIFICATION_ID, notificationBuilder.build())
            isServiceStarted = true
        } else {
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
        }
    }

    @SuppressLint("SetTextI18n", "WakelockTimeout")
    private fun startCaffeine(indefinitely: Boolean = false) {
        Log.d(LOG_TAG, "startCaffeine(), indefinitely: $indefinitely")

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            @Suppress("DEPRECATION")
            newWakeLock(PowerManager.FULL_WAKE_LOCK, "${getString(R.string.app_name)}:wakelockTag").apply { acquire() }
        }

        caffeineTimerTask = object : TimerTask() {
            override fun run() {
                if (isCaffeineStarted) {
                    Log.d(
                            LOG_TAG,
                            "caffeineTimerTask(), indefinitely: $indefinitely, selectedDuration: ${selectedDuration.format()}, isCaffeineStarted:$isCaffeineStarted"
                    )

                    showForegroundNotification()
                    updateQuickTile()
                    updateApp()

                    if (!indefinitely) {
                        selectedDuration -= 1.seconds

                        if (selectedDuration <= 0.minutes) {
                            stopCaffeine()
                        }
                    }

                    sharedPreferences.edit().putString(SHARED_PREFS_CAFFEINE_DURATION, selectedDuration.toString()).apply()
                }
            }
        }

        caffeineTimer = Timer()
        caffeineTimer.schedule(caffeineTimerTask, 0, 1000.milliseconds.inWholeMilliseconds)

        isCaffeineStarted = true

        sharedPreferences.edit().putBoolean(SHARED_PREFS_IS_CAFFEINE_STARTED, isCaffeineStarted).apply()

        updateQuickTile()
        updateApp()

        Log.d(LOG_TAG, "startCaffeine(), ${getString(R.string.app_name)} started with duration: ${selectedDuration.format()}")
    }

    @SuppressLint("SetTextI18n")
    private fun stopCaffeine() {
        if (isCaffeineStarted) {
            if (this::wakeLock.isInitialized && wakeLock.isHeld) {
                Log.d(LOG_TAG, "stopCaffeine(), releasing ${this::wakeLock.name}...")
                wakeLock.release()
                Log.d(LOG_TAG, "stopCaffeine(), ${this::wakeLock.name} released!")
            }

            if (this::caffeineTimerTask.isInitialized) caffeineTimerTask.cancel()

            if (isScreenLockReceiverRegistered) {
                Log.d(LOG_TAG, "stopCaffeine(), unregistering ${this::screenLockReceiver.name}...")

                unregisterReceiver(screenLockReceiver)
                isScreenLockReceiverRegistered = false

                Log.d(LOG_TAG, "stopCaffeine(), ${this::screenLockReceiver.name} unregistered!")
            }

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()

            Log.d(LOG_TAG, "stopCaffeine(), ${getString(R.string.app_name)} Stopped!")
        } else {
            Log.d(LOG_TAG, "stopCaffeine(), ${getString(R.string.app_name)} is Already Stopped!")
        }

        isCaffeineStarted = false
        selectedDuration = 0.minutes

        sharedPreferences.edit().putBoolean(SHARED_PREFS_IS_CAFFEINE_STARTED, isCaffeineStarted).apply()
        sharedPreferences.edit().putString(SHARED_PREFS_CAFFEINE_DURATION, selectedDuration.toString()).apply()

        updateQuickTile()
        updateApp()
    }

    private fun updateQuickTile() {
        Intent(QuickTileService.QUICK_TILE_ACTION_UPDATE).apply {
            putExtra("ID", "updateQuickTile()")
            putExtra(QuickTileService.INTENT_QUICK_TILE_IS_ACTIVE_EXTRA, isCaffeineStarted)
            putExtra(QuickTileService.INTENT_QUICK_TILE_DURATION_EXTRA, selectedDuration.toString())

            sendBroadcast(this)
        }
    }

    private fun updateApp() {
        Intent(MainActivity.MAIN_ACTIVITY_ACTION_UPDATE).apply {
            putExtra("ID", "updateApp()")
            putExtra(MainActivity.INTENT_IS_CAFFEINE_STARTED, isCaffeineStarted)
            putExtra(MainActivity.INTENT_CAFFEINE_DURATION, selectedDuration.toString())

            sendBroadcast(this)
        }
    }
}