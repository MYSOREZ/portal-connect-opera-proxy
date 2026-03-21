package com.example.operaproxy

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Worker для периодической проверки и запуска VPN при подключении к Wi-Fi.
 */
class WiFiConnectWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("OperaProxyPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("AUTO_WIFI", false)) return Result.success()

        if (ServiceState.isRunning(applicationContext)) return Result.success()
        if (VpnService.prepare(applicationContext) != null) return Result.success()

        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return Result.success()
        val caps = cm.getNetworkCapabilities(network) ?: return Result.success()

        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return Result.success()

        val targetSsid = prefs.getString("AUTO_WIFI_SSID", "")?.trim() ?: ""
        if (targetSsid.isNotEmpty()) {
            // В новых версиях Android получение SSID через WifiManager ограничено (требуется Fine Location)
            // Но мы попробуем запустить, так как в Worker это вторичная проверка.
        }

        val intent = Intent(applicationContext, ProxyVpnService::class.java)
        val countryId = prefs.getInt("COUNTRY_ID", R.id.rbEU)
        val country = when (countryId) {
            R.id.rbAS -> "AS"
            R.id.rbAM -> "AM"
            else -> "EU"
        }

        intent.putExtra("COUNTRY", country)
        intent.putExtra("DNS", prefs.getString("DNS", "8.8.8.8"))
        
        val apps = prefs.getStringSet("APPS", emptySet())
        if (!apps.isNullOrEmpty()) {
            intent.putStringArrayListExtra("ALLOWED_APPS", ArrayList(apps))
        }

        intent.putExtra("BIND_ADDRESS", prefs.getString("BIND_ADDRESS", "127.0.0.1:1085"))
        intent.putExtra("FAKE_SNI", prefs.getString("FAKE_SNI", ""))
        intent.putExtra("UPSTREAM_PROXY", prefs.getString("UPSTREAM_PROXY", ""))
        intent.putExtra("BOOTSTRAP_DNS", prefs.getString("BOOTSTRAP_DNS", ""))
        intent.putExtra("SOCKS_MODE", prefs.getBoolean("SOCKS_MODE", false))
        intent.putExtra("PROXY_ONLY", prefs.getBoolean("PROXY_ONLY", false))
        intent.putExtra("VERBOSITY", prefs.getInt("VERBOSITY", 20))
        intent.putExtra("TUN2PROXY_DNS_STRATEGY", prefs.getInt("TUN2PROXY_DNS_STRATEGY", 1))
        intent.putExtra("TEST_URL", prefs.getString("TEST_URL", ""))
        intent.putExtra("API_ADDRESS", prefs.getString("API_ADDRESS", ""))
        intent.putExtra("MANUAL_CMD_MODE", prefs.getBoolean("MANUAL_CMD_MODE", false))
        intent.putExtra("CUSTOM_CMD_STRING", prefs.getString("CUSTOM_CMD_STRING", ""))
        intent.putExtra("PROXY_APP_MODE", prefs.getInt("PROXY_APP_MODE", 0))
        intent.putExtra("KILL_SWITCH", prefs.getBoolean("KILL_SWITCH", false))

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "wifi_connect"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WiFiConnectWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, 
                ExistingPeriodicWorkPolicy.KEEP, 
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
