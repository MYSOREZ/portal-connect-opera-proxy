package com.example.operaproxy

import android.app.Activity
import android.content.Context

/**
 * Помощник для управления темами оформления.
 */
object ThemeHelper {
    private const val PREFS_NAME = "OperaProxyPrefs"
    private const val KEY_THEME = "THEME"

    const val THEME_DEFAULT = 0
    const val THEME_WHITE = 1
    const val THEME_AMOLED = 2
    const val THEME_PINK = 3
    const val THEME_HACKER = 4
    const val THEME_GLASS = 5

    fun applyTheme(activity: Activity) {
        activity.setTheme(getThemeResId(activity))
    }

    fun getThemeResId(context: Context): Int {
        val themeId = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_THEME, 0)
        return when (themeId) {
            THEME_WHITE -> R.style.Theme_PortalConnect_White
            THEME_AMOLED -> R.style.Theme_PortalConnect_Amoled
            THEME_PINK -> R.style.Theme_PortalConnect_Pink
            THEME_HACKER -> R.style.Theme_PortalConnect_Hacker
            THEME_GLASS -> R.style.Theme_PortalConnect_Glass
            else -> R.style.Theme_PortalConnect
        }
    }

    fun setTheme(context: Context, themeId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_THEME, themeId)
            .apply()
    }

    fun getCurrentThemeId(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_THEME, 0)
    }
}
