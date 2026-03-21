package com.example.operaproxy

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.RecyclerView

/**
 * Экран приветствия и первичной настройки PortalConnect.
 */
class OnboardingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val btnNext = findViewById<Button>(R.id.btnNext)
        val btnSkip = findViewById<Button>(R.id.btnSkip)

        val pages = listOf(
            OnboardingPage("Добро пожаловать", "PortalConnect — это графическая оболочка для Opera Proxy с расширенными возможностями."),
            OnboardingPage("Обход блокировок", "Используйте Fake SNI и кастомные DNS для доступа к заблокированным ресурсам."),
            OnboardingPage("Безопасность", "Настройте PIN-код и Kill Switch в расширенных настройках для защиты вашего трафика.")
        )

        viewPager.adapter = OnboardingAdapter(pages)

        btnNext.setOnClickListener {
            if (viewPager.currentItem < pages.size - 1) {
                viewPager.currentItem += 1
            } else {
                finishOnboarding()
            }
        }

        btnSkip.setOnClickListener {
            finishOnboarding()
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                btnNext.text = if (position == pages.size - 1) "Начать" else "Далее"
            }
        })
    }

    private fun finishOnboarding() {
        getSharedPreferences("OperaProxyPrefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("ONBOARDING_FINISHED", true)
            .apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    data class OnboardingPage(val title: String, val description: String)

    class OnboardingAdapter(private val pages: List<OnboardingPage>) : RecyclerView.Adapter<OnboardingAdapter.Holder>() {
        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.tvTitle)
            val desc: TextView = v.findViewById(R.id.tvDescription)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_onboarding_page, parent, false)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.title.text = pages[position].title
            holder.desc.text = pages[position].description
        }

        override fun getItemCount() = pages.size
    }
}
