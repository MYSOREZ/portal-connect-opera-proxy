package com.example.operaproxy

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.widget.RemoteViews

/**
 * Основной ресивер для виджетов PortalConnect.
 */
open class PortalWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Обновление происходит через Broadcast (STATUS_UPDATE)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val action = intent.action
        if (action == "STATUS_UPDATE" || action == "TRAFFIC_UPDATE") {
            val manager = AppWidgetManager.getInstance(context)
            
            val classes = listOf(
                PortalWidgetCompactProvider::class.java,
                PortalWidgetMediumProvider::class.java,
                PortalWidgetLargeProvider::class.java
            )

            for (cls in classes) {
                val ids = manager.getAppWidgetIds(ComponentName(context, cls))
                if (ids == null || ids.isEmpty()) continue

                for (id in ids) {
                    when (cls) {
                        PortalWidgetCompactProvider::class.java -> {
                            val views = RemoteViews(context.packageName, R.layout.portal_widget_compact)
                            manager.updateAppWidget(id, WidgetUpdateHelper.updateCompact(context, views))
                        }
                        PortalWidgetMediumProvider::class.java -> {
                            val views = RemoteViews(context.packageName, R.layout.portal_widget_medium)
                            manager.updateAppWidget(id, WidgetUpdateHelper.updateMedium(context, views))
                        }
                        PortalWidgetLargeProvider::class.java -> {
                            val views = RemoteViews(context.packageName, R.layout.portal_widget_large)
                            manager.updateAppWidget(id, WidgetUpdateHelper.updateLarge(context, views))
                        }
                    }
                }
            }
        }

        if (action == "com.portal.connect.WIDGET_CLICK") {
            if (ServiceState.isRunning(context)) {
                // Остановить
                val stopIntent = Intent(context, ProxyVpnService::class.java).apply {
                    this.action = "STOP_VPN"
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(stopIntent)
                } else {
                    context.startService(stopIntent)
                }
            } else {
                // Запустить
                if (VpnService.prepare(context) != null) {
                    // Нужно разрешение, открываем MainActivity
                    val mainIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("OPEN_VPN_PREPARE", true)
                    }
                    context.startActivity(mainIntent)
                } else {
                    startProxyFromWidget(context)
                }
            }
        }
    }

    private fun startProxyFromWidget(context: Context) {
        val prefs = context.getSharedPreferences("OperaProxyPrefs", Context.MODE_PRIVATE)
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
