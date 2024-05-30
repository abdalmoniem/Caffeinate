package com.hifnawy.caffeinate.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.ServiceStatus
import com.hifnawy.caffeinate.ServiceStatusObserver
import com.hifnawy.caffeinate.databinding.ActivityMainBinding
import com.hifnawy.caffeinate.services.KeepAwakeService
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toFormattedTime

class MainActivity : AppCompatActivity(), ServiceStatusObserver {

    companion object {

        const val SHARED_PREFERENCES_ALL_PERMISSIONS_GRANTED = "all.permissions.granted"
    }

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val application by lazy { getApplication() as CaffeinateApplication }
    private val sharedPreferences by lazy { getSharedPreferences(packageName, Context.MODE_PRIVATE) }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DynamicColors.applyToActivityIfAvailable(this)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.caffeineButton.setOnClickListener {
            val permissionsGranted = sharedPreferences.getBoolean(SHARED_PREFERENCES_ALL_PERMISSIONS_GRANTED, false)
            if (!permissionsGranted) return@setOnClickListener

            KeepAwakeService.startNextDuration(application)
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        application.observers.add(this)
        onServiceStatusUpdate(application.lastStatusUpdate)
    }

    override fun onPause() {
        super.onPause()
        application.observers.remove(this)
    }

    @SuppressLint("SetTextI18n")
    override fun onServiceStatusUpdate(status: ServiceStatus) {
        // val textSize = if (application.timeout == Duration.INFINITE) 50f else 35f
        //
        // binding.caffeineButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
        binding.caffeineButton.text = when (status) {
            is ServiceStatus.Stopped -> "OFF"
            is ServiceStatus.Running -> status.remaining.toFormattedTime()
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == 93) {
            if (grantResults.isNotEmpty() && (grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
                val snackbar = Snackbar.make(binding.root, "Notifications Permission Required", Snackbar.LENGTH_INDEFINITE)

                snackbar.setAction("Go to Settings") {
                    try {
                        // Open the specific App Info page:
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:$packageName") })
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

    @Suppress("t")
    @SuppressLint("SetTextI18n", "BatteryLife")
    private fun checkPermissions(): Boolean {
        val requiredPermissionsCount = 3
        var permissionsGrantedCount = 0


        with(binding) {
            val notGrantedDrawable = AppCompatResources.getDrawable(root.context, R.drawable.baseline_cancel_24)
            val grantedDrawable = AppCompatResources.getDrawable(root.context, R.drawable.baseline_check_circle_24)
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager

            if (!powerManager.isIgnoringBatteryOptimizations(applicationContext.packageName)) {
                batteryOptimizationCard.setOnClickListener {
                    startActivity(Intent().apply {
                        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        data = Uri.parse("package:${applicationContext.packageName}")
                    })
                    requestBatteryOptimizationPermission()
                }
                batteryOptimizationTextView.text = "Battery Optimization: Not Granted!"
                batteryOptimizationImageView.setImageDrawable(notGrantedDrawable)
                batteryOptimizationImageView.setColorFilter(Color.argb(255, 255, 0, 0))
            } else {
                permissionsGrantedCount++

                batteryOptimizationTextView.text = "Battery Optimization: Granted!"
                batteryOptimizationImageView.setImageDrawable(grantedDrawable)
                batteryOptimizationImageView.setColorFilter(Color.argb(255, 0, 255, 0))
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                if (activityManager.isBackgroundRestricted) {
                    backgroundOptimizationCard.setOnClickListener {
                        requestBatteryOptimizationPermission()
                    }
                    backgroundOptimizationTextView.text = "Background Optimization: Not Granted!"
                    backgroundOptimizationImageView.setImageDrawable(notGrantedDrawable)
                    backgroundOptimizationImageView.setColorFilter(Color.argb(255, 255, 0, 0))
                } else {
                    permissionsGrantedCount++

                    backgroundOptimizationTextView.text = "Background Optimization: Granted!"
                    backgroundOptimizationImageView.setImageDrawable(grantedDrawable)
                    backgroundOptimizationImageView.setColorFilter(Color.argb(255, 0, 255, 0))
                }
            }

            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermissionCard.setOnClickListener { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 93) }
                    notificationPermissionTextView.text = "Notifications Permission: Not Granted!"
                    notificationPermissionImageView.setImageDrawable(notGrantedDrawable)
                    notificationPermissionImageView.setColorFilter(Color.argb(255, 255, 0, 0))
                } else {
                    permissionsGrantedCount++

                    notificationPermissionTextView.text = "Notifications Permission: Granted!"
                    notificationPermissionImageView.setImageDrawable(grantedDrawable)
                    notificationPermissionImageView.setColorFilter(Color.argb(255, 0, 255, 0))
                }
            }

            caffeineButton.isEnabled = permissionsGrantedCount == requiredPermissionsCount
        }

        sharedPreferences.edit().putBoolean(SHARED_PREFERENCES_ALL_PERMISSIONS_GRANTED, permissionsGrantedCount == requiredPermissionsCount).apply()

        return permissionsGrantedCount == requiredPermissionsCount
    }

    private fun requestBatteryOptimizationPermission() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Battery optimization needed")
            .setIcon(R.drawable.coffee_icon)
            .setCancelable(false)
            .setMessage("This app requires the Battery Optimization permission to work properly.")
            .setPositiveButton("Ok") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${applicationContext.packageName}")))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}