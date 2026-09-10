package com.freedomfighter.readersmindful.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.freedomfighter.readersmindful.data.Bowls
import com.freedomfighter.readersmindful.data.Prefs
import com.freedomfighter.readersmindful.data.Session

/**
 * What is running, for Reader's Launcher's tile (signature-protected). One row:
 * content://com.freedomfighter.readersmindful/state. The launcher starts and stops
 * the functions through the exported [com.freedomfighter.readersmindful.BellService].
 */
class StateProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = "vnd.android.cursor.item/vnd.readersmindful.state"

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sort: String?): Cursor {
        val ctx = context!!
        Session.Live.sync(ctx)
        val c = MatrixCursor(COLUMNS)
        val sp = Prefs.raw(ctx)
        c.addRow(arrayOf(
            if (Session.intervalRunning(ctx)) 1 else 0, Session.intervalMin(ctx), if (Session.intervalRunning(ctx)) Session.nextInterval(ctx).second else 0L, Session.intervalRang(ctx),
            if (Session.periodRunning(ctx)) 1 else 0, Session.periodMin(ctx), Session.periodStartedAt(ctx), if (Session.periodRunning(ctx)) Session.periodEndsAt(ctx) else 0L,
            sp.getInt(Prefs.KEY_INTERVAL, 10), sp.getInt(Prefs.KEY_DURATION, 20), Bowls.selected(ctx).key
        ))
        c.setNotificationUri(ctx.contentResolver, URI)
        return c
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?) = 0
    override fun delete(uri: Uri, selection: String?, args: Array<String>?) = 0

    companion object {
        val URI: Uri = Uri.parse("content://com.freedomfighter.readersmindful/state")
        val COLUMNS = arrayOf("interval_running", "interval_min", "next_bell_at", "rang", "period_running", "period_min", "period_started_at", "period_ends_at", "last_interval", "last_duration", "bowl")
        fun notify(ctx: Context) = runCatching { ctx.contentResolver.notifyChange(URI, null) }
    }
}
