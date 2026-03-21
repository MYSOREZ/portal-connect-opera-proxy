package com.example.operaproxy

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

/**
 * Расширенные настройки PortalConnect (100% восстановление оригинала STR97).
 */
class AdvancedSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var swProxyOnly: SwitchMaterial
    private lateinit var etBindAddress: TextInputEditText
    private lateinit var etFakeSni: TextInputEditText
    private lateinit var etUpstreamProxy: TextInputEditText
    private lateinit var tilBootstrapDns: TextInputLayout
    private lateinit var etBootstrapDns: TextInputEditText
    private lateinit var tilApiAddress: TextInputLayout
    private lateinit var etApiAddress: TextInputEditText
    private lateinit var swSocksMode: SwitchMaterial
    private lateinit var spVerbosity: Spinner
    private lateinit var spDnsStrategy: Spinner
    private lateinit var ivHelpDnsStrategy: ImageView
    private lateinit var etTestUrl: TextInputEditText
    private lateinit var swManualMode: SwitchMaterial
    private lateinit var etCmdPreview: TextInputEditText

    private val DEFAULT_BIND = "127.0.0.1:1085"
    private val DEFAULT_BOOTSTRAP = "https://1.1.1.3/dns-query,https://8.8.8.8/dns-query,https://dns.google/dns-query,tls://9.9.9.9:853,https://security.cloudflare-dns.com/dns-query,https://fidelity.vm-0.com/q,https://wikimedia-dns.org/dns-query,https://dns.adguard-dns.com/dns-query,https://dns.quad9.net/dns-query,https://dns.comss.one/dns-query,https://router.comss.one/dns-query"
    private val DEFAULT_TEST_URL = "https://ajax.googleapis.com/ajax/libs/indefinite-observable/2.0.1/indefinite-observable.bundle.js"
    private val DEFAULT_VERBOSITY_INDEX = 2
    private val DEFAULT_DNS_STRATEGY_INDEX = 1

    private var mainCountry = "EU"

    private val exportJsonLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.data?.let { writeSettingsToUri(it) }
    }

    private val importJsonLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.data?.let { readSettingsFromUri(it) }
    }

    private val exportLogLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.data?.let { writeLogToUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced_settings)

        prefs = getSharedPreferences("OperaProxyPrefs", Context.MODE_PRIVATE)
        val countryId = prefs.getInt("COUNTRY_ID", R.id.rbEU)
        mainCountry = when (countryId) {
            R.id.rbAS -> "AS"
            R.id.rbAM -> "AM"
            else -> "EU"
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        initViews()
        setupTooltips()
        setupApiAddressLogic()
        setupSwitchImmediatePersistence()
        setupBootstrapDnsClick()
        loadValues()
        setupLivePreview()

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            if (validateInputs()) {
                saveValues()
                Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        findViewById<MaterialButton>(R.id.btnReset).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Сброс")
                .setMessage("Вернуть все расширенные настройки к значениям по умолчанию?")
                .setPositiveButton("Да") { _, _ -> resetDefaults() }
                .setNegativeButton("Отмена", null)
                .show()
        }

        findViewById<MaterialButton>(R.id.btnWhitelistPreset).setOnClickListener {
            applyWhitelistPreset()
        }

        findViewById<MaterialButton>(R.id.btnClearLog).setOnClickListener {
            sendBroadcast(Intent("UPDATE_LOG").putExtra("CLEAR", true).setPackage(packageName))
            Toast.makeText(this, R.string.ClearLogs, Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.btnCopyLog).setOnClickListener {
            val log = MainActivity.INSTANCE?.getLatestLogText() ?: ""
            if (log.isNotEmpty()) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("PORTAL Connect Log", log))
                Toast.makeText(this, R.string.CopyLogs, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.Logs_description, Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<MaterialButton>(R.id.btnSaveLog).setOnClickListener {
            if ((MainActivity.INSTANCE?.getLatestLogText() ?: "").isEmpty()) {
                Toast.makeText(this, R.string.save_log_empty, Toast.LENGTH_SHORT).show()
            } else {
                startLogExport()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.advanced_settings_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export_json -> { saveValues(); startExport(); true }
            R.id.action_import_json -> { startImport(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initViews() {
        swProxyOnly = findViewById(R.id.swProxyOnly)
        etBindAddress = findViewById(R.id.etBindAddress)
        etFakeSni = findViewById(R.id.etFakeSni)
        etUpstreamProxy = findViewById(R.id.etUpstreamProxy)
        tilBootstrapDns = findViewById(R.id.tilBootstrapDns)
        etBootstrapDns = findViewById(R.id.etBootstrapDns)
        swSocksMode = findViewById(R.id.swSocksMode)
        spDnsStrategy = findViewById(R.id.spDnsStrategy)
        ivHelpDnsStrategy = findViewById(R.id.ivHelpDnsStrategy)
        tilApiAddress = findViewById(R.id.tilApiAddress)
        etApiAddress = findViewById(R.id.etApiAddress)
        spVerbosity = findViewById(R.id.spVerbosity)
        etTestUrl = findViewById(R.id.etTestUrl)
        swManualMode = findViewById(R.id.swManualMode)
        etCmdPreview = findViewById(R.id.etCmdPreview)

        val verbosityAdapter = ArrayAdapter.createFromResource(this, R.array.verbosity_levels, android.R.layout.simple_spinner_item)
        verbosityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spVerbosity.adapter = verbosityAdapter

        val dnsStrategyAdapter = ArrayAdapter.createFromResource(this, R.array.dns_strategy_labels, android.R.layout.simple_spinner_item)
        dnsStrategyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spDnsStrategy.adapter = dnsStrategyAdapter
    }

    private fun setupSwitchImmediatePersistence() {
        swProxyOnly.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("PROXY_ONLY", isChecked).commit()
        }
        swSocksMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("SOCKS_MODE", isChecked).commit()
            updateCmdPreview()
        }
        swManualMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("MANUAL_CMD_MODE", isChecked).commit()
            etCmdPreview.isEnabled = isChecked
            if (!isChecked) updateCmdPreview()
        }
    }

    private fun setupBootstrapDnsClick() {
        val onClick = View.OnClickListener {
            showBootstrapDnsDialog()
        }
        tilBootstrapDns.setOnClickListener(onClick)
        etBootstrapDns.setOnClickListener(onClick)
    }

    private fun showBootstrapDnsDialog() {
        val current = prefs.getString("BOOTSTRAP_DNS", DEFAULT_BOOTSTRAP) ?: DEFAULT_BOOTSTRAP
        val input = TextInputEditText(this).apply {
            setText(current)
            minLines = 4
            maxLines = 8
            isSingleLine = false
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        }
        AlertDialog.Builder(this)
            .setTitle("Bootstrap DNS")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val newValue = input.text?.toString() ?: ""
                prefs.edit().putString("BOOTSTRAP_DNS", newValue).commit()
                etBootstrapDns.setText(newValue)
                updateCmdPreview()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun setupTooltips() {
        fun safeSetup(id: Int, titleRes: Int, msgRes: Int) {
            findViewById<TextInputLayout>(id)?.setEndIconOnClickListener { showInfoDialog(getString(titleRes), getString(msgRes)) }
        }
        safeSetup(R.id.tilBindAddress, R.string.pref_bind_address, R.string.help_bind_address)
        safeSetup(R.id.tilFakeSni, R.string.pref_fake_sni, R.string.help_fake_sni)
        safeSetup(R.id.tilUpstreamProxy, R.string.pref_upstream_proxy, R.string.help_upstream_proxy)
        safeSetup(R.id.tilBootstrapDns, R.string.pref_bootstrap_dns, R.string.help_bootstrap_dns)
        safeSetup(R.id.tilTestUrl, R.string.pref_test_url, R.string.help_test_url)

        findViewById<View>(R.id.ivHelpProxyOnly).setOnClickListener { showInfoDialog(getString(R.string.pref_proxy_only), getString(R.string.help_proxy_only)) }
        findViewById<View>(R.id.ivHelpSocksMode).setOnClickListener { showInfoDialog(getString(R.string.pref_socks_mode), getString(R.string.help_socks_mode)) }
        findViewById<View>(R.id.ivHelpCmdArgs).setOnClickListener { showInfoDialog(getString(R.string.pref_cmd_args), getString(R.string.help_cmd_args)) }
        findViewById<View>(R.id.ivHelpManualMode).setOnClickListener { showInfoDialog(getString(R.string.pref_manual_mode), getString(R.string.help_manual_mode)) }
        findViewById<View>(R.id.ivHelpVerbosity).setOnClickListener { showInfoDialog(getString(R.string.pref_verbosity), getString(R.string.help_verbosity)) }
        ivHelpDnsStrategy.setOnClickListener { showInfoDialog(getString(R.string.pref_tun_dns_strategy), getString(R.string.help_tun_dns_strategy)) }
        
        findViewById<TextInputLayout>(R.id.tilCmdPreview).setEndIconOnClickListener { showInfoDialog(getString(R.string.pref_cmd_args), getString(R.string.help_cmd_args)) }
    }

    private fun setupLivePreview() {
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { updateCmdPreview() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        val fields = listOf(etBindAddress, etFakeSni, etUpstreamProxy, etBootstrapDns, etTestUrl, etApiAddress)
        fields.forEach { it.addTextChangedListener(watcher) }
        
        spVerbosity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) { updateCmdPreview() }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun updateCmdPreview() {
        if (swManualMode.isChecked) return
        val sb = StringBuilder()
        sb.append("-bind-address ").append(etBindAddress.text.toString().ifEmpty { DEFAULT_BIND })
        sb.append(" -country ").append(mainCountry)
        val verbosityValues = resources.getStringArray(R.array.verbosity_values)
        val vPos = spVerbosity.selectedItemPosition.coerceIn(0, verbosityValues.size - 1)
        sb.append(" -verbosity ").append(verbosityValues[vPos])
        
        val bootstrap = etBootstrapDns.text.toString()
        if (bootstrap.isNotEmpty()) sb.append(" -bootstrap-dns ").append(bootstrap)
        val sni = etFakeSni.text.toString()
        if (sni.isNotEmpty()) sb.append(" -fake-SNI ").append(sni)
        val proxy = etUpstreamProxy.text.toString()
        if (proxy.isNotEmpty()) sb.append(" -proxy ").append(proxy)
        if (swSocksMode.isChecked) sb.append(" -socks-mode")
        val api = etApiAddress.text.toString()
        if (api.isNotEmpty()) sb.append(" -api-address ").append(api)
        val test = etTestUrl.text.toString()
        if (test.isNotEmpty()) sb.append(" -server-selection-test-url ").append(test)
        
        etCmdPreview.setText(sb.toString())
    }

    private fun setupApiAddressLogic() {
        tilApiAddress.setEndIconOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add("api.sec-tunnel.com")
            popup.menu.add("api2.sec-tunnel.com")
            popup.menu.add("Справка")
            popup.setOnMenuItemClickListener { item ->
                if (item.title == "Справка") {
                    showInfoDialog(getString(R.string.pref_api_address), getString(R.string.help_api_address))
                } else {
                    etApiAddress.setText(item.title)
                    checkForBootstrapConflict()
                }
                true
            }
            popup.show()
        }
    }

    private fun checkForBootstrapConflict() {
        if (etBootstrapDns.text.toString().isNotEmpty() && etBootstrapDns.text.toString() != " ") {
            AlertDialog.Builder(this)
                .setTitle(R.string.dialog_clear_bootstrap_title)
                .setMessage(R.string.dialog_clear_bootstrap_msg)
                .setPositiveButton("Да") { _, _ ->
                    etBootstrapDns.setText("")
                    updateCmdPreview()
                }
                .setNegativeButton("Нет", null)
                .show()
        }
    }

    private fun applyWhitelistPreset() {
        // 1. Обновляем UI
        etFakeSni.setText("vk.com")
        etBootstrapDns.setText("https://77.88.8.8/dns-query,https://8.8.8.8/dns-query,https://1.1.1.1/dns-query")
        etApiAddress.setText("")
        etUpstreamProxy.setText("")
        etTestUrl.setText("https://ok.ru/favicon.ico")
        swSocksMode.isChecked = true
        swManualMode.isChecked = false
        etCmdPreview.setText("")
        etCmdPreview.isEnabled = false

        // 2. КРИТИЧЕСКИЙ МОМЕНТ: Немедленное сохранение в SharedPreferences (как в оригинале)
        prefs.edit()
            .putString("FAKE_SNI", "vk.com")
            .putString("BOOTSTRAP_DNS", "https://77.88.8.8/dns-query,https://8.8.8.8/dns-query,https://1.1.1.1/dns-query")
            .putString("API_ADDRESS", "")
            .putString("UPSTREAM_PROXY", "")
            .putString("TEST_URL", "https://ok.ru/favicon.ico")
            .putBoolean("SOCKS_MODE", true)
            .putBoolean("MANUAL_CMD_MODE", false)
            .putString("CUSTOM_CMD_STRING", "")
            .commit() // Используем commit для немедленной записи

        updateCmdPreview()
        
        // 3. Показываем оригинальный диалог-гайд
        AlertDialog.Builder(this)
            .setTitle(R.string.whitelist_preset_guide_title)
            .setMessage(R.string.whitelist_preset_guide)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showInfoDialog(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }

    private fun loadValues() {
        swProxyOnly.isChecked = prefs.getBoolean("PROXY_ONLY", false)
        etBindAddress.setText(prefs.getString("BIND_ADDRESS", DEFAULT_BIND))
        etFakeSni.setText(prefs.getString("FAKE_SNI", ""))
        etUpstreamProxy.setText(prefs.getString("UPSTREAM_PROXY", ""))
        etBootstrapDns.setText(prefs.getString("BOOTSTRAP_DNS", DEFAULT_BOOTSTRAP))
        swSocksMode.isChecked = prefs.getBoolean("SOCKS_MODE", false)
        etApiAddress.setText(prefs.getString("API_ADDRESS", ""))
        etTestUrl.setText(prefs.getString("TEST_URL", DEFAULT_TEST_URL))
        
        val strategyVal = prefs.getInt("TUN2PROXY_DNS_STRATEGY", 1)
        val strategyValues = resources.getStringArray(R.array.dns_strategy_values)
        spDnsStrategy.setSelection(strategyValues.indexOf(strategyVal.toString()).coerceAtLeast(0))

        val verbosityVal = prefs.getInt("VERBOSITY", 20)
        val verbosityValues = resources.getStringArray(R.array.verbosity_values)
        spVerbosity.setSelection(verbosityValues.indexOf(verbosityVal.toString()).coerceAtLeast(0))

        val isManual = prefs.getBoolean("MANUAL_CMD_MODE", false)
        swManualMode.isChecked = isManual
        etCmdPreview.isEnabled = isManual
        if (isManual) etCmdPreview.setText(prefs.getString("CUSTOM_CMD_STRING", "")) else updateCmdPreview()
    }

    private fun validateInputs(): Boolean {
        if (!etBindAddress.text.toString().matches(Regex("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?):([0-9]{1,5})$"))) {
            findViewById<TextInputLayout>(R.id.tilBindAddress).error = getString(R.string.err_invalid_format)
            return false
        }
        return true
    }

    private fun saveValues() {
        val verbosityValues = resources.getStringArray(R.array.verbosity_values)
        val strategyValues = resources.getStringArray(R.array.dns_strategy_values)
        
        val vPos = spVerbosity.selectedItemPosition.coerceIn(0, verbosityValues.size - 1)
        val sPos = spDnsStrategy.selectedItemPosition.coerceIn(0, strategyValues.size - 1)

        val verbosityStr = verbosityValues.getOrNull(vPos) ?: "20"
        val strategyStr = strategyValues.getOrNull(sPos) ?: "1"

        prefs.edit().apply {
            putBoolean("PROXY_ONLY", swProxyOnly.isChecked)
            putString("BIND_ADDRESS", etBindAddress.text?.toString() ?: DEFAULT_BIND)
            putString("FAKE_SNI", etFakeSni.text?.toString() ?: "")
            putString("UPSTREAM_PROXY", etUpstreamProxy.text?.toString() ?: "")
            putString("BOOTSTRAP_DNS", etBootstrapDns.text?.toString() ?: DEFAULT_BOOTSTRAP)
            putBoolean("SOCKS_MODE", swSocksMode.isChecked)
            putString("API_ADDRESS", etApiAddress.text?.toString() ?: "")
            putInt("VERBOSITY", verbosityStr.toIntOrNull() ?: 20)
            putInt("TUN2PROXY_DNS_STRATEGY", strategyStr.toIntOrNull() ?: 1)
            putString("TEST_URL", etTestUrl.text?.toString() ?: DEFAULT_TEST_URL)
            putBoolean("MANUAL_CMD_MODE", swManualMode.isChecked)
            putString("CUSTOM_CMD_STRING", etCmdPreview.text?.toString() ?: "")
            apply()
        }
    }

    private fun resetDefaults() {
        prefs.edit().clear().apply()
        loadValues()
    }

    private fun startExport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"; putExtra(Intent.EXTRA_TITLE, "PortalConnect_config.json") }
        exportJsonLauncher.launch(intent)
    }

    private fun startImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/json" }
        importJsonLauncher.launch(intent)
    }

    private fun startLogExport() {
        val name = "PortalConnect_log_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.txt"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "text/plain"; putExtra(Intent.EXTRA_TITLE, name) }
        exportLogLauncher.launch(intent)
    }

    private fun writeSettingsToUri(uri: Uri) {
        try {
            val json = JSONObject(prefs.all)
            contentResolver.openOutputStream(uri)?.use { it.write(json.toString(4).toByteArray()) }
            Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun readSettingsFromUri(uri: Uri) {
        try {
            val json = JSONObject(contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: "{}")
            val editor = prefs.edit()
            json.keys().forEach { key ->
                val v = json.get(key)
                when (v) {
                    is Boolean -> editor.putBoolean(key, v)
                    is Int -> editor.putInt(key, v)
                    is String -> editor.putString(key, v)
                }
            }
            editor.apply()
            loadValues()
            Toast.makeText(this, "Загружено", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun writeLogToUri(uri: Uri) {
        try {
            val log = MainActivity.INSTANCE?.getLatestLogText() ?: ""
            contentResolver.openOutputStream(uri)?.use { it.write(log.toByteArray()) }
            Toast.makeText(this, "Лог сохранен", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show() }
    }
}
