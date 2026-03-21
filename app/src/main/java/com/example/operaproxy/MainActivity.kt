package com.example.operaproxy

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.*
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import com.google.android.material.button.MaterialButton
import java.util.*

/**
 * Главный экран PortalConnect (100% логическая идентичность STR97).
 */
class MainActivity : AppCompatActivity() {
    private lateinit var tvLog: TextView
    private lateinit var svLog: ScrollView
    private lateinit var tvServer: TextView
    private lateinit var tvTrafficIn: TextView
    private lateinit var tvTrafficOut: TextView
    private lateinit var tvConnectionTime: TextView
    private lateinit var btnToggle: MaterialButton
    private lateinit var rgCountry: RadioGroup
    private lateinit var tvPingEU: TextView
    private lateinit var tvPingAS: TextView
    private lateinit var tvPingAM: TextView
    private lateinit var prefs: SharedPreferences

    private var updateTimer: Timer? = null

    companion object {
        var INSTANCE: MainActivity? = null
        var latestLogText: String = ""
    }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getBooleanExtra("CLEAR", false) == true) {
                tvLog.text = ""
                latestLogText = ""
                return
            }
            val log = intent?.getStringExtra("LOG") ?: return
            tvLog.append("$log\n")
            latestLogText = tvLog.text.toString()
            svLog.post { svLog.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateUi()
        }
    }

    private val trafficReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val rx = intent?.getLongExtra("rx", 0L) ?: 0L
            val tx = intent?.getLongExtra("tx", 0L) ?: 0L
            tvTrafficIn.text = WidgetUpdateHelper.formatBytes(rx)
            tvTrafficOut.text = WidgetUpdateHelper.formatBytes(tx)
        }
    }

    private val serverReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val server = intent?.getStringExtra("server") ?: return
            prefs.edit().putString("LAST_CONNECTED_SERVER", server).apply()
            tvServer.text = server
        }
    }

    fun getLatestLogText(): String = latestLogText

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        INSTANCE = this
        
        val startupPrefs = getSharedPreferences("OperaProxyPrefs", Context.MODE_PRIVATE)
        if (!startupPrefs.getBoolean("ONBOARDING_FINISHED", false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        
        if (PinHelper.isPinEnabled(this)) {
            PinHelper.showVerifyDialog(this, { continueOnCreate() }, { finish() })
        } else {
            continueOnCreate()
        }
    }

    private fun continueOnCreate() {
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("OperaProxyPrefs", Context.MODE_PRIVATE)

        initViews()
        setupListeners()
        
        registerReceivers()
        updateUi()
        startUpdateTimer()
        
        runLatencyTest()
    }

    private fun initViews() {
        tvLog = findViewById(R.id.tvLog)
        svLog = findViewById(R.id.svLog)
        tvServer = findViewById(R.id.tvServer)
        tvTrafficIn = findViewById(R.id.tvTrafficIn)
        tvTrafficOut = findViewById(R.id.tvTrafficOut)
        tvConnectionTime = findViewById(R.id.tvConnectionTime)
        btnToggle = findViewById(R.id.btnToggle)
        rgCountry = findViewById(R.id.rgCountry)
        tvPingEU = findViewById(R.id.tvPingEU)
        tvPingAS = findViewById(R.id.tvPingAS)
        tvPingAM = findViewById(R.id.tvPingAM)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        rgCountry.check(prefs.getInt("COUNTRY_ID", R.id.rbEU))
        tvLog.text = latestLogText
    }

    private fun setupListeners() {
        btnToggle.setOnClickListener {
            if (ServiceState.isRunning(this)) stopProxyService() else startVpnPreparation()
        }

        findViewById<MaterialButton>(R.id.btnTestLatency).setOnClickListener {
            runLatencyTest()
        }

        rgCountry.setOnCheckedChangeListener { _, checkedId ->
            prefs.edit().putInt("COUNTRY_ID", checkedId).apply()
        }
    }

    private fun updateUi() {
        val isRunning = ServiceState.isRunning(this)
        btnToggle.text = if (isRunning) getString(R.string.btn_stop) else getString(R.string.btn_start)
        btnToggle.setBackgroundResource(if (isRunning) R.drawable.bg_btn_glass_stop_selector else R.drawable.bg_btn_glass_selector)
        
        if (isRunning) {
            tvServer.text = prefs.getString("LAST_CONNECTED_SERVER", getString(R.string.current_server_unknown))
        } else {
            tvServer.text = getString(R.string.current_server_unknown)
            tvTrafficIn.text = "—"
            tvTrafficOut.text = "—"
            tvConnectionTime.text = "—"
        }
    }

    private fun startVpnPreparation() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 0)
        } else {
            onActivityResult(0, RESULT_OK, null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            startProxyService()
        }
    }

    private fun startProxyService() {
        val intent = Intent(this, ProxyVpnService::class.java)
        
        val countryId = rgCountry.checkedRadioButtonId
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
        intent.putExtra("API_ADDRESS", prefs.getString("API_ADDRESS", ""))
        
        // КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Передаем TEST_URL из пресета
        intent.putExtra("TEST_URL", prefs.getString("TEST_URL", ""))

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

    private fun stopProxyService() {
        val intent = Intent(this, ProxyVpnService::class.java).apply { action = "STOP_VPN" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction("UPDATE_LOG")
            addAction("STATUS_UPDATE")
            addAction("TRAFFIC_UPDATE")
            addAction("VPN_SERVER_UPDATE")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(trafficReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(serverReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(logReceiver, filter)
            registerReceiver(statusReceiver, filter)
            registerReceiver(trafficReceiver, filter)
            registerReceiver(serverReceiver, filter)
        }
    }

    private fun startUpdateTimer() {
        updateTimer = Timer()
        updateTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (ServiceState.isRunning(this@MainActivity)) {
                    val time = WidgetUpdateHelper.formatConnectionTime(this@MainActivity)
                    runOnUiThread { tvConnectionTime.text = time }
                }
            }
        }, 0, 1000)
    }

    private fun runLatencyTest() {
        tvPingEU.text = "..."
        tvPingAS.text = "..."
        tvPingAM.text = "..."
        Thread {
            RegionPingHelper.pingAllRegions { results ->
                runOnUiThread {
                    results["EU"]?.let { tvPingEU.text = if (it.success) "${it.latencyMs} ms" else "err" }
                    results["AS"]?.let { tvPingAS.text = if (it.success) "${it.latencyMs} ms" else "err" }
                    results["AM"]?.let { tvPingAM.text = if (it.success) "${it.latencyMs} ms" else "err" }
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(logReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(statusReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(trafficReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(serverReceiver) } catch (_: Exception) {}
        updateTimer?.cancel()
        INSTANCE = null
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> { startActivity(Intent(this, MainSettingsActivity::class.java)); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
