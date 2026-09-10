package com.freedomfighter.readersmindful.ui

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.freedomfighter.readersmindful.App
import com.freedomfighter.readersmindful.BellService
import com.freedomfighter.readersmindful.R
import com.freedomfighter.readersmindful.data.Bell
import com.freedomfighter.readersmindful.data.Bowls
import com.freedomfighter.readersmindful.data.FontChoice
import com.freedomfighter.readersmindful.data.Schedule
import com.freedomfighter.readersmindful.data.Session
import com.freedomfighter.readersmindful.data.TextSize
import kotlinx.coroutines.delay
import java.util.Calendar

sealed class Screen {
    data object Home : Screen()
    data object Schedule : Screen()
    data object Settings : Screen()
}

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = stack.last()
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.size - 1) }
}

fun clock(millis: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}

/** "mm:ss" left until [at], never negative. */
fun countdown(at: Long, now: Long): String {
    val s = ((at - now).coerceAtLeast(0L) + 999) / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

/** A clock that ticks once a second while something runs, so countdowns move. */
@Composable
fun rememberNow(running: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) { while (running) { now = System.currentTimeMillis(); delay(1000L - now % 1000) } ; now = System.currentTimeMillis() }
    return now
}

// ---------------------------------------------------------------------------------------------
// Home: the three functions, each one line. Tap the minutes to change them, ▶ to start, ■ to stop.
// ---------------------------------------------------------------------------------------------

@Composable
fun HomeScreen(nav: Nav, app: App) {
    val context = LocalContext.current
    val colors = LocalColors.current
    val typo = LocalTypo.current
    val settings by app.prefs.settings.collectAsState()
    val live = Session.Live
    var menu by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf<String?>(null) }   // interval | period
    val now = rememberNow(live.intervalRunning || live.periodRunning)
    val bells = remember(nav.stack.size) { Schedule.load(context) }
    LaunchedEffect(Unit) { live.sync(context) }
    val tick = rememberTick()
    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.app_title), onBack = null, trailing = "⋯", onTrailing = { menu = true })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                VSpace(10.dp)
                // ---- reminder every X minutes ----
                FunctionRow(
                    title = stringResource(R.string.f_interval),
                    minutes = if (live.intervalRunning) live.intervalMin else settings.intervalMin,
                    running = live.intervalRunning,
                    detail = if (live.intervalRunning) stringResource(R.string.next_bowl, clock(live.nextBellAt), countdown(live.nextBellAt, now)) + (if (live.rang > 0) " · " + stringResource(R.string.rang, live.rang) else "") else stringResource(R.string.f_interval_hint),
                    onMinutes = { prompt = "interval" },
                    onToggle = {
                        tick()
                        if (live.intervalRunning) BellService.stopInterval(context) else BellService.startInterval(context, settings.intervalMin)
                    }
                )
                Rule(Modifier.padding(vertical = 6.dp))
                // ---- meditation period of X minutes ----
                FunctionRow(
                    title = stringResource(R.string.f_period),
                    minutes = if (live.periodRunning) live.periodMin else settings.durationMin,
                    running = live.periodRunning,
                    detail = if (live.periodRunning) stringResource(R.string.until, clock(live.periodEndsAt), countdown(live.periodEndsAt, now)) else stringResource(R.string.f_period_hint),
                    onMinutes = { prompt = "period" },
                    onToggle = {
                        tick()
                        if (live.periodRunning) BellService.stopPeriod(context) else BellService.startPeriod(context, settings.durationMin)
                    }
                )
                if (live.periodRunning) {
                    val f = ((now - live.periodStartedAt).toFloat() / (live.periodMin * 60_000f)).coerceIn(0f, 1f)
                    Progress(f, Modifier.padding(horizontal = rowPadH).padding(top = 2.dp, bottom = 10.dp))
                }
                Rule(Modifier.padding(vertical = 6.dp))
                // ---- bowls at set times ----
                val enabled = bells.filter { it.enabled }
                TextRow(
                    stringResource(R.string.f_schedule),
                    secondary = if (enabled.isEmpty()) stringResource(R.string.f_schedule_hint) else enabled.joinToString("  ") { it.clock + if (it.three) " ×3" else "" },
                    onClick = { nav.push(Screen.Schedule) }
                )
                Rule(Modifier.padding(vertical = 6.dp))
                TextRow(if (settings.bowl == Bowls.CUSTOM && Bowls.customFile(context) != null) Bowls.customName(context) else Bowls.byKey(settings.bowl).label, secondary = stringResource(R.string.bowl), size = typo.title) { nav.push(Screen.Settings) }
            }
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
        if (menu) TextMenu(null, listOf(
            MenuItem(stringResource(R.string.f_schedule)) { nav.push(Screen.Schedule) },
            MenuItem(stringResource(R.string.listen)) { BellService.preview(context, false) }
        ), onDismiss = { menu = false }, footer = listOf(
            MenuItem(if (colors.isDark) stringResource(R.string.theme_light) else stringResource(R.string.theme_dark)) { app.prefs.toggleTheme(colors.isDark) },
            MenuItem(stringResource(R.string.settings)) { nav.push(Screen.Settings) }
        ))
        prompt?.let { which ->
            MinutesPrompt(
                title = stringResource(if (which == "interval") R.string.every_minutes else R.string.minutes),
                initial = if (which == "interval") settings.intervalMin else settings.durationMin,
                onDone = { m -> if (which == "interval") app.prefs.setIntervalMin(m) else app.prefs.setDurationMin(m); prompt = null },
                onCancel = { prompt = null }
            )
        }
    }
}

/** One function on one line: the title with the minutes underlined as the thing to tap, and ▶ or ■ at the end. */
@Composable
fun FunctionRow(title: String, minutes: Int, running: Boolean, detail: String, onMinutes: () -> Unit, onToggle: () -> Unit) {
    val colors = LocalColors.current
    val typo = LocalTypo.current
    Row(Modifier.fillMaxWidth().padding(horizontal = rowPadH, vertical = rowPadV * 0.6f), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).then(if (running) Modifier else Modifier.noRippleClickable(onClick = onMinutes))) {
            Row(verticalAlignment = Alignment.Bottom) {
                T(title, maxLines = 1, align = TextAlign.Start)
                Box(Modifier.padding(start = 10.dp).width(IntrinsicSize.Min)) {
                    T("$minutes", maxLines = 1, align = TextAlign.Start, color = colors.fg)
                    if (!running) Rule(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp), color = colors.fg)
                }
                Small(" " + stringResource(R.string.min), Modifier.padding(bottom = 5.dp), maxLines = 1, align = TextAlign.Start)
            }
            Small(detail, maxLines = 2, align = TextAlign.Start)
        }
        Box(
            Modifier.padding(start = 16.dp).background(if (running) colors.fg else Color.Transparent).noRippleClickable(onClick = onToggle).padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            T(if (running) "■" else "▶", size = typo.tile * 0.9f, color = if (running) colors.bg else colors.fg, align = TextAlign.Center)
        }
    }
}

/** A hairline, [fraction] of it in the foreground colour. */
@Composable
fun Progress(fraction: Float, modifier: Modifier = Modifier) {
    val colors = LocalColors.current
    Canvas(modifier.fillMaxWidth().height(3.dp)) {
        drawRect(colors.rule, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height / 3), size = androidx.compose.ui.geometry.Size(size.width, size.height / 3))
        drawRect(colors.fg, size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height))
    }
}

@Composable
fun MinutesPrompt(title: String, initial: Int, onDone: (Int) -> Unit, onCancel: () -> Unit) {
    var bad by remember { mutableStateOf(false) }
    TextPrompt(title + (if (bad) "  (${Session.MIN_MINUTES}–${Session.MAX_MINUTES})" else ""), initial.toString(), keyboard = KeyboardType.Number, onDone = { text ->
        val m = text.trim().toIntOrNull()
        if (m != null && m in Session.MIN_MINUTES..Session.MAX_MINUTES) onDone(m) else bad = true
    }, onCancel = onCancel)
}

@Composable
fun TimePrompt(title: String, initial: String, onDone: (Int, Int) -> Unit, onCancel: () -> Unit) {
    var bad by remember { mutableStateOf(false) }
    TextPrompt(title + (if (bad) "  (hh:mm)" else ""), initial, keyboard = KeyboardType.Number, onDone = { text ->
        val m = Regex("^\\s*(\\d{1,2})\\s*[:hH.]?\\s*(\\d{2})?\\s*$").find(text)
        val h = m?.groupValues?.get(1)?.toIntOrNull(); val mi = m?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        if (m != null && h != null && h in 0..23 && mi in 0..59) onDone(h, mi) else bad = true
    }, onCancel = onCancel)
}

// ---------------------------------------------------------------------------------------------
// Bowls at set times: a list of times, each one or three strikes, on or off.
// ---------------------------------------------------------------------------------------------

@Composable
fun ScheduleScreen(nav: Nav, app: App) {
    val context = LocalContext.current
    val typo = LocalTypo.current
    var version by remember { mutableIntStateOf(0) }
    val bells = remember(version) { Schedule.load(context) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Bell?>(null) }
    var retiming by remember { mutableStateOf<Bell?>(null) }
    BackHandler { nav.pop() }
    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.f_schedule), onBack = { nav.pop() })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Small(stringResource(R.string.schedule_hint), Modifier.padding(horizontal = rowPadH).padding(top = 16.dp, bottom = 6.dp), maxLines = 4)
                bells.forEach { b ->
                    TextRow(
                        b.clock + "   " + stringResource(if (b.three) R.string.three_bowls else R.string.one_bowl),
                        inverted = b.enabled,
                        secondary = stringResource(if (b.enabled) R.string.on else R.string.off),
                        onClick = { editing = b }
                    )
                }
                if (bells.isEmpty()) Small(stringResource(R.string.no_bells), Modifier.padding(horizontal = rowPadH, vertical = 10.dp))
            }
            Rule()
            TextRow(stringResource(R.string.new_time), size = typo.title) { adding = true }
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
        if (adding) TimePrompt(stringResource(R.string.new_time_title), "", onDone = { h, m -> Schedule.add(context, h, m, three = true); version++; adding = false }, onCancel = { adding = false })
        editing?.let { b ->
            TextMenu(b.clock, listOf(
                MenuItem(stringResource(if (b.enabled) R.string.turn_off else R.string.turn_on)) { Schedule.update(context, b.copy(enabled = !b.enabled)); version++ },
                MenuItem(stringResource(if (b.three) R.string.one_bowl else R.string.three_bowls)) { Schedule.update(context, b.copy(three = !b.three)); version++ },
                MenuItem(stringResource(R.string.change_time)) { retiming = b },
                MenuItem(stringResource(R.string.delete)) { Schedule.remove(context, b.id); version++ }
            ), onDismiss = { editing = null })
        }
        retiming?.let { b ->
            TimePrompt(stringResource(R.string.change_time), b.clock, onDone = { h, m -> Schedule.update(context, b.copy(hour = h, minute = m)); version++; retiming = null }, onCancel = { retiming = null })
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Settings: the bowl, the look, the battery exemption
// ---------------------------------------------------------------------------------------------

@Composable
fun SettingsScreen(nav: Nav, app: App) {
    val context = LocalContext.current
    val s by app.prefs.settings.collectAsState()
    val colors = LocalColors.current
    val typo = LocalTypo.current
    val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
    var battOk by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName)) }
    LaunchedEffect(Unit) { while (true) { delay(1500); battOk = pm.isIgnoringBatteryOptimizations(context.packageName) } }
    BackHandler { nav.pop() }
    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.settings), onBack = { nav.pop() })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Small(stringResource(R.string.bowl_hint), Modifier.padding(horizontal = rowPadH).padding(top = 16.dp, bottom = 4.dp), maxLines = 3)
                Bowls.ALL.forEach { b ->
                    TextRow(b.label, inverted = b.key == s.bowl, size = typo.title) { app.prefs.setBowl(b.key); BellService.preview(context, false) }
                }
                val customName = Bowls.customName(context)
                val hasCustom = Bowls.customFile(context) != null
                var customMenu by remember { mutableStateOf(false) }
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null && Bowls.importCustom(context, uri)) BellService.preview(context, false)
                }
                TextRow(
                    if (hasCustom) customName else stringResource(R.string.custom_sound),
                    inverted = hasCustom && s.bowl == Bowls.CUSTOM,
                    secondary = stringResource(if (hasCustom) R.string.custom_sound_hint_set else R.string.custom_sound_hint),
                    size = typo.title
                ) { if (hasCustom) customMenu = true else picker.launch(arrayOf("audio/*", "application/octet-stream")) }
                if (customMenu) TextMenu(customName, listOf(
                    MenuItem(stringResource(R.string.use_this_sound)) { app.prefs.setBowl(Bowls.CUSTOM); BellService.preview(context, false) },
                    MenuItem(stringResource(R.string.pick_another_file)) { picker.launch(arrayOf("audio/*", "application/octet-stream")) }
                ), onDismiss = { customMenu = false })
                Rule(Modifier.padding(vertical = 8.dp))
                TextRow(stringResource(if (battOk) R.string.battery_ok else R.string.battery_fix), secondary = stringResource(R.string.battery_hint), size = typo.title) {
                    if (!battOk) runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:${context.packageName}"))) }
                }
                Rule(Modifier.padding(vertical = 8.dp))
                TextRow(if (colors.isDark) stringResource(R.string.theme_dark) else stringResource(R.string.theme_light), secondary = stringResource(R.string.colours)) { app.prefs.toggleTheme(colors.isDark) }
                TextRow(when (s.textSize) { TextSize.SMALL -> "S"; TextSize.MEDIUM -> "M"; TextSize.LARGE -> "L" }, secondary = stringResource(R.string.text_size)) {
                    app.prefs.setTextSize(when (s.textSize) { TextSize.SMALL -> TextSize.MEDIUM; TextSize.MEDIUM -> TextSize.LARGE; TextSize.LARGE -> TextSize.SMALL })
                }
                TextRow(when (s.font) { FontChoice.SANS -> "sans-serif"; FontChoice.SERIF -> "serif"; FontChoice.MONO -> "mono" }, secondary = stringResource(R.string.font)) {
                    app.prefs.setFont(when (s.font) { FontChoice.SANS -> FontChoice.SERIF; FontChoice.SERIF -> FontChoice.MONO; FontChoice.MONO -> FontChoice.SANS })
                }
                TextRow(if (s.haptics) stringResource(R.string.on) else stringResource(R.string.off), secondary = stringResource(R.string.haptics)) { app.prefs.setHaptics(!s.haptics) }
                Rule(Modifier.padding(vertical = 8.dp))
                TextRow(stringResource(R.string.app_name), secondary = stringResource(R.string.about)) { }
            }
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}
