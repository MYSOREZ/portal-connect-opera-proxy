package com.example.operaproxy

import android.content.Context
import java.io.File

/**
 * Хранит текущие данные трафика в файле для отображения в виджете.
 */
object WidgetData {
    private const val TRAFFIC_FILE = "widget_traffic"

    private fun trafficFile(ctx: Context): File = 
        File(ctx.applicationContext.noBackupFilesDir, TRAFFIC_FILE)

    fun saveTraffic(ctx: Context, rx: Long, tx: Long) {
        try {
            trafficFile(ctx).writeText("$rx,$tx")
        } catch (_: Exception) {}
    }

    fun getTraffic(ctx: Context): Pair<Long, Long> {
        return try {
            val content = trafficFile(ctx).readText().trim()
            val parts = content.split(",")
            if (parts.size >= 2) {
                Pair(parts[0].toLongOrNull() ?: 0L, parts[1].toLongOrNull() ?: 0L)
            } else {
                Pair(0L, 0L)
            }
        } catch (_: Exception) {
            Pair(0L, 0L)
        }
    }

    fun clearTraffic(ctx: Context) {
        try {
            trafficFile(ctx).delete()
        } catch (_: Exception) {}
    }
}
