package com.example.operaproxy

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.Build

/**
 * Мониторинг Wi-Fi для автоматического запуска VPN.
 */
class WiFiMonitor(private val context: Context) {
    private var callback: ConnectivityManager.NetworkCallback? = null
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val prefs: SharedPreferences = context.getSharedPreferences("OperaProxyPrefs", Context.MODE_PRIVATE)

    fun start() {
        if (callback == null && prefs.getBoolean("AUTO_WIFI", false)) {
            val request = NetworkRequest.Builder()
                .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val targetSsid = prefs.getString("AUTO_WIFI_SSID", "")?.trim() ?: ""
                    val currentSsid = getCurrentSsid()
                    
                    if (targetSsid.isNotEmpty() && currentSsid != null && currentSsid != targetSsid) {
                        return
                    }

                    if (!ServiceState.isRunning(context)) {
                        startVpnService()
                    }
                }
            }
            callback = networkCallback
            connectivityManager.registerNetworkCallback(request, networkCallback)
        }
    }

    fun stop() {
        callback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
            callback = null
        }
    }

    private fun getCurrentSsid(): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            info.ssid?.trim('\"')
        } catch (_: Exception) {
            null
        }
    }

    private fun startVpnService() {
        if (VpnService.prepare(context) != null) return

        val intent = Intent(context, ProxyVpnService::class.java)
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
