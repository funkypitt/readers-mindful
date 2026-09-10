package com.freedomfighter.readersmindful.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The two running things — a reminder every X minutes, a meditation period of X
 * minutes — in the two places they have to be known.
 *
 * The durable copy lives in SharedPreferences: the alarm that rings a bowl arrives
 * in a receiver that may find the process was killed in between, and it has to
 * work out on its own which bowl is due and when the next one falls. [Live] is the
 * in-memory mirror the screen and the tiles observe.
 */
object Session {
    private const val PREFS = "session"
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 240

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- reminder every X minutes ----
    fun intervalRunning(ctx: Context) = sp(ctx).getBoolean("i_running", false)
    fun intervalMin(ctx: Context) = sp(ctx).getInt("i_min", 10)
    fun intervalStartedAt(ctx: Context) = sp(ctx).getLong("i_started", 0L)
    /** How many bowls have already rung in this reminder session. */
    fun intervalRang(ctx: Context) = sp(ctx).getInt("i_rang", 0)

    fun beginInterval(ctx: Context, minutes: Int, startedAt: Long) =
        sp(ctx).edit().putBoolean("i_running", true).putInt("i_min", minutes).putLong("i_started", startedAt).putInt("i_rang", 0).apply()
    fun recordRang(ctx: Context, n: Int) = sp(ctx).edit().putInt("i_rang", n).apply()
    fun endInterval(ctx: Context) = sp(ctx).edit().putBoolean("i_running", false).apply()

    /**
     * When bowl number [n] falls, counted from the start rather than from the
     * previous bowl: adding an interval to "now" at each ring would fold every
     * wake-up delay into the next gap and drift minutes off the clock by evening.
     */
    fun bellAt(startedAt: Long, minutes: Int, n: Int): Long = startedAt + n.toLong() * minutes * 60_000L

    /** The next bowl still ahead of [now] (a missed one is skipped, never rung late). */
    fun nextInterval(ctx: Context, now: Long = System.currentTimeMillis()): Pair<Int, Long> {
        val s = intervalStartedAt(ctx); val m = intervalMin(ctx)
        var n = intervalRang(ctx) + 1
        while (bellAt(s, m, n) <= now) n++
        return n to bellAt(s, m, n)
    }

    // ---- meditation period of X minutes ----
    fun periodRunning(ctx: Context) = sp(ctx).getBoolean("p_running", false)
    fun periodMin(ctx: Context) = sp(ctx).getInt("p_min", 20)
    fun periodStartedAt(ctx: Context) = sp(ctx).getLong("p_started", 0L)
    fun periodEndsAt(ctx: Context) = periodStartedAt(ctx) + periodMin(ctx) * 60_000L

    fun beginPeriod(ctx: Context, minutes: Int, startedAt: Long) =
        sp(ctx).edit().putBoolean("p_running", true).putInt("p_min", minutes).putLong("p_started", startedAt).apply()
    fun endPeriod(ctx: Context) = sp(ctx).edit().putBoolean("p_running", false).apply()

    fun anyRunning(ctx: Context) = intervalRunning(ctx) || periodRunning(ctx)

    /** A session whose alarm no longer exists (dropped, and nothing re-armed it) is ended
     *  rather than shown as a countdown that never reaches zero. Called when the app comes
     *  to the front — never from a concurrent read. */
    fun reconcile(ctx: Context) {
        if (intervalRunning(ctx) && !Alarms.hasArmed(ctx, Alarms.RING_INTERVAL)) endInterval(ctx)
        if (periodRunning(ctx) && !Alarms.hasArmed(ctx, Alarms.RING_PERIOD)) endPeriod(ctx)
    }

    /** In-memory mirror, observed by the screen. */
    object Live {
        var intervalRunning by mutableStateOf(false)
        var intervalMin by mutableIntStateOf(10)
        var nextBellAt by mutableLongStateOf(0L)
        var rang by mutableIntStateOf(0)
        var periodRunning by mutableStateOf(false)
        var periodMin by mutableIntStateOf(20)
        var periodStartedAt by mutableLongStateOf(0L)
        var periodEndsAt by mutableLongStateOf(0L)

        /** Refresh from storage. A pure read: it runs from the provider too, on a binder
         *  thread, possibly in the instant between a session being written and its alarm
         *  being armed — so it must never end anything (see [reconcile]). */
        fun sync(ctx: Context) {
            intervalRunning = intervalRunning(ctx)
            intervalMin = intervalMin(ctx)
            rang = intervalRang(ctx)
            nextBellAt = if (intervalRunning) nextInterval(ctx).second else 0L
            periodRunning = periodRunning(ctx)
            periodMin = periodMin(ctx)
            periodStartedAt = periodStartedAt(ctx)
            periodEndsAt = if (periodRunning) periodEndsAt(ctx) else 0L
        }
    }
}
