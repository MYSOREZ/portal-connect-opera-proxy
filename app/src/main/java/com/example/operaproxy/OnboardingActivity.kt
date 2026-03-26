package com.example.operaproxy

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

class OnboardingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("OperaProxyPrefs", Context.MODE_PRIVATE)
        
        var packageInfo: PackageInfo? = null
        try {
            packageInfo = if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                packageManager.getPackageInfo(packageName, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val firstInstallTime = packageInfo?.firstInstallTime ?: 0L

        if (firstInstallTime <= prefs.getLong("LAST_INSTALL_TIME", 0L) && prefs.getBoolean("ONBOARDING_DONE", false)) {
            startMainAndFinish()
            return
        }

        prefs.edit().putInt("COUNTRY_ID", R.id.rbAuto).apply()

        setContentView(R.layout.activity_onboarding)

        val viewPager = findViewById<ViewPager2>(R.id.onboardingPager)
        val btnStart = findViewById<Button>(R.id.btnOnboardingStart)
        val btnNext = findViewById<Button>(R.id.btnOnboardingNext)

        val pages = listOf(
            OnboardingPage(getString(R.string.onboarding_welcome_title), getString(R.string.onboarding_welcome_desc)),
            OnboardingPage(getString(R.string.onboarding_features_title), getString(R.string.onboarding_features_desc)),
            OnboardingPage(getString(R.string.onboarding_start_title), getString(R.string.onboarding_start_desc))
        )

        val adapter = OnboardingAdapter(pages)
        viewPager.adapter = adapter

        btnStart.setOnClickListener {
            prefs.edit()
                .putBoolean("ONBOARDING_DONE", true)
                .putLong("LAST_INSTALL_TIME", firstInstallTime)
                .apply()
            startMainAndFinish()
        }

        btnNext.setOnClickListener {
            viewPager.setCurrentItem(viewPager.currentItem + 1, true)
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateButtons(adapter, btnStart, btnNext, position)
            }
        })
        
        updateButtons(adapter, btnStart, btnNext, 0)
    }

    private fun updateButtons(adapter: OnboardingAdapter, btnStart: Button, btnNext: Button, position: Int) {
        val isLast = position == adapter.itemCount - 1
        btnStart.visibility = if (isLast) View.VISIBLE else View.GONE
        btnNext.visibility = if (isLast || adapter.itemCount == 1) View.GONE else View.VISIBLE
    }

    private fun startMainAndFinish() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    data class OnboardingPage(val title: String, val desc: String)

    class OnboardingAdapter(private val pages: List<OnboardingPage>) : RecyclerView.Adapter<OnboardingAdapter.Holder>() {
        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.onboardingTitle)
            val desc: TextView = v.findViewById(R.id.onboardingDesc)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_onboarding_page, parent, false)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.title.text = pages[position].title
            holder.desc.text = pages[position].desc
        }

        override fun getItemCount() = pages.size
    }
}
