package com.freedomfighter.readersmindful.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.freedomfighter.readersmindful.AlarmReceiver
import com.freedomfighter.readersmindful.MainActivity
import java.util.Calendar

/**
 * The reliability core, taken from Retreat Timer. Every bowl is registered with
 * [AlarmManager.setAlarmClock] — the one alarm type Android guarantees fires at
 * the exact time even in Doze, with the screen locked for hours. The OS treats it
 * as a user alarm, like the stock Clock app's, and never defers it.
 */
object Alarms {
    const val RING_INTERVAL = "com.freedomfighter.readersmindful.RING_INTERVAL"
    const val RING_PERIOD = "com.freedomfighter.readersmindful.RING_PERIOD"
    const val RING_SCHEDULED = "com.freedomfighter.readersmindful.RING_SCHEDULED"
    const val EXTRA_BELL_ID = "bell_id"
    private const val CODE_INTERVAL = 1
    private const val CODE_PERIOD = 2
    private const val CODE_SCHEDULED = 100

    private fun am(ctx: Context) = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun show(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Extras are deliberately absent: PendingIntent equality ignores them, and the service reads the session from storage. */
    private fun fire(ctx: Context, action: String, code: Int, uri: String, flags: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent? =
        PendingIntent.getBroadcast(ctx, code, Intent(ctx, AlarmReceiver::class.java).setAction(action).setData(Uri.parse(uri)), flags or PendingIntent.FLAG_IMMUTABLE)

    private fun code(action: String) = if (action == RING_INTERVAL) CODE_INTERVAL else CODE_PERIOD
    private fun uri(action: String) = if (action == RING_INTERVAL) "mindful://interval" else "mindful://period"

    /** True while a bowl is actually armed for this session. Every PendingIntent is
     *  dropped on reboot, so this is how a live session is told from one the stored
     *  flag still claims but that died with the last power cycle. */
    fun hasArmed(ctx: Context, action: String): Boolean =
        fire(ctx, action, code(action), uri(action), PendingIntent.FLAG_NO_CREATE) != null

    fun armInterval(ctx: Context) {
        val (n, at) = Session.nextInterval(ctx)
        if (n != Session.intervalRang(ctx) + 1) Session.recordRang(ctx, n - 1)   // skipped bowls are not rung late
        am(ctx).setAlarmClock(AlarmManager.AlarmClockInfo(at, show(ctx)), fire(ctx, RING_INTERVAL, CODE_INTERVAL, uri(RING_INTERVAL))!!)
    }

    fun armPeriod(ctx: Context) {
        am(ctx).setAlarmClock(AlarmManager.AlarmClockInfo(Session.periodEndsAt(ctx), show(ctx)), fire(ctx, RING_PERIOD, CODE_PERIOD, uri(RING_PERIOD))!!)
    }

    fun cancel(ctx: Context, action: String) {
        fire(ctx, action, code(action), uri(action))?.let { am(ctx).cancel(it); it.cancel() }
    }

    /** Cancel every scheduled bowl we could ever have created, then arm the next occurrence of each enabled one. */
    fun rescheduleAll(ctx: Context) {
        val hwm = Schedule.highWatermark(ctx)
        for (id in 1..hwm) scheduledPi(ctx, id)?.let { am(ctx).cancel(it) }
        Schedule.load(ctx).filter { it.enabled }.forEach { b ->
            am(ctx).setAlarmClock(AlarmManager.AlarmClockInfo(nextTrigger(b), show(ctx)), scheduledPi(ctx, b.id)!!)
        }
    }

    private fun scheduledPi(ctx: Context, id: Int) = fire(ctx, RING_SCHEDULED, CODE_SCHEDULED + id, "mindful://bell/$id")

    /** Next wall-clock instant for this bell: today if still ahead, else tomorrow. */
    fun nextTrigger(b: Bell): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, b.hour); set(Calendar.MINUTE, b.minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        if (next.timeInMillis <= now.timeInMillis) next.add(Calendar.DAY_OF_YEAR, 1)
        return next.timeInMillis
    }

    /** Re-arm everything from storage: after a reboot, a time change or an update. A
     *  period whose end is already past is ended; a reminder carries on its grid. */
    fun rearmAll(ctx: Context) {
        rescheduleAll(ctx)
        if (Session.periodRunning(ctx)) { if (Session.periodEndsAt(ctx) > System.currentTimeMillis()) armPeriod(ctx) else Session.endPeriod(ctx) }
        if (Session.intervalRunning(ctx)) armInterval(ctx)
    }
}
