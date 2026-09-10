package com.freedomfighter.readersmindful

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.freedomfighter.readersmindful.data.Alarms
import com.freedomfighter.readersmindful.data.Session
import com.freedomfighter.readersmindful.ui.HomeScreen
import com.freedomfighter.readersmindful.ui.LocalColors
import com.freedomfighter.readersmindful.ui.Nav
import com.freedomfighter.readersmindful.ui.ReaderTheme
import com.freedomfighter.readersmindful.ui.ScheduleScreen
import com.freedomfighter.readersmindful.ui.Screen
import com.freedomfighter.readersmindful.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val nav = Nav()
    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val app = application as App
        setContent {
            val settings by app.prefs.settings.collectAsState()
            ReaderTheme(settings) {
                Bars()
                when (nav.current) {
                    Screen.Home -> HomeScreen(nav, app)
                    Screen.Schedule -> ScheduleScreen(nav, app)
                    Screen.Settings -> SettingsScreen(nav, app)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Whatever storage claims is running must really be armed; if not, it is ended here.
        Alarms.rescheduleAll(this)
        Session.reconcile(this)
        Session.Live.sync(this)
    }

    @Composable
    private fun Bars() {
        val view = LocalView.current
        val colors = LocalColors.current
        LaunchedEffect(colors.isDark) {
            val c = WindowInsetsControllerCompat(window, view)
            c.isAppearanceLightStatusBars = !colors.isDark
            c.isAppearanceLightNavigationBars = !colors.isDark
        }
    }
}
