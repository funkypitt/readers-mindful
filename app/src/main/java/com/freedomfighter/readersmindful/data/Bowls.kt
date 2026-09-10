package com.freedomfighter.readersmindful.data

import android.content.Context
import android.net.Uri
import com.freedomfighter.readersmindful.R
import java.io.File

/** One of Retreat Timer's bowls: [three] is three strikes, [one] a single strike. All recordings are loudness-matched. */
data class Bowl(val key: String, val label: String, val three: Int, val one: Int)

object Bowls {
    const val DEFAULT = "eflat"
    /** The user's own recording (mp3, wav…), copied into the app's files; three strikes = played three times. */
    const val CUSTOM = "custom"
    val ALL = listOf(
        Bowl("eflat", "Tibetan bowl (E♭)", R.raw.bell_eflat, R.raw.bell_eflat_one),
        Bowl("singing", "singing bowl", R.raw.bell_singing, R.raw.bell_singing_one),
        Bowl("gong", "gong bowl", R.raw.bell_gong, R.raw.bell_gong_one),
        Bowl("satipanya", "Satipanya", R.raw.bell_satipanya, R.raw.bell_satipanya_one),
        Bowl("enpleineconscience", "enpleineconscience.ch", R.raw.bell_enpleineconscience, R.raw.bell_enpleineconscience_one)
    )

    fun byKey(key: String?): Bowl = ALL.firstOrNull { it.key == key } ?: ALL.first()

    /** The chosen bowl, readable from any process state (receiver, widget, service). */
    fun selected(context: Context): Bowl = byKey(Prefs.raw(context).getString(Prefs.KEY_BOWL, DEFAULT))
    fun isCustom(context: Context): Boolean = Prefs.raw(context).getString(Prefs.KEY_BOWL, DEFAULT) == CUSTOM && customFile(context) != null

    fun rawRes(context: Context, three: Boolean): Int = selected(context).let { if (three) it.three else it.one }

    fun customFile(context: Context): File? = File(context.filesDir, "custom_bell").takeIf { it.exists() && it.length() > 0 }
    fun customName(context: Context): String = Prefs.raw(context).getString(Prefs.KEY_CUSTOM_NAME, "") ?: ""

    /** Copy the picked recording into our files (a content URI's grant does not survive an alarm after reboot) and choose it. */
    fun importCustom(context: Context, uri: Uri): Boolean = runCatching {
        val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: uri.lastPathSegment ?: "sound"
        val tmp = File(context.filesDir, "custom_bell.tmp")
        context.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        // it has to be something MediaPlayer can open
        android.media.MediaMetadataRetriever().use { r -> r.setDataSource(tmp.absolutePath); r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() }
        tmp.renameTo(File(context.filesDir, "custom_bell"))
        Prefs.raw(context).edit().putString(Prefs.KEY_CUSTOM_NAME, name).putString(Prefs.KEY_BOWL, CUSTOM).apply()
        true
    }.getOrDefault(false)
}
