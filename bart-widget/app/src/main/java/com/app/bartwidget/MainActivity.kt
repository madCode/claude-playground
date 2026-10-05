package com.app.bartwidget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var focused by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        focused = intent.station()
        Refresher.schedule(this)
        setContent { AppTheme { App(focused, onFocus = { focused = it }) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        focused = intent.station()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { Refresher.refresh(applicationContext) }
    }

    // The widget puts the station it was tapped on in the intent; whatever it says is what we show.
    private fun Intent.station(): String? = getStringExtra(StationParam.name)
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        android.os.Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(focused: String?, onFocus: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val starred by Store.starred(context).collectAsStateWithLifecycle(emptyList())
    val snapshot by Store.snapshot(context).collectAsStateWithLifecycle(Snapshot())
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    val refresh: () -> Unit = { scope.launch { Refresher.refresh(context.applicationContext); now = System.currentTimeMillis() } }
    val toggle: (String) -> Unit = { abbr -> scope.launch { Store.toggle(context, abbr); Refresher.refresh(context.applicationContext) } }
    BackHandler(enabled = focused != null) { onFocus(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(focused?.let(::stationName) ?: "BART") },
                navigationIcon = {
                    if (focused != null) IconButton(onClick = { onFocus(null) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "All stations") }
                },
                actions = {
                    if (focused != null) StarButton(focused in starred) { toggle(focused) }
                    IconButton(onClick = refresh) { Icon(Icons.Filled.Refresh, "Refresh") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (focused != null) {
                item { BoardCard(focused, snapshot.boards[focused], now, showName = false, onClick = null) }
                if (focused !in starred) item { Text("Star this station to keep it on the widget.", style = MaterialTheme.typography.bodyMedium) }
                return@LazyColumn
            }
            items(starred, key = { "board-$it" }) { abbr ->
                BoardCard(abbr, snapshot.boards[abbr], now, showName = true, onClick = { onFocus(abbr) })
            }
            item {
                FilledTonalButton(onClick = { context.startActivity(parkingIntent(context)) }, Modifier.fillMaxWidth()) {
                    Text("Parking in the BART app")
                }
            }
            item { Text("All stations", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            items(STATIONS, key = { "stn-${it.abbr}" }) { station ->
                Row(
                    Modifier.fillMaxWidth().clickable { onFocus(station.abbr) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(station.name, Modifier.weight(1f))
                    StarButton(station.abbr in starred) { toggle(station.abbr) }
                }
            }
        }
    }
}

@Composable
private fun StarButton(starred: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        if (starred) Icon(Icons.Filled.Star, "Unstar", tint = Color(0xFFF2B01E))
        else Icon(Icons.Outlined.StarBorder, "Star")
    }
}

@Composable
private fun BoardCard(abbr: String, board: Board?, now: Long, showName: Boolean, onClick: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showName) Text(stationName(abbr), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            when {
                board == null -> Text("Loading…", color = muted)
                board.error != null && board.fetchedAt > 0 -> Text("${board.error}. Times are from ${formatClock(board.fetchedAt)}.", color = MaterialTheme.colorScheme.error)
                board.error != null -> Text(board.error, color = MaterialTheme.colorScheme.error)
                else -> Text("Updated ${formatClock(board.fetchedAt)}", color = muted, style = MaterialTheme.typography.bodySmall)
            }
            val rows = board?.let { destinationRows(it.trains, now) } ?: emptyList()
            if (board != null && rows.isEmpty() && board.error == null) Text("No trains right now", color = muted)
            rows.forEach { row ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(parseHex(row.hexColor), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(row.destination, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        row.trains.joinToString("   ") { t ->
                            val m = minutesUntil(t.departsAt, now)
                            "${formatClock(t.departsAt)} (${if (m == 0) "now" else "$m min"})"
                        },
                        Modifier.padding(start = 18.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    val first = row.trains.first()
                    Text(
                        "Platform ${first.platform} · ${first.cars} cars" + if (first.delayed) " · delayed" else "",
                        Modifier.padding(start = 18.dp),
                        color = muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun parseHex(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)
