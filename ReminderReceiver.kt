package com.attaproductions.secretary

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Напоминание"
        val id = intent.getLongExtra("id", System.currentTimeMillis()).toInt()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = "tasks"
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(channel, "Задачи", NotificationManager.IMPORTANCE_HIGH))
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(context, channel) else android.app.Notification.Builder(context)
        val n = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("ATTA Secretary")
            .setContentText(title)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        nm.notify(id, n)
    }
}
