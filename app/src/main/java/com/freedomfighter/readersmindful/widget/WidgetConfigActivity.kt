package com.freedomfighter.readersmindful.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.freedomfighter.readersmindful.App
import com.freedomfighter.readersmindful.R
import com.freedomfighter.readersmindful.ui.Page
import com.freedomfighter.readersmindful.ui.ReaderTheme
import com.freedomfighter.readersmindful.ui.ScreenTitle
import com.freedomfighter.readersmindful.ui.Small
import com.freedomfighter.readersmindful.ui.TextRow
import com.freedomfighter.readersmindful.ui.rowPadH

/** Asked once, when the widget is placed: both functions, or only one of them. */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(Activity.RESULT_CANCELED)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val app = application as App
        setContent {
            val settings by app.prefs.settings.collectAsState()
            ReaderTheme(settings) {
                Page {
                    Column(Modifier.fillMaxSize()) {
                        ScreenTitle(stringResource(R.string.widget_name), onBack = { finish() })
                        Small(stringResource(R.string.widget_config_hint), Modifier.padding(horizontal = rowPadH).padding(top = 16.dp, bottom = 6.dp), maxLines = 4)
                        TextRow(stringResource(R.string.widget_both)) { done(id, WidgetMode.BOTH) }
                        TextRow(stringResource(R.string.widget_interval_only)) { done(id, WidgetMode.INTERVAL) }
                        TextRow(stringResource(R.string.widget_period_only)) { done(id, WidgetMode.PERIOD) }
                    }
                }
            }
        }
    }

    private fun done(id: Int, mode: WidgetMode) {
        MindfulWidgets.setMode(this, id, mode)
        MindfulWidgets.render(this, AppWidgetManager.getInstance(this), id)
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        finish()
    }
}
