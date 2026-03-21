package com.example.operaproxy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Ресивер для отслеживания изменений Wi-Fi.
 */
class WifiConnectivityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            val monitor = WiFiMonitor(context)
            monitor.start()
        }
    }
}
