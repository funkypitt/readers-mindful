package com.freedomfighter.readersmindful.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.freedomfighter.readersmindful.BellService
import com.freedomfighter.readersmindful.R
import com.freedomfighter.readersmindful.data.Prefs
import com.freedomfighter.readersmindful.data.Session
import java.util.Calendar

/** What a widget shows, chosen when it is placed. */
enum class WidgetMode { BOTH, INTERVAL, PERIOD }

/**
 * The standard home-screen widget for any launcher: the reminder, the meditation
 * period, or both stacked. Each function is one line — minus, minutes, plus, and
 * play; while it runs the line shows a live countdown (a Chronometer, so no
 * refresh is needed) and a stop mark. The period draws a thin progress rule.
 */
object MindfulWidgets {
    const val ACTION_ADJUST = "com.freedomfighter.readersmindful.widget.ADJUST"
    private const val EXTRA_WHICH = "which"
    private const val EXTRA_DELTA = "delta"
    /** Values the minus/plus taps walk through. */
    val INTERVAL_STEPS = listOf(1, 2, 3, 5, 10, 15, 20, 30, 45, 60, 90, 120)
    val DURATION_STEPS = listOf(5, 10, 15, 20, 25, 30, 40, 45, 60, 90, 120)

    fun mode(ctx: Context, id: Int): WidgetMode = runCatching { WidgetMode.valueOf(ctx.getSharedPreferences("widgets", Context.MODE_PRIVATE).getString("mode_$id", null) ?: "BOTH") }.getOrDefault(WidgetMode.BOTH)
    fun setMode(ctx: Context, id: Int, m: WidgetMode) = ctx.getSharedPreferences("widgets", Context.MODE_PRIVATE).edit().putString("mode_$id", m.name).apply()

    fun render(ctx: Context, mgr: AppWidgetManager, id: Int) {
        val v = RemoteViews(ctx.packageName, R.layout.widget_mindful)
        val (bg, fg, dim) = WidgetUi.colors(ctx)
        v.setInt(R.id.widget_root, "setBackgroundColor", bg)
        val mode = mode(ctx, id)
        val sp = Prefs.raw(ctx)
        v.setViewVisibility(R.id.row_interval, if (mode == WidgetMode.PERIOD) View.GONE else View.VISIBLE)
        v.setViewVisibility(R.id.row_period, if (mode == WidgetMode.INTERVAL) View.GONE else View.VISIBLE)
        v.setViewVisibility(R.id.rule_between, if (mode == WidgetMode.BOTH) View.VISIBLE else View.GONE)
        v.setInt(R.id.rule_between, "setBackgroundColor", WidgetUi.rule(fg))
        val now = System.currentTimeMillis()

        // ---- reminder every X minutes ----
        val iRunning = Session.intervalRunning(ctx) && com.freedomfighter.readersmindful.data.Alarms.hasArmed(ctx, com.freedomfighter.readersmindful.data.Alarms.RING_INTERVAL)
        val iMin = if (iRunning) Session.intervalMin(ctx) else sp.getInt(Prefs.KEY_INTERVAL, 10)
        line(v, ctx, "interval", iRunning, iMin, ctx.getString(R.string.w_interval, iMin), fg, dim)
        if (iRunning) {
            val (_, at) = Session.nextInterval(ctx, now)
            v.setTextViewText(R.id.interval_sub, ctx.getString(R.string.w_next, clock(at), Session.intervalRang(ctx)))
            v.setChronometerCountDown(R.id.interval_timer, true)
            v.setChronometer(R.id.interval_timer, SystemClock.elapsedRealtime() + (at - now), null, true)
            v.setOnClickPendingIntent(R.id.interval_play, service(ctx, BellService.ACTION_STOP_INTERVAL, 21))
        } else {
            v.setOnClickPendingIntent(R.id.interval_play, service(ctx, BellService.ACTION_START_INTERVAL, 22, iMin))
            v.setOnClickPendingIntent(R.id.interval_minus, adjust(ctx, id, "interval", -1, 23))
            v.setOnClickPendingIntent(R.id.interval_plus, adjust(ctx, id, "interval", +1, 24))
        }

        // ---- meditation period of X minutes ----
        val pRunning = Session.periodRunning(ctx) && com.freedomfighter.readersmindful.data.Alarms.hasArmed(ctx, com.freedomfighter.readersmindful.data.Alarms.RING_PERIOD)
        val pMin = if (pRunning) Session.periodMin(ctx) else sp.getInt(Prefs.KEY_DURATION, 20)
        line(v, ctx, "period", pRunning, pMin, ctx.getString(R.string.w_period, pMin), fg, dim)
        if (pRunning) {
            val ends = Session.periodEndsAt(ctx); val total = pMin * 60_000L
            v.setTextViewText(R.id.period_sub, ctx.getString(R.string.w_until, clock(ends)))
            v.setChronometerCountDown(R.id.period_timer, true)
            v.setChronometer(R.id.period_timer, SystemClock.elapsedRealtime() + (ends - now), null, true)
            v.setImageViewBitmap(R.id.period_progress, progress(fg, dim, ((now - Session.periodStartedAt(ctx)).toFloat() / total).coerceIn(0f, 1f)))
            v.setViewVisibility(R.id.period_progress, View.VISIBLE)
            v.setOnClickPendingIntent(R.id.period_play, service(ctx, BellService.ACTION_STOP_PERIOD, 31))
        } else {
            v.setViewVisibility(R.id.period_progress, View.GONE)
            v.setOnClickPendingIntent(R.id.period_play, service(ctx, BellService.ACTION_START_PERIOD, 32, pMin))
            v.setOnClickPendingIntent(R.id.period_minus, adjust(ctx, id, "period", -1, 33))
            v.setOnClickPendingIntent(R.id.period_plus, adjust(ctx, id, "period", +1, 34))
        }
        val open = Intent(ctx, com.freedomfighter.readersmindful.MainActivity::class.java)
        v.setOnClickPendingIntent(R.id.interval_body, WidgetUi.activity(ctx, open, 1))
        v.setOnClickPendingIntent(R.id.period_body, WidgetUi.activity(ctx, open, 2))
        mgr.updateAppWidget(id, v)
    }


    /** The shared part of a function line: title, colours, and which controls show. */
    private fun line(v: RemoteViews, ctx: Context, which: String, running: Boolean, minutes: Int, title: String, fg: Int, dim: Int) {
        val ids = ids(ctx, which)
        v.setTextViewText(ids.title, title)
        v.setTextViewText(ids.minutes, minutes.toString())
        v.setTextViewText(ids.play, if (running) "■" else "▶")
        listOf(ids.title, ids.minutes, ids.play, ids.minus, ids.plus, ids.timer).forEach { v.setTextColor(it, fg) }
        v.setTextColor(ids.sub, dim)
        v.setViewVisibility(ids.minus, if (running) View.GONE else View.VISIBLE)
        v.setViewVisibility(ids.plus, if (running) View.GONE else View.VISIBLE)
        v.setViewVisibility(ids.minutes, if (running) View.GONE else View.VISIBLE)
        v.setViewVisibility(ids.timer, if (running) View.VISIBLE else View.GONE)
        v.setViewVisibility(ids.sub, if (running) View.VISIBLE else View.GONE)
    }

    private class Ids(val title: Int, val sub: Int, val minutes: Int, val minus: Int, val plus: Int, val play: Int, val timer: Int)
    private fun ids(ctx: Context, which: String) = if (which == "interval")
        Ids(R.id.interval_title, R.id.interval_sub, R.id.interval_minutes, R.id.interval_minus, R.id.interval_plus, R.id.interval_play, R.id.interval_timer)
    else Ids(R.id.period_title, R.id.period_sub, R.id.period_minutes, R.id.period_minus, R.id.period_plus, R.id.period_play, R.id.period_timer)

    private fun service(ctx: Context, action: String, code: Int, minutes: Int = 0): PendingIntent =
        PendingIntent.getForegroundService(ctx, code, BellService.intent(ctx, action, minutes), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun adjust(ctx: Context, id: Int, which: String, delta: Int, code: Int): PendingIntent =
        WidgetUi.broadcast(ctx, Intent(ctx, AdjustReceiver::class.java).setAction(ACTION_ADJUST).putExtra(EXTRA_WHICH, which).putExtra(EXTRA_DELTA, delta), code)

    /** Step the stored minutes along the preset list, then redraw every widget. */
    fun adjust(ctx: Context, which: String, delta: Int) {
        val sp = Prefs.raw(ctx)
        val key = if (which == "interval") Prefs.KEY_INTERVAL else Prefs.KEY_DURATION
        val steps = if (which == "interval") INTERVAL_STEPS else DURATION_STEPS
        val cur = sp.getInt(key, if (which == "interval") 10 else 20)
        val next = if (delta > 0) steps.firstOrNull { it > cur } ?: cur else steps.lastOrNull { it < cur } ?: cur
        sp.edit().putInt(key, next).apply()
        refresh(ctx)
        com.freedomfighter.readersmindful.provider.StateProvider.notify(ctx)
    }

    /** A thin rule, [fraction] of it in the foreground colour, the rest dim. */
    private fun progress(fg: Int, dim: Int, fraction: Float): Bitmap {
        val w = 600; val h = 6
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); val p = Paint()
        p.color = WidgetUi.rule(fg); c.drawRect(0f, 2f, w.toFloat(), 4f, p)
        p.color = fg; c.drawRect(0f, 0f, w * fraction, h.toFloat(), p)
        return b
    }

    private fun clock(millis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    fun refresh(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(android.content.ComponentName(ctx, MindfulWidget::class.java))
        ids.forEach { render(ctx, mgr, it) }
    }
}

class MindfulWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) { ids.forEach { MindfulWidgets.render(context, mgr, it) } }
    override fun onDeleted(context: Context, ids: IntArray) {
        val sp = context.getSharedPreferences("widgets", Context.MODE_PRIVATE).edit(); ids.forEach { sp.remove("mode_$it") }; sp.apply()
    }
}

class AdjustReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == MindfulWidgets.ACTION_ADJUST) MindfulWidgets.adjust(context, intent.getStringExtra("which") ?: return, intent.getIntExtra("delta", 0))
    }
}
