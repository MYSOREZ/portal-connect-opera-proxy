package com.example.operaproxy

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Плитка быстрых настроек для PortalConnect.
 */
class ProxyTileService : TileService() {

    companion object {
        fun requestUpdate(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                TileService.requestListeningState(context, ComponentName(context, ProxyTileService::class.java))
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    private fun isServiceRunning(): Boolean {
        return ServiceState.isRunning(this)
    }

    override fun onClick() {
        super.onClick()
        if (isServiceRunning()) {
            val intent = Intent(this, ProxyVpnService::class.java).apply { action = "STOP_VPN" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            updateTile(Tile.STATE_INACTIVE)
        } else {
            if (VpnService.prepare(this) != null) {
                val intent2 = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("OPEN_VPN_PREPARE", true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pi = PendingIntent.getActivity(this, 0, intent2, PendingIntent.FLAG_IMMUTABLE)
                    startActivityAndCollapse(pi)
                } else {
                    startActivityAndCollapse(intent2)
                }
            } else {
                startProxyService()
                updateTile(Tile.STATE_ACTIVE)
            }
        }
    }

    private fun updateTileState() {
        updateTile(if (isServiceRunning()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE)
    }

    private fun updateTile(state: Int) {
        val tile = qsTile ?: return
        tile.state = state
        tile.label = getString(R.string.app_name)
        tile.updateTile()
    }

    private fun startProxyService() {
        val prefs = getSharedPreferences("OperaProxyPrefs", Context.MODE_PRIVATE)
        val intent = Intent(this, ProxyVpnService::class.java)
        
        val countryId = prefs.getInt("COUNTRY_ID", R.id.rbEU)
        val country = when (countryId) {
            R.id.rbAS -> "AS"
            R.id.rbAM -> "AM"
            else -> "EU"
        }

        intent.putExtra("COUNTRY", country)
        intent.putExtra("DNS", prefs.getString("DNS", "8.8.8.8"))
        intent.putExtra("BIND_ADDRESS", prefs.getString("BIND_ADDRESS", "127.0.0.1:1085"))
        intent.putExtra("FAKE_SNI", prefs.getString("FAKE_SNI", ""))
        intent.putExtra("UPSTREAM_PROXY", prefs.getString("UPSTREAM_PROXY", ""))
        intent.putExtra("BOOTSTRAP_DNS", prefs.getString("BOOTSTRAP_DNS", ""))
        intent.putExtra("SOCKS_MODE", prefs.getBoolean("SOCKS_MODE", false))
        intent.putExtra("PROXY_ONLY", prefs.getBoolean("PROXY_ONLY", false))
        intent.putExtra("VERBOSITY", prefs.getInt("VERBOSITY", 20))
        intent.putExtra("TUN2PROXY_DNS_STRATEGY", prefs.getInt("TUN2PROXY_DNS_STRATEGY", 1))
        intent.putExtra("PROXY_APP_MODE", prefs.getInt("PROXY_APP_MODE", 0))
        intent.putExtra("KILL_SWITCH", prefs.getBoolean("KILL_SWITCH", false))

        val apps = prefs.getStringSet("APPS", emptySet())
        if (!apps.isNullOrEmpty()) {
            intent.putStringArrayListExtra("ALLOWED_APPS", ArrayList(apps))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
