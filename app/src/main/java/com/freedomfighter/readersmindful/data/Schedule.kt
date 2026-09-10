package com.freedomfighter.readersmindful.data

import android.content.Context

/** A bowl at a set time of day, every day: one strike or three. */
data class Bell(val id: Int, val hour: Int, val minute: Int, val three: Boolean, val enabled: Boolean) {
    val clock: String get() = "%02d:%02d".format(hour, minute)
}

/** The scheduled bowls, kept as one line per bell in SharedPreferences. */
object Schedule {
    private const val PREFS = "schedule"
    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): List<Bell> = (sp(ctx).getString("bells", "") ?: "").split(";").filter { it.isNotBlank() }.mapNotNull { line ->
        val f = line.split("|"); if (f.size < 5) null else runCatching { Bell(f[0].toInt(), f[1].toInt(), f[2].toInt(), f[3] == "1", f[4] == "1") }.getOrNull()
    }.sortedWith(compareBy({ it.hour }, { it.minute }))

    /** Ids are monotonic from 1; the high-water mark lets the alarms of deleted bells be cancelled too. */
    fun highWatermark(ctx: Context): Int = sp(ctx).getInt("hwm", 0)

    fun save(ctx: Context, bells: List<Bell>) {
        sp(ctx).edit().putString("bells", bells.joinToString(";") { "${it.id}|${it.hour}|${it.minute}|${if (it.three) 1 else 0}|${if (it.enabled) 1 else 0}" }).apply()
        Alarms.rescheduleAll(ctx)
    }

    fun add(ctx: Context, hour: Int, minute: Int, three: Boolean): Bell {
        val id = highWatermark(ctx) + 1
        sp(ctx).edit().putInt("hwm", id).apply()
        val b = Bell(id, hour, minute, three, true)
        save(ctx, load(ctx) + b)
        return b
    }

    fun update(ctx: Context, bell: Bell) = save(ctx, load(ctx).map { if (it.id == bell.id) bell else it })
    fun remove(ctx: Context, id: Int) = save(ctx, load(ctx).filter { it.id != id })
    fun get(ctx: Context, id: Int): Bell? = load(ctx).firstOrNull { it.id == id }
}
