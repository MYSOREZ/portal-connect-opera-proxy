package com.example.operaproxy

import android.content.Context
import java.io.File

/**
 * Состояние для multi-process (:vpn).
 * Хранит статус работы и время начала соединения в файлах.
 */
object ServiceState {
    private const val FILE_NAME = "proxy_service_state"
    private const val START_TIME_FILE = "proxy_connection_start"

    private fun file(ctx: Context): File = File(ctx.noBackupFilesDir, FILE_NAME)
    private fun startTimeFile(ctx: Context): File = File(ctx.noBackupFilesDir, START_TIME_FILE)

    fun setRunning(ctx: Context, running: Boolean) {
        try {
            file(ctx).writeText(if (running) "1" else "0")
            if (!running) {
                startTimeFile(ctx).delete()
            }
        } catch (_: Exception) {
        }
    }

    fun isRunning(ctx: Context): Boolean {
        return try {
            file(ctx).readText().trim() == "1"
        } catch (_: Exception) {
            false
        }
    }

    fun setConnectionStartTime(ctx: Context, timeMs: Long) {
        try {
            startTimeFile(ctx).writeText(timeMs.toString())
        } catch (_: Exception) {
        }
    }

    fun getConnectionStartTime(ctx: Context): Long {
        return try {
            startTimeFile(ctx).readText().trim().toLong()
        } catch (_: Exception) {
            0L
        }
    }
}
