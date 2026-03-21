package com.example.operaproxy

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import java.util.Locale

/**
 * Утилита для обновления содержимого виджетов.
 */
object WidgetUpdateHelper {

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = 1024L
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes.toDouble() / gb)
            bytes >= mb -> String.format(Locale.US, "%.2f MB", bytes.toDouble() / mb)
            bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / kb)
            else -> "$bytes B"
        }
    }

    fun formatConnectionTime(ctx: Context): String {
        val startTime = ServiceState.getConnectionStartTime(ctx)
        if (startTime <= 0) return "00:00:00"
        val diffSec = (System.currentTimeMillis() - startTime) / 1000
        val h = diffSec / 3600
        val m = (diffSec % 3600) / 60
        val s = diffSec % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    fun regionLabel(country: String): String {
        return when (country) {
            "EU" -> "Европа"
            "AS" -> "Азия"
            "AM" -> "Америка"
            else -> country
        }
    }

    private fun createClickIntent(context: Context): PendingIntent {
        val intent = Intent(context, PortalWidgetProvider::class.java).apply {
            action = "com.portal.connect.WIDGET_CLICK"
        }
        val flags = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0) or PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    fun updateCompact(context: Context, views: RemoteViews): RemoteViews {
        val isRunning = ServiceState.isRunning(context)
        val statusText = if (isRunning) "Подключено" else "Отключено"
        
        views.setTextViewText(R.id.widgetStatus, statusText)
        // Цвета: темно-зеленый если запущен, темно-серый если нет
        views.setInt(R.id.widgetRoot, "setBackgroundColor", if (isRunning) -0xf2d65c else -0xe5e5e6)
        
        views.setOnClickPendingIntent(R.id.widgetRoot, createClickIntent(context))
        return views
    }

    fun updateMedium(context: Context, views: RemoteViews): RemoteViews {
        val isRunning = ServiceState.isRunning(context)
        val prefs = context.getSharedPreferences("OperaProxyPrefs", Context.MODE_PRIVATE)
        val lastCountry = prefs.getString("LAST_CONNECTED_COUNTRY", "EU") ?: "EU"
        val (rx, tx) = if (isRunning) WidgetData.getTraffic(context) else Pair(0L, 0L)

        views.setTextViewText(R.id.widgetStatus, if (isRunning) "Подключено" else "Отключено")
        views.setTextViewText(R.id.widgetRegion, if (isRunning) regionLabel(lastCountry) else "—")
        views.setTextViewText(R.id.widgetTrafficIn, if (isRunning) formatBytes(rx) else "—")
        views.setTextViewText(R.id.widgetTrafficOut, if (isRunning) formatBytes(tx) else "—")
        
        views.setInt(R.id.widgetRoot, "setBackgroundColor", if (isRunning) -0xf2d65c else -0xe5e5e6)
        views.setOnClickPendingIntent(R.id.widgetRoot, createClickIntent(context))
        return views
    }

    fun updateLarge(context: Context, views: RemoteViews): RemoteViews {
        val isRunning = ServiceState.isRunning(context)
        val prefs = context.getSharedPreferences("OperaProxyPrefs", Context.MODE_PRIVATE)
        val lastCountry = prefs.getString("LAST_CONNECTED_COUNTRY", "EU") ?: "EU"
        val lastPing = if (isRunning) prefs.getInt("LAST_PING_MS", -1) else -1
        val (rx, tx) = if (isRunning) WidgetData.getTraffic(context) else Pair(0L, 0L)

        val pingStr = if (isRunning && lastPing >= 0) "$lastPing мс" else "—"

        views.setTextViewText(R.id.widgetStatus, if (isRunning) "Подключено" else "Отключено")
        views.setTextViewText(R.id.widgetRegion, if (isRunning) regionLabel(lastCountry) else "—")
        views.setInt(R.id.widgetStatus, "setTextColor", if (isRunning) -0xff28f6 else -0x5f5f60)
        
        views.setTextViewText(R.id.widgetPing, pingStr)
        views.setTextViewText(R.id.widgetTrafficIn, if (isRunning) formatBytes(rx) else "—")
        views.setTextViewText(R.id.widgetTrafficOut, if (isRunning) formatBytes(tx) else "—")
        views.setTextViewText(R.id.widgetTime, if (isRunning) formatConnectionTime(context) else "—")

        views.setInt(R.id.widgetRoot, "setBackgroundColor", if (isRunning) -0xf2d65c else -0xe5e5e6)
        views.setOnClickPendingIntent(R.id.widgetRoot, createClickIntent(context))
        return views
    }
}
