package com.example.operaproxy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.*
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.*
import kotlin.collections.ArrayList

/**
 * Сердце PortalConnect - управление VPN и прокси (100% восстановление логики STR97).
 */
class ProxyVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var process: java.lang.Process? = null
    private var tunFd: Int = -1
    private var tun2proxyThread: Thread? = null
    
    private var currentCountry = "EU"
    private var bindAddress = "127.0.0.1:1085"
    private var dnsServer = "8.8.8.8"
    private var fakeSni = ""
    private var upstreamProxy = ""
    private var bootstrapDns = ""
    private var socksMode = false
    private var proxyOnlyMode = false
    private var verbosity = 20
    private var tunDnsStrategy = 1
    private var testUrl = ""
    private var apiAddress = ""
    private var manualCmdMode = false
    private var customCmdString = ""
    private var proxyAppMode = 0
    private var killSwitch = false
    private var allowedApps: ArrayList<String>? = null

    private var stopRequested = false
    private var cleanedUp = false

    companion object {
        init {
            try {
                System.loadLibrary("tun2proxy")
                System.loadLibrary("native-lib")
            } catch (e: Exception) {
                Log.e("ProxyVpnService", "Failed to load native libraries", e)
            }
        }

        @JvmStatic
        private external fun startTun2proxy(service: ProxyVpnService, proxyUrl: String, tunFd: Int, closeFdOnDrop: Boolean, tunMtu: Char, dnsStrategy: Int, verbosity: Int): Int

        @JvmStatic
        private external fun stopTun2proxy(): Int
    }

    // JNI Callbacks
    fun onTun2ProxyLog(message: String) {
        if (verbosity <= 10) {
            logToUI("[tun2proxy] $message")
        }
    }

    fun onTraffic(rx: Long, tx: Long) {
        WidgetData.saveTraffic(this, rx, tx)
        val intent = Intent("TRAFFIC_UPDATE").apply {
            setPackage(packageName)
            putExtra("rx", rx)
            putExtra("tx", tx)
        }
        sendBroadcast(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()

        if (intent?.action == "STOP_VPN") {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }

        if (ServiceState.isRunning(this)) {
            return START_STICKY
        }

        parseIntent(intent)
        updateForegroundNotification()
        
        ServiceState.setRunning(this, true)
        ServiceState.setConnectionStartTime(this, System.currentTimeMillis())
        stopRequested = false
        cleanedUp = false
        notifyStatusChange()

        val serverInfo = if (apiAddress.isNotEmpty()) apiAddress else "$currentCountry ($bindAddress)"
        broadcastServer(serverInfo, currentCountry)

        Thread { runBinary() }.start()
        
        if (!proxyOnlyMode) {
            Thread {
                // ПОВЫШЕННОЕ ОЖИДАНИЕ (до 2 минут, как в оригинале при ретраях)
                if (waitForProxy(1085)) {
                    if (!stopRequested) establishVpn()
                } else {
                    logToUI("[TIMEOUT] Прокси-ядро не запустилось за 2 минуты.")
                    if (!stopRequested) stopVpn()
                }
            }.start()
        }

        return START_STICKY
    }

    private fun parseIntent(intent: Intent?) {
        intent?.let {
            currentCountry = it.getStringExtra("COUNTRY") ?: "EU"
            bindAddress = it.getStringExtra("BIND_ADDRESS") ?: "127.0.0.1:1085"
            dnsServer = it.getStringExtra("DNS") ?: "8.8.8.8"
            fakeSni = it.getStringExtra("FAKE_SNI") ?: ""
            upstreamProxy = it.getStringExtra("UPSTREAM_PROXY") ?: ""
            bootstrapDns = it.getStringExtra("BOOTSTRAP_DNS") ?: ""
            socksMode = it.getBooleanExtra("SOCKS_MODE", false)
            proxyOnlyMode = it.getBooleanExtra("PROXY_ONLY", false)
            verbosity = it.getIntExtra("VERBOSITY", 20)
            tunDnsStrategy = it.getIntExtra("TUN2PROXY_DNS_STRATEGY", 1)
            testUrl = it.getStringExtra("TEST_URL") ?: ""
            apiAddress = it.getStringExtra("API_ADDRESS") ?: ""
            manualCmdMode = it.getBooleanExtra("MANUAL_CMD_MODE", false)
            customCmdString = it.getStringExtra("CUSTOM_CMD_STRING") ?: ""
            proxyAppMode = it.getIntExtra("PROXY_APP_MODE", 0)
            killSwitch = it.getBooleanExtra("KILL_SWITCH", false)
            allowedApps = it.getStringArrayListExtra("ALLOWED_APPS")
        }
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("OperaProxy_vpn_channel", "PortalConnect Service", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, ProxyVpnService::class.java).apply { action = "STOP_VPN" }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val mainIntent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT }
        val mainPendingIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "OperaProxy_vpn_channel")
            .setContentTitle("PortalConnect: Запуск...")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "ОСТАНОВИТЬ", stopPendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun updateForegroundNotification() {
        val stopIntent = Intent(this, ProxyVpnService::class.java).apply { action = "STOP_VPN" }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val mainIntent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT }
        val mainPendingIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "OperaProxy_vpn_channel")
            .setContentTitle("PortalConnect: Активен ($currentCountry)")
            .setContentText("Прокси: $bindAddress | DNS: $dnsServer")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "ОСТАНОВИТЬ", stopPendingIntent)
            .setOngoing(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1, notification)
    }

    private fun waitForProxy(port: Int): Boolean {
        var attempts = 0
        // Ждем до 120 попыток (60 секунд)
        while (attempts < 120 && !stopRequested) {
            try {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 500); return true }
            } catch (_: Exception) {
                Thread.sleep(500)
                attempts++
            }
        }
        return false
    }

    private fun establishVpn() {
        try {
            val builder = Builder()
            builder.setSession("PortalConnect")
            builder.setMtu(1420)
            builder.addAddress("10.1.10.1", 24)
            builder.addDnsServer(dnsServer)
            builder.addRoute("0.0.0.0", 0)
            if (killSwitch) builder.setBlocking(true)
            applyRouting(builder)

            vpnInterface = builder.establish()
            if (vpnInterface == null) { stopVpn(); return }

            tunFd = vpnInterface!!.detachFd()
            startTun2proxyWorker()
        } catch (e: Exception) {
            broadcastVpnError(e.message ?: "Ошибка VPN")
            stopVpn()
        }
    }

    private fun applyRouting(builder: Builder) {
        val myPackage = packageName
        val apps = allowedApps?.toSet() ?: emptySet()
        when (proxyAppMode) {
            2 -> { 
                try { builder.addDisallowedApplication(myPackage) } catch (_: Exception) {}
                apps.forEach { try { builder.addDisallowedApplication(it) } catch (_: Exception) {} }
            }
            1 -> {
                val allApps = getLaunchablePackages()
                val toDisallow = allApps.filter { it != myPackage && !apps.contains(it) }
                if (toDisallow.size < 900) {
                    toDisallow.forEach { try { builder.addDisallowedApplication(it) } catch (_: Exception) {} }
                }
            }
            else -> {
                if (apps.isEmpty()) {
                    try { builder.addDisallowedApplication(myPackage) } catch (_: Exception) {}
                } else {
                    apps.forEach { try { builder.addAllowedApplication(it) } catch (_: Exception) {} }
                }
            }
        }
    }

    private fun getLaunchablePackages(): List<String> {
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }
    }

    private fun startTun2proxyWorker() {
        if (tunFd <= 0) return
        val proxyUrl = "${if (socksMode) "socks5" else "http"}://127.0.0.1:${bindAddress.substringAfterLast(":")}"
        tun2proxyThread = Thread {
            try {
                startTun2proxy(this, proxyUrl, tunFd, true, 1420.toChar(), tunDnsStrategy, 3)
            } catch (e: Exception) {
                logToUI("[tun2proxy ERROR] ${e.message}")
            }
        }
        tun2proxyThread?.start()
    }

    private fun runBinary() {
        try {
            val libDir = applicationInfo.nativeLibraryDir
            val binFile = File(libDir, "liboperaproxy.so")
            
            val args = mutableListOf(binFile.absolutePath)
            if (manualCmdMode && customCmdString.isNotBlank()) {
                args.addAll(customCmdString.trim().split(Regex("\\s+")))
            } else {
                // 100% ПАРИТЕТ С ОРИГИНАЛОМ
                args.add("-bind-address"); args.add(bindAddress)
                args.add("-country"); args.add(currentCountry)
                args.add("-verbosity"); args.add(if (verbosity == 5) "50" else verbosity.toString())
                
                if (bootstrapDns.isNotEmpty()) {
                    args.add("-bootstrap-dns"); args.add(bootstrapDns)
                }
                
                if (fakeSni.isNotEmpty()) {
                    args.add("-fake-SNI")
                    args.add(fakeSni.lowercase().trim())
                }
                
                if (upstreamProxy.isNotEmpty()) {
                    args.add("-proxy"); args.add(upstreamProxy)
                }
                
                if (socksMode) {
                    args.add("-socks-mode")
                }
                
                if (apiAddress.isNotEmpty()) {
                    args.add("-api-address"); args.add(apiAddress)
                }
                
                args.add("-server-selection-test-url")
                args.add(if (testUrl.isNotEmpty()) testUrl else "https://ajax.googleapis.com/ajax/libs/indefinite-observable/2.0.1/indefinite-observable.bundle.js")
            }

            logToUI("[CMD] Запуск бинарного файла...")
            if (verbosity <= 10) {
                logToUI("CMD: ${args.joinToString(" ")}")
            }

            val pb = ProcessBuilder(args)
            pb.redirectErrorStream(true)
            pb.environment()["LD_LIBRARY_PATH"] = libDir
            process = pb.start()

            process?.inputStream?.bufferedReader()?.use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val logLine = line ?: break
                    logToUI(logLine)
                    if (logLine.contains("Connected to")) {
                        val server = logLine.substringAfter("Connected to").trim()
                        broadcastServer(server, currentCountry)
                    }
                }
            }
        } catch (e: Exception) {
            if (!stopRequested) {
                logToUI("[CRITICAL BIN ERROR] ${e.message}")
                maybeScheduleReconnect()
            }
        }
    }

    private fun maybeScheduleReconnect() {
        val prefs = getSharedPreferences("OperaProxyPrefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("AUTO_RECONNECT", false) && !stopRequested) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (!ServiceState.isRunning(this) && !stopRequested) {
                    val intent = Intent(this, ProxyVpnService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                }
            }, 5000)
        }
    }

    private fun broadcastServer(server: String, country: String) {
        val intent = Intent("VPN_SERVER_UPDATE").apply {
            setPackage(packageName)
            putExtra("server", server)
            putExtra("country", country)
        }
        sendBroadcast(intent)
    }

    private fun logToUI(msg: String) {
        sendBroadcast(Intent("UPDATE_LOG").putExtra("LOG", msg).setPackage(packageName))
    }

    private fun broadcastVpnError(msg: String) {
        sendBroadcast(Intent("VPN_ERROR").putExtra("ERROR", msg).setPackage(packageName))
    }

    private fun notifyStatusChange() {
        sendBroadcast(Intent("STATUS_UPDATE").setPackage(packageName))
        val widgetIntent = Intent("android.appwidget.action.APPWIDGET_UPDATE")
        widgetIntent.putExtra("appWidgetIds", AppWidgetManager.getInstance(this).getAppWidgetIds(ComponentName(this, PortalWidgetProvider::class.java)))
        sendBroadcast(widgetIntent)
    }

    private fun stopVpn() {
        if (cleanedUp) return
        cleanedUp = true
        stopRequested = true
        try { stopTun2proxy() } catch (_: Exception) {}
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        try { process?.destroy() } catch (_: Exception) {}
        process = null
        ServiceState.setRunning(this, false)
        WidgetData.clearTraffic(this)
        notifyStatusChange()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(1)
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }
}
