package com.freedomfighter.readersmindful.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { DARK, LIGHT, SYSTEM }
enum class FontChoice { SERIF, SANS, MONO }
enum class TextSize { SMALL, MEDIUM, LARGE }
enum class Align { LEFT, CENTER }

data class Settings(
    val theme: ThemeMode = ThemeMode.DARK,
    val font: FontChoice = FontChoice.SANS,
    val textSize: TextSize = TextSize.MEDIUM,
    val align: Align = Align.LEFT,
    val haptics: Boolean = true,
    /** Key of the chosen bowl, see [Bowls]. */
    val bowl: String = Bowls.DEFAULT,
    /** Minutes the two functions start on, remembered from the last use. */
    val intervalMin: Int = 10,
    val durationMin: Int = 20
)

class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> _settings.value = read() }
    init { sp.registerOnSharedPreferenceChangeListener(listener) }

    private fun read() = Settings(
        theme = enumOr(sp.getString("theme", null), ThemeMode.DARK),
        font = enumOr(sp.getString("font", null), FontChoice.SANS),
        textSize = enumOr(sp.getString("text_size", null), TextSize.MEDIUM),
        align = enumOr(sp.getString("align", null), Align.LEFT),
        haptics = sp.getBoolean("haptics", true),
        bowl = sp.getString(KEY_BOWL, Bowls.DEFAULT) ?: Bowls.DEFAULT,
        intervalMin = sp.getInt(KEY_INTERVAL, 10),
        durationMin = sp.getInt(KEY_DURATION, 20)
    )
    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default

    fun setTheme(m: ThemeMode) = sp.edit().putString("theme", m.name).apply()
    fun setFont(f: FontChoice) = sp.edit().putString("font", f.name).apply()
    fun setTextSize(t: TextSize) = sp.edit().putString("text_size", t.name).apply()
    fun setAlign(a: Align) = sp.edit().putString("align", a.name).apply()
    fun setHaptics(v: Boolean) = sp.edit().putBoolean("haptics", v).apply()
    fun setBowl(key: String) = sp.edit().putString(KEY_BOWL, key).apply()
    fun setIntervalMin(m: Int) = sp.edit().putInt(KEY_INTERVAL, m.coerceIn(Session.MIN_MINUTES, Session.MAX_MINUTES)).apply()
    fun setDurationMin(m: Int) = sp.edit().putInt(KEY_DURATION, m.coerceIn(Session.MIN_MINUTES, Session.MAX_MINUTES)).apply()
    fun toggleTheme(systemIsDark: Boolean) {
        val dark = when (_settings.value.theme) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> systemIsDark }
        setTheme(if (dark) ThemeMode.LIGHT else ThemeMode.DARK)
    }

    companion object {
        const val KEY_BOWL = "bowl"
        const val KEY_CUSTOM_NAME = "custom_name"
        const val KEY_INTERVAL = "interval_min"
        const val KEY_DURATION = "duration_min"
        /** Read without the flow, for receivers and widgets that live outside the app process lifecycle. */
        fun raw(context: Context): SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }
}
