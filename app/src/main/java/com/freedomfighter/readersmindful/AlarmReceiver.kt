package com.freedomfighter.readersmindful

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.freedomfighter.readersmindful.data.Alarms

/**
 * Fired by AlarmManager at the exact instant of a bowl. Its only job is to hand
 * straight over to the foreground [BellService], which rings and re-arms.
 * Starting a foreground service from an exact-alarm broadcast is explicitly
 * allowed by the OS even while the device is idle, so this is reliable.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Alarms.RING_INTERVAL -> BellService.send(context, BellService.ACTION_RING_INTERVAL)
            Alarms.RING_PERIOD -> BellService.send(context, BellService.ACTION_RING_PERIOD)
            Alarms.RING_SCHEDULED -> {
                val id = intent.data?.lastPathSegment?.toIntOrNull() ?: return
                BellService.send(context, BellService.ACTION_RING_SCHEDULED, id)
                // This bell is now in the past: re-arm so it rolls to tomorrow.
                Alarms.rescheduleAll(context)
            }
        }
    }
}

/**
 * AlarmManager alarms do not survive a reboot, a time change or an app update.
 * Re-arm everything from storage so a running reminder or period carries on and
 * the scheduled bowls keep ringing.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Alarms.rearmAll(context)
        if (com.freedomfighter.readersmindful.data.Session.anyRunning(context)) BellService.send(context, BellService.ACTION_RESUME)
        com.freedomfighter.readersmindful.widget.MindfulWidgets.refresh(context)
    }
}
