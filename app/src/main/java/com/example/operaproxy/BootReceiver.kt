package com.example.operaproxy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Ресивер для запуска сервиса после перезагрузки устройства.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("OperaProxyPrefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("AUTO_START_BOOT", false)) {
                val serviceIntent = Intent(context, ProxyVpnService::class.java)
                
                // Восстанавливаем параметры (упрощенно)
                val countryId = prefs.getInt("COUNTRY_ID", R.id.rbEU)
                val country = when (countryId) {
                    R.id.rbAS -> "AS"
                    R.id.rbAM -> "AM"
                    else -> "EU"
                }
                serviceIntent.putExtra("COUNTRY", country)
                serviceIntent.putExtra("DNS", prefs.getString("DNS", "8.8.8.8"))
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
