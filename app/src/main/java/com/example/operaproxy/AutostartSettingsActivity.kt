package com.example.operaproxy

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

/**
 * Настройки автозапуска и автоматизации Wi-Fi.
 */
class AutostartSettingsActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var swAutoStartBoot: SwitchMaterial
    private lateinit var swAutoReconnect: SwitchMaterial
    private lateinit var swAutoWifi: SwitchMaterial
    private lateinit var etAutoWifiSsid: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        prefs = getSharedPreferences("OperaProxyPrefs", Context.MODE_PRIVATE)

        if (PinHelper.isPinEnabled(this) && !intent.getBooleanExtra("SKIP_PIN_VERIFY", false)) {
            setContentView(R.layout.activity_autostart_settings)
            PinHelper.showVerifyDialog(this, {
                continueOnCreate()
            }, {
                finish()
            })
        } else {
            continueOnCreate()
        }
    }

    private fun continueOnCreate() {
        setContentView(R.layout.activity_autostart_settings)
        
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        swAutoStartBoot = findViewById(R.id.swAutoStartBoot)
        swAutoReconnect = findViewById(R.id.swAutoReconnect)
        swAutoWifi = findViewById(R.id.swAutoWifi)
        etAutoWifiSsid = findViewById(R.id.etAutoWifiSsid)

        loadSettings()

        swAutoStartBoot.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("AUTO_START_BOOT", isChecked).apply()
        }

        swAutoReconnect.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("AUTO_RECONNECT", isChecked).apply()
        }

        swAutoWifi.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("AUTO_WIFI", isChecked).apply()
        }
    }

    private fun loadSettings() {
        swAutoStartBoot.isChecked = prefs.getBoolean("AUTO_START_BOOT", false)
        swAutoReconnect.isChecked = prefs.getBoolean("AUTO_RECONNECT", false)
        swAutoWifi.isChecked = prefs.getBoolean("AUTO_WIFI", false)
        etAutoWifiSsid.setText(prefs.getString("AUTO_WIFI_SSID", ""))
    }

    override fun onPause() {
        super.onPause()
        val ssid = etAutoWifiSsid.text?.toString()?.trim() ?: ""
        prefs.edit().putString("AUTO_WIFI_SSID", ssid).apply()
    }
}
