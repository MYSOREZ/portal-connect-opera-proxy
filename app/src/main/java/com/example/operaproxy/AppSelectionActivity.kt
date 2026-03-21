package com.example.operaproxy

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import java.util.*

/**
 * Выбор приложений для проксирования (PortalConnect Original Logic).
 */
class AppSelectionActivity : AppCompatActivity() {
    private lateinit var adapter: AppAdapter
    private lateinit var prefs: SharedPreferences
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchInput: EditText
    private var showSystemApps = false
    private val selectedPkgs = LinkedHashSet<String>()
    private val allApps = ArrayList<AppInfo>()

    data class AppInfo(
        val name: String,
        val packageName: String,
        val icon: Drawable,
        var isSelected: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("OperaProxyPrefs", Context.MODE_PRIVATE)
        selectedPkgs.clear()
        selectedPkgs.addAll(prefs.getStringSet("APPS", emptySet()) ?: emptySet())

        // Программное создание UI (как в оригинале)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setBackgroundColor(ContextCompat.getColor(this@AppSelectionActivity, R.color.background_app))
        }

        val toolbar = Toolbar(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@AppSelectionActivity, R.color.background_card))
            setTitleTextColor(ContextCompat.getColor(this@AppSelectionActivity, R.color.white))
            title = getString(R.string.app_selector_btn)
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener { finish() }
        }
        root.addView(toolbar)
        setSupportActionBar(toolbar)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }
        root.addView(container)

        searchInput = EditText(this).apply {
            hint = "Поиск приложений"
            setHintTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 16 }
        }
        container.addView(searchInput)

        recyclerView = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1.0f)
            layoutManager = LinearLayoutManager(this@AppSelectionActivity)
        }
        container.addView(recyclerView)

        val btnSave = MaterialButton(this).apply {
            text = getString(R.string.btn_save)
            setTextColor(ContextCompat.getColor(context, R.color.portal_accent))
            setBackgroundColor(ContextCompat.getColor(context, R.color.background_card))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 24 }
            setOnClickListener {
                persistSelection()
                Toast.makeText(this@AppSelectionActivity, "Список сохранен", Toast.LENGTH_SHORT).show()
            }
        }
        container.addView(btnSave)

        setContentView(root)

        adapter = AppAdapter(allApps)
        recyclerView.adapter = adapter

        loadApps()

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s?.toString() ?: "")
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Выбрать все").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, 2, 0, "Очистить все").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        val item = menu.add(0, 3, 0, "Системные приложения")
        item.isCheckable = true
        item.isChecked = showSystemApps
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> selectAll(true)
            2 -> selectAll(false)
            3 -> {
                showSystemApps = !showSystemApps
                item.isChecked = showSystemApps
                loadApps()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun selectAll(select: Boolean) {
        adapter.getFilteredItems().forEach {
            it.isSelected = select
            if (select) selectedPkgs.add(it.packageName) else selectedPkgs.remove(it.packageName)
        }
        adapter.notifyDataSetChanged()
    }

    private fun loadApps() {
        Thread {
            val pm = packageManager
            val apps = if (showSystemApps) {
                pm.getInstalledPackages(0).mapNotNull { pkg ->
                    val ai = pkg.applicationInfo ?: return@mapNotNull null
                    if (pkg.packageName == packageName) return@mapNotNull null
                    AppInfo(ai.loadLabel(pm).toString(), pkg.packageName, ai.loadIcon(pm), selectedPkgs.contains(pkg.packageName))
                }
            } else {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(intent, 0).mapNotNull { res ->
                    val pkg = res.activityInfo.packageName
                    if (pkg == packageName) return@mapNotNull null
                    AppInfo(res.loadLabel(pm).toString(), pkg, res.loadIcon(pm), selectedPkgs.contains(pkg))
                }
            }

            val uniqueApps = apps.distinctBy { it.packageName }
            val sortedApps = uniqueApps.sortedWith(compareByDescending<AppInfo> { it.isSelected }.thenBy { it.name.lowercase() })

            runOnUiThread {
                allApps.clear()
                allApps.addAll(sortedApps)
                adapter.filter(searchInput.text.toString())
            }
        }.start()
    }

    private fun persistSelection() {
        prefs.edit().putStringSet("APPS", selectedPkgs).apply()
    }

    override fun onPause() {
        super.onPause()
        persistSelection()
    }

    inner class AppAdapter(private val fullList: List<AppInfo>) : RecyclerView.Adapter<AppAdapter.Holder>() {
        private var filteredList = ArrayList(fullList)

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val icon = v.findViewById<ImageView>(R.id.appIcon)
            val name = v.findViewById<TextView>(R.id.appName)
            val pkg = v.findViewById<TextView>(R.id.appPkg)
            val cb = v.findViewById<MaterialCheckBox>(R.id.appCheck)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val app = filteredList[position]
            holder.icon.setImageDrawable(app.icon)
            holder.name.text = app.name
            holder.pkg.text = app.packageName
            holder.cb.setOnCheckedChangeListener(null)
            holder.cb.isChecked = app.isSelected
            holder.cb.setOnCheckedChangeListener { _, isChecked ->
                app.isSelected = isChecked
                if (isChecked) selectedPkgs.add(app.packageName) else selectedPkgs.remove(app.packageName)
            }
            holder.itemView.setOnClickListener { holder.cb.performClick() }
        }

        override fun getItemCount() = filteredList.size

        fun filter(query: String) {
            filteredList = if (query.isEmpty()) {
                ArrayList(allApps)
            } else {
                val q = query.lowercase()
                ArrayList(allApps.filter { it.name.lowercase().contains(q) || it.packageName.lowercase().contains(q) })
            }
            notifyDataSetChanged()
        }

        fun getFilteredItems() = filteredList
    }
}
