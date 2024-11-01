package com.hifnawy.caffeinate.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat

/**
 * Utility class for creating notifications and their actions.
 *
 * @author AbdAlMoniem AlHifnawy
 */
object NotificationUtils {

    /**
     * Builds a [NotificationCompat.Action] for the given [Context], [ClassType],
     * [action], [icon], [title], and [requestCode].
     *
     * @param context [Context] the context in which the action is built
     * @param destClass [ClassType] the class of the activity or service that the action will
     * intend to start
     * @param action [String] the action to be performed by the intent
     * @param icon [DrawableRes] the icon to be displayed for the action
     * @param title [String] the title of the action
     * @param requestCode [Int] the request code with which the PendingIntent is created
     *
     * @return [NotificationCompat.Action] the built [NotificationCompat.Action]
     */
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

    /**
     * Creates a [PendingIntent] for the given [Context], [ClassType], [action], and [requestCode].
     *
     * @param context [Context] the context in which the pending intent is created
     * @param destClass [ClassType] the class of the activity or service that the intent will start
     * @param action [String] the action to be performed by the intent
     * @param requestCode [Int] the request code for the pending intent
     * @return [PendingIntent] the created [PendingIntent]
     */
    fun <ClassType : Any> getPendingIntent(context: Context, destClass: Class<ClassType>, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, destClass).apply { this.action = action }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            PendingIntent.getService(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}