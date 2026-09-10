package com.freedomfighter.readersmindful

import android.app.Application
import com.freedomfighter.readersmindful.data.Prefs

class App : Application() {
    val prefs: Prefs by lazy { Prefs(this) }
    override fun onCreate() { super.onCreate(); prefs }
}
