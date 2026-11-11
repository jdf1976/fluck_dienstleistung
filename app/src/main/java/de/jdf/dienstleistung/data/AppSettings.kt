package de.jdf.dienstleistung.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Hell"), DARK("Dunkel") }

/** Kleine App-Einstellungen (SharedPreferences). */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString("themeMode", null) ?: "") }.getOrDefault(ThemeMode.SYSTEM)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("themeMode", mode.name).apply()
        _themeMode.value = mode
    }
}
