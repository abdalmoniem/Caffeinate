package com.hifnawy.caffeinate.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.ServiceStatus
import com.hifnawy.caffeinate.ServiceStatusObserver
import com.hifnawy.caffeinate.databinding.ActivityMainBinding
import com.hifnawy.caffeinate.databinding.DialogChooseThemeBinding
import com.hifnawy.caffeinate.databinding.DialogChooseTimeoutsBinding
import com.hifnawy.caffeinate.services.KeepAwakeService
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toLocalizedFormattedTime
import com.hifnawy.caffeinate.utils.ImageViewExtensionFunctions.setColoredImageDrawable
import com.hifnawy.caffeinate.utils.MutableListExtensionFunctions.addObserver
import com.hifnawy.caffeinate.utils.SharedPrefsManager
import com.hifnawy.caffeinate.utils.ThemeExtensionFunctions.themeColor

class MainActivity : AppCompatActivity(), SharedPrefsManager.SharedPrefsChangedListener, ServiceStatusObserver {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val caffeinateApplication by lazy { application as CaffeinateApplication }
    private val sharedPreferences by lazy { SharedPrefsManager(caffeinateApplication) }
    private val grantedDrawable by lazy { AppCompatResources.getDrawable(binding.root.context, R.drawable.baseline_check_circle_24) }
    private val notGrantedDrawable by lazy { AppCompatResources.getDrawable(binding.root.context, R.drawable.baseline_cancel_24) }
    private val displayWidth: Int
        get() = resources.displayMetrics.widthPixels
    private val displayHeight: Int
        get() = resources.displayMetrics.heightPixels
    private val Iterable<CheckBoxItem>.enabledDurations: CharSequence
        get() =
            filter { checkBoxItem -> checkBoxItem.isChecked }.joinToString(
                    separator = ", ",
                    limit = 10,
                    truncated = "..."
            ) { checkBoxItem -> checkBoxItem.duration.toLocalizedFormattedTime(binding.root.context) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppCompatDelegate.setDefaultNightMode(sharedPreferences.theme.value)
        if (DynamicColors.isDynamicColorAvailable() && sharedPreferences.isMaterialYouEnabled) DynamicColors.applyToActivityIfAvailable(this@MainActivity)

        enableEdgeToEdge()
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        with(binding) {
            val themeClickListener = View.OnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                showChooseThemeDialog()
            }

            appIcon.setColoredImageDrawable(R.drawable.outline_coffee_24, root.context.theme.themeColor)

            appThemeCard.setOnClickListener(themeClickListener)
            appThemeButton.setOnClickListener(themeClickListener)

            when (sharedPreferences.theme) {
                SharedPrefsManager.Theme.SYSTEM_DEFAULT -> appThemeButton.text = getString(R.string.app_theme_system_default)
                SharedPrefsManager.Theme.LIGHT          -> appThemeButton.text = getString(R.string.app_theme_system_light)
                SharedPrefsManager.Theme.DARK           -> appThemeButton.text = getString(R.string.app_theme_system_dark)
            }

            if (DynamicColors.isDynamicColorAvailable()) enableMaterialYouPreferences()

            caffeineButton.setOnClickListener {
                if (!sharedPreferences.isAllPermissionsGranted) return@setOnClickListener

                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                KeepAwakeService.startNextTimeout(caffeinateApplication)
            }

            caffeineButton.setOnLongClickListener {
                if (!sharedPreferences.isAllPermissionsGranted) return@setOnLongClickListener false

                KeepAwakeService.startIndefinitely(caffeinateApplication)

                return@setOnLongClickListener true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isAllPermissionsGranted()) onIsAllPermissionsGrantedChanged(true)

        caffeinateApplication.run {
            applyLocaleConfiguration()

            keepAwakeServiceObservers.addObserver(::keepAwakeServiceObservers.name, this@MainActivity)
            sharedPrefsObservers.addObserver(::sharedPrefsObservers.name, this@MainActivity)

            onServiceStatusUpdate(lastStatusUpdate)
        }
    }

    override fun onPause() {
        super.onPause()
        caffeinateApplication.keepAwakeServiceObservers.remove(this)
    }

    override fun onIsAllPermissionsGrantedChanged(isAllPermissionsGranted: Boolean) {
        binding.caffeineButton.isEnabled = isAllPermissionsGranted

        changeAllowDimmingPreferences(isAllPermissionsGranted)
        changeAllowWhileLockedPreferences(isAllPermissionsGranted)
        changeTimeoutsPreferences(isAllPermissionsGranted)
    }

    override fun onIsDimmingEnabledChanged(isDimmingEnabled: Boolean) {
        binding.allowDimmingSwitch.isChecked = isDimmingEnabled
    }

    override fun onIsWhileLockedEnabledChanged(isWhileLockedEnabled: Boolean) {
        binding.allowWhileLockedSwitch.isChecked = isWhileLockedEnabled
    }

    override fun onServiceStatusUpdate(status: ServiceStatus) {
        with(binding) {
            when (status) {
                is ServiceStatus.Stopped -> {
                    caffeineButton.text = getString(R.string.caffeinate_button_off)
                    appIcon.setColoredImageDrawable(R.drawable.outline_coffee_24, root.context.theme.themeColor)
                }

                is ServiceStatus.Running -> {
                    caffeineButton.text = status.remaining.toLocalizedFormattedTime(root.context)
                    appIcon.setColoredImageDrawable(R.drawable.baseline_coffee_24, root.context.theme.themeColor)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == 93 && grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            val snackbar = Snackbar.make(binding.root, getString(R.string.notifications_permission_required), Snackbar.LENGTH_INDEFINITE)

            snackbar.setAction(getString(R.string.go_to_settings)) {
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
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun enableMaterialYouPreferences() {
        with(binding) {
            materialYouCard.isEnabled = true
            materialYouTextView.isEnabled = true
            materialYouSubTextTextView.visibility = View.GONE
            materialYouSwitch.isEnabled = true
            materialYouSwitch.isChecked = sharedPreferences.isMaterialYouEnabled

            materialYouCard.setOnClickListener { materialYouSwitch.isChecked = !sharedPreferences.isMaterialYouEnabled }
            materialYouSwitch.setOnCheckedChangeListener { switch, isChecked ->
                switch.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                sharedPreferences.isMaterialYouEnabled = isChecked
                recreate()
            }
        }
    }

    private fun changeAllowWhileLockedPreferences(isAllPermissionsGranted: Boolean) {
        with(binding) {
            allowWhileLockedCard.isEnabled = isAllPermissionsGranted
            allowWhileLockedTextView.isEnabled = isAllPermissionsGranted
            allowWhileLockedSubTextTextView.visibility = if (isAllPermissionsGranted) View.VISIBLE else View.GONE
            allowWhileLockedSwitch.isEnabled = isAllPermissionsGranted
            allowWhileLockedSwitch.isChecked = sharedPreferences.isWhileLockedEnabled

            allowWhileLockedCard.setOnClickListener { allowWhileLockedSwitch.isChecked = !sharedPreferences.isWhileLockedEnabled }
            allowWhileLockedSwitch.setOnCheckedChangeListener { switch, isChecked ->
                switch.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                sharedPreferences.isWhileLockedEnabled = isChecked
            }
        }
    }

    private fun changeAllowDimmingPreferences(isAllPermissionsGranted: Boolean) {
        with(binding) {
            allowDimmingCard.isEnabled = isAllPermissionsGranted
            allowDimmingTextView.isEnabled = isAllPermissionsGranted
            allowDimmingSubTextTextView.visibility = if (isAllPermissionsGranted) View.VISIBLE else View.GONE
            allowDimmingSwitch.isEnabled = isAllPermissionsGranted
            allowDimmingSwitch.isChecked = sharedPreferences.isDimmingEnabled

            allowDimmingCard.setOnClickListener { allowDimmingSwitch.isChecked = !sharedPreferences.isDimmingEnabled }
            allowDimmingSwitch.setOnCheckedChangeListener { switch, isChecked ->
                switch.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                sharedPreferences.isDimmingEnabled = isChecked
            }
        }
    }

    private fun changeTimeoutsPreferences(isAllPermissionsGranted: Boolean) {
        with(binding) {
            val timeoutChoiceClickListener = View.OnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                showChooseTimeoutDialog()
            }

            timeoutChoiceCard.isEnabled = isAllPermissionsGranted
            timeoutChoiceTextView.isEnabled = isAllPermissionsGranted
            timeoutChoiceSubTextTextView.isEnabled = true
            timeoutChoiceSubTextTextView.visibility = if (isAllPermissionsGranted) View.VISIBLE else View.GONE
            timeoutChoiceSubTextTextView.text = sharedPreferences.timeoutCheckBoxes.enabledDurations
            timeoutChoiceButton.isEnabled = isAllPermissionsGranted

            timeoutChoiceCard.setOnClickListener(timeoutChoiceClickListener)
            timeoutChoiceButton.setOnClickListener(timeoutChoiceClickListener)
        }
    }

    @SuppressLint("BatteryLife")
    private fun isAllPermissionsGranted(): Boolean {
        with(binding) {
            var isAllPermissionsGranted = sharedPreferences.isAllPermissionsGranted

            batteryOptimizationTextView.text = getString(R.string.battery_optimization_granted)
            batteryOptimizationImageView.setImageDrawable(grantedDrawable)
            batteryOptimizationImageView.setColorFilter(Color.argb(255, 0, 255, 0))

            backgroundOptimizationTextView.text = getString(R.string.background_optimization_granted)
            backgroundOptimizationImageView.setImageDrawable(grantedDrawable)
            backgroundOptimizationImageView.setColorFilter(Color.argb(255, 0, 255, 0))

            notificationPermissionTextView.text = getString(R.string.notifications_permission_granted)
            notificationPermissionImageView.setImageDrawable(grantedDrawable)
            notificationPermissionImageView.setColorFilter(Color.argb(255, 0, 255, 0))

            if (isAllPermissionsGranted) return true
            val requiredPermissions = listOf(checkBatteryOptimization(), checkBackgroundOptimization(), checkNotificationPermission())

            isAllPermissionsGranted = requiredPermissions.all { it }

            sharedPreferences.isAllPermissionsGranted = isAllPermissionsGranted
            return isAllPermissionsGranted
        }
    }

    @SuppressLint("BatteryLife")
    private fun checkBatteryOptimization(): Boolean {
        with(binding) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            return if (!powerManager.isIgnoringBatteryOptimizations(caffeinateApplication.localizedApplicationContext.packageName)) {
                batteryOptimizationCard.setOnClickListener {
                    startActivity(Intent().apply {
                        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        data = Uri.parse("package:${caffeinateApplication.localizedApplicationContext.packageName}")
                    })
                    requestBatteryOptimizationPermission()
                }
                batteryOptimizationTextView.text = getString(R.string.battery_optimization_not_granted)
                batteryOptimizationImageView.setImageDrawable(notGrantedDrawable)
                batteryOptimizationImageView.setColorFilter(Color.argb(255, 255, 0, 0))

                false
            } else {
                true
            }
        }
    }

    private fun checkBackgroundOptimization(): Boolean {
        with(binding) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                if (activityManager.isBackgroundRestricted) {
                    backgroundOptimizationCard.setOnClickListener {
                        requestBatteryOptimizationPermission()
                    }
                    backgroundOptimizationTextView.text = getString(R.string.background_optimization_not_granted)
                    backgroundOptimizationImageView.setImageDrawable(notGrantedDrawable)
                    backgroundOptimizationImageView.setColorFilter(Color.argb(255, 255, 0, 0))
                    return false
                } else {
                    return true
                }
            } else {
                return true
            }
        }
    }

    private fun checkNotificationPermission(): Boolean {
        with(binding) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermissionCard.setOnClickListener { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 93) }
                    notificationPermissionTextView.text = getString(R.string.notifications_permission_not_granted)
                    notificationPermissionImageView.setImageDrawable(notGrantedDrawable)
                    notificationPermissionImageView.setColorFilter(Color.argb(255, 255, 0, 0))

                    return false
                } else {
                    return true
                }
            } else {
                return true
            }
        }
    }

    private fun requestBatteryOptimizationPermission() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_battery_optimization_needed_title))
            .setIcon(R.drawable.coffee_icon)
            .setCancelable(false)
            .setMessage(getString(R.string.dialog_battery_optimization_needed_message))
            .setPositiveButton(getString(R.string.dialog_button_ok)) { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${caffeinateApplication.localizedApplicationContext.packageName}")))
            }
            .setNegativeButton(getString(R.string.dialog_button_cancel), null)
            .show()
    }

    private fun showChooseThemeDialog() {
        with(binding) {
            var theme = sharedPreferences.theme
            val dialogBinding = DialogChooseThemeBinding.inflate(LayoutInflater.from(root.context))
            val dialog = MaterialAlertDialogBuilder(root.context).setView(dialogBinding.root).create().apply {
                window?.setLayout((displayWidth * 0.6f).toInt(), (displayHeight * 0.45f).toInt())
            }

            when (theme.value) {
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> {
                    dialogBinding.themeRadioGroup.check(R.id.themeSystemDefault)
                    appThemeButton.text = getString(R.string.app_theme_system_default)
                }

                AppCompatDelegate.MODE_NIGHT_NO            -> {
                    dialogBinding.themeRadioGroup.check(R.id.themeSystemLight)
                    appThemeButton.text = getString(R.string.app_theme_system_light)
                }

                AppCompatDelegate.MODE_NIGHT_YES           -> {
                    dialogBinding.themeRadioGroup.check(R.id.themeSystemDark)
                    appThemeButton.text = getString(R.string.app_theme_system_dark)
                }
            }

            with(dialogBinding) {
                themeRadioGroup.setOnCheckedChangeListener { radioGroup, checkedRadioButtonId ->
                    radioGroup.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

                    when (checkedRadioButtonId) {
                        R.id.themeSystemDefault -> theme = SharedPrefsManager.Theme.SYSTEM_DEFAULT
                        R.id.themeSystemLight   -> theme = SharedPrefsManager.Theme.LIGHT
                        R.id.themeSystemDark    -> theme = SharedPrefsManager.Theme.DARK
                    }
                }

                dialogButtonOk.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

                    dialog.dismiss()

                    sharedPreferences.theme = theme
                    AppCompatDelegate.setDefaultNightMode(theme.value)

                    recreate()
                }

                dialogButtonCancel.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    dialog.dismiss()
                }
            }

            dialog.show()
        }
    }

    private fun showChooseTimeoutDialog() {
        with(binding) {
            val dialogBinding = DialogChooseTimeoutsBinding.inflate(LayoutInflater.from(root.context))
            val dialog = MaterialAlertDialogBuilder(root.context).setView(dialogBinding.root).create()

            with(dialogBinding) {
                val checkBoxAdapter = CheckBoxAdapter(caffeinateApplication.timeoutCheckBoxes.map { it.copy() })
                timeoutsRecyclerView.layoutManager = LinearLayoutManager(root.context)
                timeoutsRecyclerView.adapter = checkBoxAdapter
                val height = checkBoxAdapter.timeoutCheckBoxes.map { 0f }.fold(0f) { sum, _ -> sum + 0.12f }

                dialog.window?.setLayout((displayWidth * 0.8f).toInt(), (displayHeight * height).toInt())

                dialogButtonOk.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

                    caffeinateApplication.run {
                        timeoutCheckBoxes.clear()
                        checkBoxAdapter.timeoutCheckBoxes.forEach { checkBoxItem -> timeoutCheckBoxes.add(checkBoxItem.copy()) }

                        timeoutChoiceSubTextTextView.text = timeoutCheckBoxes.enabledDurations

                        checkBoxAdapter.timeoutCheckBoxes
                            .find { checkBoxItem -> checkBoxItem.duration == timeout && !checkBoxItem.isChecked }
                            ?.let {
                                when (lastStatusUpdate) {
                                    is ServiceStatus.Running -> KeepAwakeService.startNextTimeout(this, debounce = false)
                                    else                     -> timeout = checkBoxAdapter.timeoutCheckBoxes.first { checkBoxItem -> checkBoxItem.isChecked }.duration
                                }
                            }
                        ?: when (lastStatusUpdate) {
                            is ServiceStatus.Stopped -> timeout = checkBoxAdapter.timeoutCheckBoxes.first { checkBoxItem -> checkBoxItem.isChecked }.duration
                            else                     -> Unit // do nothing if the service is running
                        }

                        sharedPreferences.timeoutCheckBoxes = timeoutCheckBoxes

                        dialog.dismiss()
                    }
                }

                dialogButtonCancel.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    dialog.dismiss()
                }
            }

            dialog.show()
        }
    }
}