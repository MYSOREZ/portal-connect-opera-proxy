package com.example.operaproxy

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

/**
 * Основные настройки PortalConnect.
 */
class MainSettingsActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var rgAppMode: RadioGroup
    private lateinit var swKillSwitch: SwitchMaterial
    private lateinit var swPinProtection: SwitchMaterial
    private lateinit var etDns: TextInputEditText
    private lateinit var btnSelectApps: MaterialButton
    private lateinit var btnAdvanced: MaterialButton
    private lateinit var btnCheckUpdate: MaterialButton
    private lateinit var tvVersion: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        
        prefs = getSharedPreferences("OperaProxyPrefs", Context.MODE_PRIVATE)

        if (PinHelper.isPinEnabled(this) && !intent.getBooleanExtra("SKIP_PIN_VERIFY", false)) {
            setContentView(R.layout.activity_main_settings)
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
        setContentView(R.layout.activity_main_settings)
        
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        rgAppMode = findViewById(R.id.rgAppMode)
        swKillSwitch = findViewById(R.id.swKillSwitch)
        swPinProtection = findViewById(R.id.swPinProtection)
        etDns = findViewById(R.id.etDns)
        btnSelectApps = findViewById(R.id.btnSelectApps)
        btnAdvanced = findViewById(R.id.btnAdvanced)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)
        tvVersion = findViewById(R.id.tvVersion)

        tvVersion.text = "${getString(R.string.app_name)} v1.0.0"

        loadSettings()

        findViewById<View>(R.id.btnAutostart).setOnClickListener {
            startActivity(Intent(this, AutostartSettingsActivity::class.java))
        }

        findViewById<View>(R.id.btnThemeSelect).setOnClickListener {
            val themes = arrayOf(
                getString(R.string.theme_default),
                getString(R.string.theme_white),
                getString(R.string.theme_amoled),
                getString(R.string.theme_pink),
                getString(R.string.theme_hacker),
                getString(R.string.theme_glass)
            )
            val current = ThemeHelper.getCurrentThemeId(this)
            AlertDialog.Builder(this)
                .setTitle(R.string.theme_select)
                .setSingleChoiceItems(themes, current) { dialog, which ->
                    ThemeHelper.setTheme(this, which)
                    dialog.dismiss()
                    recreate()
                }
                .show()
        }

        swKillSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("KILL_SWITCH", isChecked).apply()
        }

        swPinProtection.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!PinHelper.isPinEnabled(this)) {
                    PinHelper.showSetupDialog(this, {
                        swPinProtection.isChecked = true
                    }, {
                        swPinProtection.isChecked = false
                    })
                }
            } else {
                if (PinHelper.isPinEnabled(this)) {
                    PinHelper.showVerifyDialog(this, {
                        PinHelper.disablePin(this)
                        swPinProtection.isChecked = false
                    }, {
                        swPinProtection.isChecked = true
                    })
                }
            }
        }

        btnSelectApps.setOnClickListener {
            startActivity(Intent(this, AppSelectionActivity::class.java))
        }

        btnAdvanced.setOnClickListener {
            startActivity(Intent(this, AdvancedSettingsActivity::class.java))
        }

        btnCheckUpdate.setOnClickListener {
            checkForUpdate()
        }

        findViewById<View>(R.id.btnLinkKiberportal).setOnClickListener { openUrl("https://t.me/kiberportal") }
        findViewById<View>(R.id.btnLinkMixKiberportal).setOnClickListener { openUrl("https://t.me/mix_kiberportal") }
        findViewById<View>(R.id.btnSupportProject).setOnClickListener { openUrl("https://t.me/str_bypass") }
        
        if (intent.hasExtra("START_UPDATE")) {
            intent.getStringExtra("START_UPDATE")?.let { downloadUpdate(it) }
        }
    }

    private fun loadSettings() {
        val appMode = prefs.getInt("PROXY_APP_MODE", 0)
        when (appMode) {
            1 -> rgAppMode.check(R.id.rbModeWhitelistDisallowed)
            2 -> rgAppMode.check(R.id.rbModeBlacklist)
            else -> rgAppMode.check(R.id.rbModeWhitelist)
        }

        swKillSwitch.isChecked = prefs.getBoolean("KILL_SWITCH", false)
        swPinProtection.isChecked = PinHelper.isPinEnabled(this)
        etDns.setText(prefs.getString("DNS", "8.8.8.8"))

        rgAppMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rbModeWhitelistDisallowed -> 1
                R.id.rbModeBlacklist -> 2
                else -> 0
            }
            prefs.edit().putInt("PROXY_APP_MODE", mode).apply()
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.error_open_link, Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkForUpdate() {
        btnCheckUpdate.isEnabled = false
        btnCheckUpdate.text = getString(R.string.update_checking)
        UpdateChecker.check(this) { info ->
            runOnUiThread {
                btnCheckUpdate.isEnabled = true
                btnCheckUpdate.text = getString(R.string.btn_check_update)
                if (info.hasUpdate) {
                    showUpdateDialog(info)
                } else {
                    Toast.makeText(this, getString(R.string.update_latest, "1.0.0"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showUpdateDialog(info: UpdateChecker.UpdateInfo) {
        AlertDialog.Builder(this)
            .setTitle(R.string.update_available)
            .setMessage(getString(R.string.update_available, info.remoteVersion) + "\nХотите обновить?")
            .setPositiveButton(R.string.update_install) { _, _ ->
                info.downloadUrl?.let { downloadUpdate(it) }
            }
            .setNegativeButton(R.string.update_cancel, null)
            .show()
    }

    private fun downloadUpdate(url: String) {
        val pd = ProgressDialog(this)
        pd.setTitle(getString(R.string.update_download_title))
        pd.setCancelable(false)
        pd.show()

        UpdateChecker.downloadAndInstall(this, url, { progress ->
            runOnUiThread { pd.setProgress(progress) }
        }, { file ->
            runOnUiThread {
                pd.dismiss()
                UpdateChecker.installApk(this, file)
            }
        }, {
            runOnUiThread {
                pd.dismiss()
                Toast.makeText(this, R.string.update_download_error, Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onPause() {
        super.onPause()
        prefs.edit().putString("DNS", etDns.text.toString()).apply()
    }
    
    private class ProgressDialog(context: Context) : AlertDialog(context) {
        private lateinit var pb: ProgressBar
        fun setProgress(p: Int) { pb.progress = p }
        override fun onCreate(savedInstanceState: Bundle?) {
            val v = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(64, 64, 64, 64)
                pb = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    layoutParams = LinearLayout.LayoutParams(-1, -2)
                }
                addView(pb)
            }
            setView(v)
            super.onCreate(savedInstanceState)
        }
    }
}
