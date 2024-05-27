package com.hifnawy.caffeinate.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.hifnawy.caffeinate.CaffeineDurationSelector
import com.hifnawy.caffeinate.DurationExtensionFunctions.format
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.databinding.ActivityMainBinding
import com.hifnawy.caffeinate.services.KeepAwakeService
import java.util.Locale
import kotlin.time.Duration

class MainActivity : AppCompatActivity() {

    companion object {

        const val MAIN_ACTIVITY_ACTION_UPDATE = "main.activity.action.update"
        const val INTENT_CAFFEINE_DURATION = "caffeine.duration"
        const val INTENT_IS_CAFFEINE_STARTED = "is.caffeine.started"
        const val SHARED_PREFERENCES_ALL_PERMISSIONS_GRANTED = "all.permissions.granted"
    }

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val mainActivityKeepAwakeServiceReceiver by lazy { MainActivityKeepAwakeServiceReceiver() }
    private val sharedPreferences by lazy { getSharedPreferences(packageName, Context.MODE_PRIVATE) }
    private val caffeineDurationSelector by lazy {
        CaffeineDurationSelector(this).apply {
            caffeineDurationCallback = CaffeineDurationCallbacksImpl()
        }
    }
    private var isCaffeineStarted = false
    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DynamicColors.applyToActivityIfAvailable(this)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        Log.d(this::class.simpleName, "Build.VERSION.SDK_INT: ${Build.VERSION.SDK_INT}")

        overlayPermissionLauncher = registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
        ) {
            if (Settings.canDrawOverlays(this)) {
                // Permission granted, do your task here
                Log.d(this::class.simpleName, "Overlay permission granted")
            } else {
                // Permission not granted
                Log.d(this::class.simpleName, "Overlay permission not granted")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                    mainActivityKeepAwakeServiceReceiver,
                    IntentFilter(MAIN_ACTIVITY_ACTION_UPDATE),
                    Context.RECEIVER_EXPORTED
            )
        } else {
            registerReceiver(
                    mainActivityKeepAwakeServiceReceiver,
                    IntentFilter(MAIN_ACTIVITY_ACTION_UPDATE)
            )
        }

        binding.caffeineButton.setOnClickListener {
            val permissionsGranted =
                    sharedPreferences.getBoolean(
                            MainActivity.SHARED_PREFERENCES_ALL_PERMISSIONS_GRANTED,
                            false
                    )
            if (!permissionsGranted) return@setOnClickListener

            caffeineDurationSelector.selectNextDuration()
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    @SuppressLint("SetTextI18n")
    @Suppress("t")
    private fun checkPermissions(): Boolean {
        val requiredPermissionsCount = 4
        var permissionsGrantedCount = 0

        with(binding) {
            if (!(getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(
                        applicationContext.packageName
                )
            ) {
                batteryOptimizationCard.setOnClickListener {
                    requestBatteryOptimizationPermission()
                    // startActivity(Intent().apply {
                    //     action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    //     data = Uri.parse("package:${applicationContext.packageName}")
                    // })
                }
                batteryOptimizationTextView.text = "Battery Optimization: Not Granted!"
                batteryOptimizationImageView.setImageDrawable(
                        AppCompatResources.getDrawable(
                                root.context,
                                R.drawable.baseline_cancel_24
                        )
                )
                batteryOptimizationImageView.setColorFilter(Color.argb(255, 255, 0, 0))
            } else {
                permissionsGrantedCount++

                batteryOptimizationTextView.text = "Battery Optimization: Granted!"
                batteryOptimizationImageView.setImageDrawable(
                        AppCompatResources.getDrawable(
                                root.context,
                                R.drawable.baseline_check_circle_24
                        )
                )
                batteryOptimizationImageView.setColorFilter(Color.argb(255, 0, 255, 0))
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                if (activityManager.isBackgroundRestricted) {
                    backgroundOptimizationCard.setOnClickListener {
                        requestBatteryOptimizationPermission()
                    }
                    backgroundOptimizationTextView.text = "Background Optimization: Not Granted!"
                    backgroundOptimizationImageView.setImageDrawable(
                            AppCompatResources.getDrawable(
                                    root.context,
                                    R.drawable.baseline_cancel_24
                            )
                    )
                    backgroundOptimizationImageView.setColorFilter(Color.argb(255, 255, 0, 0))
                } else {
                    permissionsGrantedCount++

                    backgroundOptimizationTextView.text = "Background Optimization: Granted!"
                    backgroundOptimizationImageView.setImageDrawable(
                            AppCompatResources.getDrawable(
                                    root.context,
                                    R.drawable.baseline_check_circle_24
                            )
                    )
                    backgroundOptimizationImageView.setColorFilter(Color.argb(255, 0, 255, 0))
                }
            }

            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) &&
                (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            ) {
                notificationPermissionCard.setOnClickListener {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 93)
                }
                notificationPermissionTextView.text = "Notifications Permission: Not Granted!"
                notificationPermissionImageView.setImageDrawable(
                        AppCompatResources.getDrawable(
                                root.context,
                                R.drawable.baseline_cancel_24
                        )
                )
                notificationPermissionImageView.setColorFilter(Color.argb(255, 255, 0, 0))
            } else {
                permissionsGrantedCount++

                notificationPermissionTextView.text = "Notifications Permission: Granted!"
                notificationPermissionImageView.setImageDrawable(
                        AppCompatResources.getDrawable(
                                root.context,
                                R.drawable.baseline_check_circle_24
                        )
                )
                notificationPermissionImageView.setColorFilter(Color.argb(255, 0, 255, 0))
            }

            if (!Settings.canDrawOverlays(root.context)) {
                drawOverlaysCard.setOnClickListener {
                    requestDrawOverlaysPermission()
                }

                drawOverlaysTextView.text = "Draw Over Other Apps Permission: Not Granted!"
                drawOverlaysImageView.setImageDrawable(
                        AppCompatResources.getDrawable(
                                root.context,
                                R.drawable.baseline_cancel_24
                        )
                )
                drawOverlaysImageView.setColorFilter(Color.argb(255, 255, 0, 0))
            } else {
                permissionsGrantedCount++

                drawOverlaysTextView.text = "Draw Over Other Apps Permission: Granted!"
                drawOverlaysImageView.setImageDrawable(
                        AppCompatResources.getDrawable(
                                root.context,
                                R.drawable.baseline_check_circle_24
                        )
                )
                drawOverlaysImageView.setColorFilter(Color.argb(255, 0, 255, 0))
            }

            remainingTimeTextView.isEnabled = permissionsGrantedCount == requiredPermissionsCount
            remainingTimeLabelTextView.isEnabled = permissionsGrantedCount == requiredPermissionsCount
            caffeineButton.isEnabled = permissionsGrantedCount == requiredPermissionsCount
        }

        sharedPreferences.edit().putBoolean(
                SHARED_PREFERENCES_ALL_PERMISSIONS_GRANTED,
                permissionsGrantedCount == requiredPermissionsCount
        ).apply()

        return permissionsGrantedCount == requiredPermissionsCount
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(mainActivityKeepAwakeServiceReceiver)
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == 93) {
            if (grantResults.isNotEmpty() && (grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
                val snackbar = Snackbar.make(
                        binding.root,
                        "Notifications Permission Required",
                        Snackbar.LENGTH_INDEFINITE
                )

                snackbar.setAction("Go to Settings") {
                    try {
                        // Open the specific App Info page:
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:$packageName")
                        })
                    } catch (e: ActivityNotFoundException) {
                        // Open the generic Apps page:
                        val intent = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                        startActivity(intent)
                    }
                }

                snackbar.show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun requestBatteryOptimizationPermission() =
            MaterialAlertDialogBuilder(this)
                .setTitle("Battery optimization needed")
                .setIcon(R.drawable.coffee_icon)
                .setCancelable(false)
                .setMessage("This app requires the Battery Optimization permission to work properly.")
                .setPositiveButton("Ok") { _, _ ->
                    // startActivity(Intent().apply {
                    //     action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                    // })
                    startActivity(
                            Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${applicationContext.packageName}")
                            )
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()

    private fun requestDrawOverlaysPermission() =
            MaterialAlertDialogBuilder(this)
                .setTitle("Draw over other apps permission needed")
                .setIcon(R.drawable.coffee_icon)
                .setCancelable(false)
                .setMessage("This app requires the Draw over other apps permission to work properly.")
                .setPositiveButton("Ok") { _, _ ->
                    overlayPermissionLauncher.launch(
                            Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                            )
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()

    inner class CaffeineDurationCallbacksImpl : CaffeineDurationSelector.CaffeineDurationCallback {

        override fun onCaffeineStarted(duration: Duration) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(Intent(this@MainActivity, KeepAwakeService::class.java).apply {
                    action = KeepAwakeService.KEEP_AWAKE_SERVICE_ACTION_START

                    putExtra(
                            KeepAwakeService.KEEP_AWAKE_SERVICE_INTENT_EXTRA_DURATION,
                            duration.toString()
                    )
                })
            }
        }

        override fun onCaffeineStopped() {
            Log.d(this::class.simpleName, "onCaffeineStopped")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                caffeineDurationSelector.clearState()
                startForegroundService(Intent(this@MainActivity, KeepAwakeService::class.java).apply {
                    action = KeepAwakeService.KEEP_AWAKE_SERVICE_ACTION_STOP
                })
            }
        }

        @SuppressLint("SetTextI18n")
        override fun onCaffeineDurationChanged(isActive: Boolean, duration: Duration) {
            with(binding) {
                binding.remainingTimeTextView.text = duration.format()

                if (duration.format() == "∞") {
                    binding.remainingTimeTextView.setTextSize(
                            TypedValue.COMPLEX_UNIT_PX,
                            resources.getDimension(R.dimen.caffeine_duration_timer_infinity_size)
                    )
                } else {
                    binding.remainingTimeTextView.setTextSize(
                            TypedValue.COMPLEX_UNIT_PX,
                            resources.getDimension(R.dimen.caffeine_duration_timer_normal_size)
                    )
                }

                when {
                    isActive -> {
                        caffeineButton.text = "Caffeinate: ON"
                        binding.remainingTimeTextView.setTypeface(
                                binding.remainingTimeTextView.getTypeface(),
                                Typeface.NORMAL
                        )
                    }

                    else     -> {
                        caffeineButton.text = "Caffeinate: OFF"
                        binding.remainingTimeTextView.setTypeface(
                                binding.remainingTimeTextView.getTypeface(),
                                Typeface.NORMAL
                        )
                    }
                }
            }
        }
    }

    inner class MainActivityKeepAwakeServiceReceiver : BroadcastReceiver() {

        private val maxCount = 4
        private var count = 0

        @SuppressLint("SetTextI18n")
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            if (intent.action != MAIN_ACTIVITY_ACTION_UPDATE) return
            val caffeineDurationStr = intent.getStringExtra(INTENT_CAFFEINE_DURATION) ?: return
            val caffeineDuration = Duration.parse(caffeineDurationStr).format()
            val id = intent.getStringExtra("ID") ?: return

            isCaffeineStarted = intent.getBooleanExtra(INTENT_IS_CAFFEINE_STARTED, false)

            Log.d(
                    this::class.simpleName,
                    "id: $id, caffeineDuration: $caffeineDuration, isCaffeineStarted: $isCaffeineStarted"
            )

            binding.remainingTimeTextView.text = caffeineDuration

            if (caffeineDuration == "∞") {
                binding.remainingTimeTextView.setTextSize(
                        TypedValue.COMPLEX_UNIT_PX,
                        resources.getDimension(R.dimen.caffeine_duration_timer_infinity_size)
                )
            } else {
                binding.remainingTimeTextView.setTextSize(
                        TypedValue.COMPLEX_UNIT_PX,
                        resources.getDimension(R.dimen.caffeine_duration_timer_normal_size)
                )
            }

            with(binding) {
                when {
                    isCaffeineStarted -> {
                        count = (count + 1) % maxCount
                        var dots = ""
                        repeat(count) {
                            dots += "."
                        }
                        caffeineButton.text = "Caffeinate: ON$dots"
                        binding.remainingTimeTextView.setTypeface(
                                binding.remainingTimeTextView.getTypeface(),
                                Typeface.NORMAL
                        )
                    }

                    else              -> {
                        caffeineButton.text = "Caffeinate: OFF"
                        binding.remainingTimeTextView.setTypeface(
                                binding.remainingTimeTextView.getTypeface(),
                                Typeface.NORMAL
                        )
                    }
                }
            }
        }
    }
}