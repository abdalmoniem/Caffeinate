package com.hifnawy.caffeinate.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat

object NotificationUtils {

    fun <ClassType : Any> getNotificationAction(
            context: Context,
            destClass: Class<ClassType>,
            action: String,
            @DrawableRes
            icon: Int,
            title: String,
            requestCode: Int
    ): NotificationCompat.Action =
            NotificationCompat.Action(icon, title, getPendingIntent(context, destClass, action, requestCode))

    fun <ClassType : Any> getPendingIntent(context: Context, destClass: Class<ClassType>, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, destClass).apply { this.action = action }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            PendingIntent.getService(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}