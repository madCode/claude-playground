package com.app.bartwidget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.flow.first

val StationParam = ActionParameters.Key<String>("station")

/** Stations expanded to show every line, per widget: two widgets can differ. */
val ExpandedKey = stringSetPreferencesKey("expanded")

class BartWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = context.container
        val first = Triple(c.store.starred.first(), c.store.lines.first(), c.store.snapshot.first())
        val parking = parkingIntent(context)
        provideContent {
            val starred by c.store.starred.collectAsState(first.first)
            val lines by c.store.lines.collectAsState(first.second)
            val snapshot by c.store.snapshot.collectAsState(first.third)
            val expanded = currentState<Preferences>()[ExpandedKey] ?: emptySet()
            GlanceTheme { WidgetContent(starred, lines, expanded, snapshot, c.clock(), parking) }
        }
    }
}

@Composable
internal fun WidgetContent(
    starred: List<String>,
    lines: Set<String>,
    expanded: Set<String>,
    snapshot: Snapshot,
    now: Long,
    parking: android.content.Intent,
) {
    val text = GlanceTheme.colors.onSurface
    val muted = GlanceTheme.colors.onSurfaceVariant
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(16.dp).padding(10.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val updated = starred.mapNotNull { snapshot.boards[it]?.fetchedAt?.takeIf { t -> t > 0 } }.minOrNull()
            Text(
                if (updated == null) "BART" else "Updated ${formatClock(updated)}",
                GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>()),
                style = TextStyle(color = muted, fontSize = 12.sp),
            )
            Pill("Refresh", GlanceModifier.clickable(actionRunCallback<RefreshAction>()))
            Spacer(GlanceModifier.width(6.dp))
            Pill("Parking", GlanceModifier.clickable(actionStartActivity(parking)))
        }
        Spacer(GlanceModifier.height(4.dp))
        if (starred.isEmpty()) {
            Text(
                "Tap to star your stations",
                GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
                style = TextStyle(color = text, fontSize = 14.sp),
            )
            return@Column
        }
        LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
            items(starred, itemId = { it.hashCode().toLong() }) { abbr ->
                StationBlock(abbr, snapshot.boards[abbr], lines, abbr in expanded, now)
            }
        }
    }
}

@Composable
private fun StationBlock(abbr: String, board: Board?, lines: Set<String>, expanded: Boolean, now: Long) {
    val text = GlanceTheme.colors.onSurface
    val muted = GlanceTheme.colors.onSurfaceVariant
    // Tapping opens this station, always: never the one the phone guesses is nearest.
    val open = actionStartActivity<MainActivity>(actionParametersOf(StationParam to abbr))
    val rows = board?.let { destinationRows(it.trains, now) } ?: emptyList()
    val show = widgetRows(rows, abbr, lines, expanded)
    Column(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(GlanceModifier.fillMaxWidth().clickable(open)) {
            Text(stationName(abbr), style = TextStyle(color = text, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            when {
                board == null -> Text("Loading…", style = TextStyle(color = muted, fontSize = 12.sp))
                rows.isEmpty() -> Text(board.error ?: "No trains", style = TextStyle(color = muted, fontSize = 12.sp))
                else -> {
                    if (board.error != null) Text("${board.error}: times from ${formatClock(board.fetchedAt)}", style = TextStyle(color = muted, fontSize = 11.sp))
                    if (show.shown.isEmpty()) Text("No trains on your lines right now", style = TextStyle(color = muted, fontSize = 12.sp))
                    Column(GlanceModifier.fillMaxWidth()) {
                        show.shown.forEach { row -> TrainRow(row) }
                    }
                }
            }
        }
        if (show.more > 0 || show.canCollapse) {
            val toggle = actionRunCallback<ToggleExpandAction>(actionParametersOf(StationParam to abbr))
            Text(
                if (show.canCollapse) "Show less" else "+${show.more} more",
                GlanceModifier.padding(top = 2.dp, bottom = 2.dp).clickable(toggle),
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

@Composable
private fun TrainRow(row: DestinationRow) {
    val text = GlanceTheme.colors.onSurface
    Row(GlanceModifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(lineColor(row.hexColor))) {}
        Spacer(GlanceModifier.width(6.dp))
        Text(row.destination, GlanceModifier.defaultWeight(), style = TextStyle(color = text, fontSize = 13.sp), maxLines = 1)
        Text(
            row.trains.take(3).joinToString("  ") { formatClock(it.departsAt) },
            style = TextStyle(color = text, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun Pill(label: String, modifier: GlanceModifier) {
    Text(
        label,
        modifier.background(GlanceTheme.colors.secondaryContainer).cornerRadius(12.dp).padding(horizontal = 10.dp, vertical = 4.dp),
        style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 12.sp, fontWeight = FontWeight.Medium),
    )
}

fun lineColor(hex: String): ColorProvider =
    ColorProvider(runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray))

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.container.refresher.refresh()
    }
}

/** "+N more" and "Show less": expands or collapses one station on the widget that was tapped. */
class ToggleExpandAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val station = parameters[StationParam] ?: return
        updateAppWidgetState(context, glanceId) { prefs ->
            val now = prefs[ExpandedKey] ?: emptySet()
            prefs[ExpandedKey] = if (station in now) now - station else now + station
        }
        BartWidget().update(context, glanceId)
    }
}

class BartWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = BartWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Refresher.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Refresher.cancel(context)
    }
}
