package com.freedomfighter.readersmindful

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.freedomfighter.readersmindful.data.Alarms
import com.freedomfighter.readersmindful.data.Bowls
import com.freedomfighter.readersmindful.data.Prefs
import com.freedomfighter.readersmindful.data.Schedule
import com.freedomfighter.readersmindful.data.Session
import com.freedomfighter.readersmindful.provider.StateProvider
import com.freedomfighter.readersmindful.widget.MindfulWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * The whole engine: a foreground service (type mediaPlayback) that rings the
 * bowls and stays alive while a reminder or a meditation period runs. Bowls are
 * armed with [android.app.AlarmManager.setAlarmClock] through [Alarms]; this
 * service is what the alarm wakes. Being in the foreground keeps it from being
 * killed between bowls and puts the Stop actions in the shade. The bowl plays on
 * the alarm stream, through silent mode and Do-Not-Disturb, under a wake lock so
 * the CPU cannot doze off between the alarm and the sound.
 */
class BellService : Service() {
    private var player: MediaPlayer? = null
    private var ringLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var ticker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Foreground first, always: a service started from the background that does
        // not call startForeground promptly is killed with an exception.
        goForeground()
        when (intent?.action) {
            ACTION_START_INTERVAL -> startInterval(intent.getIntExtra(EXTRA_MINUTES, 0))
            ACTION_START_PERIOD -> startPeriod(intent.getIntExtra(EXTRA_MINUTES, 0))
            ACTION_STOP_INTERVAL -> { Alarms.cancel(this, Alarms.RING_INTERVAL); Session.endInterval(this) }
            ACTION_STOP_PERIOD -> { Alarms.cancel(this, Alarms.RING_PERIOD); Session.endPeriod(this) }
            ACTION_STOP_ALL -> { Alarms.cancel(this, Alarms.RING_INTERVAL); Alarms.cancel(this, Alarms.RING_PERIOD); Session.endInterval(this); Session.endPeriod(this) }
            ACTION_RING_INTERVAL -> ringInterval()
            ACTION_RING_PERIOD -> ringPeriod()
            ACTION_RING_SCHEDULED -> ringScheduled(intent.getIntExtra(Alarms.EXTRA_BELL_ID, -1))
            ACTION_PREVIEW -> play(intent.getBooleanExtra(EXTRA_THREE, false))
            else -> resume()   // null intent = Android restarting us after a kill
        }
        changed()
        return START_STICKY
    }

    private fun startInterval(minutes: Int) {
        val m = minutes.coerceIn(Session.MIN_MINUTES, Session.MAX_MINUTES)
        Prefs.raw(this).edit().putInt(Prefs.KEY_INTERVAL, m).apply()
        Session.beginInterval(this, m, System.currentTimeMillis())
        Alarms.armInterval(this)
    }

    private fun startPeriod(minutes: Int) {
        val m = minutes.coerceIn(Session.MIN_MINUTES, Session.MAX_MINUTES)
        Prefs.raw(this).edit().putInt(Prefs.KEY_DURATION, m).apply()
        Session.beginPeriod(this, m, System.currentTimeMillis())
        Alarms.armPeriod(this)
    }

    /** Make sure whatever storage says is running really is armed (after a kill, a reboot, or when the screen asks). */
    private fun resume() {
        if (Session.intervalRunning(this)) Alarms.armInterval(this)
        if (Session.periodRunning(this)) { if (Session.periodEndsAt(this) > System.currentTimeMillis()) Alarms.armPeriod(this) else Session.endPeriod(this) }
    }

    /** One bowl, then arm the next multiple of the interval. Reads storage rather than a field, so it still lands right in a fresh instance. */
    private fun ringInterval() {
        if (!Session.intervalRunning(this)) return   // Stop pressed in the same instant the alarm fired
        Session.recordRang(this, Session.nextInterval(this, System.currentTimeMillis() - 1_000).first)
        play(three = false)
        Alarms.armInterval(this)
    }

    /** The period is over: three bowls, and the session ends. */
    private fun ringPeriod() {
        if (!Session.periodRunning(this)) return
        Session.endPeriod(this)
        play(three = true)
    }

    private fun ringScheduled(id: Int) {
        val bell = Schedule.get(this, id) ?: return
        if (bell.enabled) play(bell.three)
    }

    /** Strikes still to play of the custom recording (three = the file three times over). */
    private var strikesLeft = 0

    private fun play(three: Boolean) {
        strikesLeft = if (Bowls.isCustom(this)) (if (three) 3 else 1) else 1
        playOnce(three)
    }

    private fun playOnce(three: Boolean) {
        // Held briefly so the CPU cannot drop back to sleep between the alarm waking us and the bowl sounding.
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        ringLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ReadersMindful:ring").apply { setReferenceCounted(false); acquire(90_000L) }
        runCatching { player?.release() }
        player = null
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setWakeMode(this@BellService, PowerManager.PARTIAL_WAKE_LOCK)
                val custom = Bowls.customFile(this@BellService)?.takeIf { Bowls.isCustom(this@BellService) }
                if (custom != null) setDataSource(custom.absolutePath) else {
                    val afd = resources.openRawResourceFd(Bowls.rawRes(this@BellService, three))
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                }
                setOnCompletionListener { if (--strikesLeft > 0) playOnce(three) else { releasePlayer(); changed() } }
                setOnErrorListener { _, _, _ -> releasePlayer(); changed(); true }
                prepare()
                start()
            }
            playing = true
        }.onFailure { releasePlayer() }
    }

    private fun releasePlayer() {
        runCatching { player?.release() }
        player = null
        playing = false
        runCatching { if (ringLock?.isHeld == true) ringLock?.release() }
        ringLock = null
    }

    /** After every change: tell the screen, the widgets and the launcher, then stay up or bow out. */
    private fun changed() {
        Session.Live.sync(this)
        MindfulWidgets.refresh(this)
        StateProvider.notify(this)
        if (Session.anyRunning(this) || player != null) {
            pushNotification()
            if (Session.periodRunning(this) && ticker == null) startTicker()
        } else {
            finish()
        }
    }

    /** Progress in the widgets and the notification while a period runs. Best effort — the bowls do not depend on it. */
    private fun startTicker() {
        ticker = scope.launch {
            while (isActive && Session.periodRunning(this@BellService)) { delay(30_000); MindfulWidgets.refresh(this@BellService); pushNotification() }
            ticker = null
        }
    }

    private fun finish() {
        ticker?.cancel(); ticker = null
        releasePlayer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { scope.cancel(); releasePlayer(); super.onDestroy() }

    // ---- notification ----

    private fun goForeground() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(NOTIF_ID, n)
    }

    private fun pushNotification() = (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, buildNotification())

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Silent, no vibration: the bowl is the only sound this app makes.
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, getString(R.string.channel), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.channel_desc); setSound(null, null); enableVibration(false)
        })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val interval = Session.intervalRunning(this); val period = Session.periodRunning(this)
        val lines = buildList {
            if (interval) add(getString(R.string.notif_interval, Session.intervalMin(this@BellService), clock(Session.nextInterval(this@BellService).second)))
            if (period) add(getString(R.string.notif_period, Session.periodMin(this@BellService), clock(Session.periodEndsAt(this@BellService))))
            if (isEmpty()) add(getString(R.string.notif_ringing))
        }
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(lines.first())
            .setSmallIcon(R.drawable.ic_bell)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOnlyAlertOnce(true)
            .setOngoing(interval || period)
            .setShowWhen(false)
        if (lines.size > 1) b.setContentText(lines[1]).setStyle(NotificationCompat.InboxStyle().also { s -> lines.forEach { s.addLine(it) } })
        if (period) {
            val total = Session.periodMin(this) * 60_000L; val done = (System.currentTimeMillis() - Session.periodStartedAt(this)).coerceIn(0L, total)
            b.setProgress(1000, (done * 1000 / total).toInt(), false)
            b.setUsesChronometer(true).setChronometerCountDown(true).setWhen(Session.periodEndsAt(this)).setShowWhen(true)
        }
        if (interval) b.addAction(0, getString(R.string.stop_reminder), servicePi(11, ACTION_STOP_INTERVAL))
        if (period) b.addAction(0, getString(R.string.stop_meditation), servicePi(12, ACTION_STOP_PERIOD))
        return b.build()
    }

    private fun servicePi(code: Int, action: String): PendingIntent =
        PendingIntent.getService(this, code, Intent(this, BellService::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun clock(millis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    companion object {
        const val ACTION_START_INTERVAL = "com.freedomfighter.readersmindful.START_INTERVAL"
        const val ACTION_START_PERIOD = "com.freedomfighter.readersmindful.START_PERIOD"
        const val ACTION_STOP_INTERVAL = "com.freedomfighter.readersmindful.STOP_INTERVAL"
        const val ACTION_STOP_PERIOD = "com.freedomfighter.readersmindful.STOP_PERIOD"
        const val ACTION_STOP_ALL = "com.freedomfighter.readersmindful.STOP_ALL"
        const val ACTION_RING_INTERVAL = "com.freedomfighter.readersmindful.svc.RING_INTERVAL"
        const val ACTION_RING_PERIOD = "com.freedomfighter.readersmindful.svc.RING_PERIOD"
        const val ACTION_RING_SCHEDULED = "com.freedomfighter.readersmindful.svc.RING_SCHEDULED"
        const val ACTION_RESUME = "com.freedomfighter.readersmindful.RESUME"
        const val ACTION_PREVIEW = "com.freedomfighter.readersmindful.PREVIEW"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_THREE = "three"
        private const val CHANNEL_ID = "mindful_session"
        private const val NOTIF_ID = 1

        /** True while a bowl is sounding. */
        @Volatile var playing: Boolean = false

        fun intent(ctx: Context, action: String, minutes: Int = 0, bellId: Int = -1): Intent =
            Intent(ctx, BellService::class.java).setAction(action).putExtra(EXTRA_MINUTES, minutes).putExtra(Alarms.EXTRA_BELL_ID, bellId)

        fun send(ctx: Context, action: String, bellId: Int = -1) = ContextCompat.startForegroundService(ctx, intent(ctx, action, bellId = bellId))
        fun startInterval(ctx: Context, minutes: Int) = ContextCompat.startForegroundService(ctx, intent(ctx, ACTION_START_INTERVAL, minutes))
        fun startPeriod(ctx: Context, minutes: Int) = ContextCompat.startForegroundService(ctx, intent(ctx, ACTION_START_PERIOD, minutes))
        fun stopInterval(ctx: Context) = send(ctx, ACTION_STOP_INTERVAL)
        fun stopPeriod(ctx: Context) = send(ctx, ACTION_STOP_PERIOD)
        fun preview(ctx: Context, three: Boolean) = ContextCompat.startForegroundService(ctx, intent(ctx, ACTION_PREVIEW).putExtra(EXTRA_THREE, three))
    }
}
